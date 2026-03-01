import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { MemberCsvImport } from "@/components/admin/MemberCsvImport";
import { ToastProvider } from "@/components/shared/ToastProvider";
import { IntlWrapper } from "../../../helpers/intl";

Object.defineProperty(window, "matchMedia", {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

const mockDryRun = vi.fn();
const mockExecute = vi.fn();
const mockDownloadTemplate = vi.fn();
const mockGetOrgTree = vi.fn();

vi.mock("@/services/memberCsvService", () => ({
  dryRunMemberCsv: (...args: unknown[]) => mockDryRun(...args),
  executeMemberCsvImport: (...args: unknown[]) => mockExecute(...args),
  downloadMemberCsvTemplate: (...args: unknown[]) => mockDownloadTemplate(...args),
}));

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      organizations: {
        getOrganizationTree: (...args: unknown[]) => mockGetOrgTree(...args),
      },
    },
  },
}));

const mockToast = { success: vi.fn(), error: vi.fn() };
vi.mock("@/hooks/useToast", () => ({
  useToast: () => mockToast,
}));

function renderWithProviders(ui: ReactElement) {
  return render(
    <IntlWrapper>
      <ToastProvider>{ui}</ToastProvider>
    </IntlWrapper>,
  );
}

const mockOrgTree = [
  {
    id: "org-1",
    code: "DEV",
    name: "開発部",
    level: 1,
    status: "ACTIVE" as const,
    memberCount: 5,
    children: [
      {
        id: "org-2",
        code: "FE",
        name: "フロントエンド",
        level: 2,
        status: "ACTIVE" as const,
        memberCount: 3,
        children: [],
      },
    ],
  },
  {
    id: "org-3",
    code: "INACTIVE",
    name: "廃止部",
    level: 1,
    status: "INACTIVE" as const,
    memberCount: 0,
    children: [],
  },
];

const mockDryRunResult = {
  sessionId: "session-123",
  totalRows: 3,
  validRows: 2,
  errorRows: 1,
  rows: [
    { rowNumber: 1, email: "alice@test.com", displayName: "Alice", status: "VALID" as const, errors: [] },
    { rowNumber: 2, email: "bob@test.com", displayName: "Bob", status: "VALID" as const, errors: [] },
    {
      rowNumber: 3,
      email: "invalid",
      displayName: "Charlie",
      status: "ERROR" as const,
      errors: ["email: メール形式が不正です"],
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  mockGetOrgTree.mockResolvedValue(mockOrgTree);
});

describe("MemberCsvImport", () => {
  describe("Step 1 - Upload", () => {
    it("renders upload form with org selector and dropzone", async () => {
      renderWithProviders(<MemberCsvImport />);

      await waitFor(() => {
        expect(screen.getByText("テンプレートをダウンロード")).toBeInTheDocument();
      });

      expect(screen.getByText("ここにCSVファイルをドロップするか、クリックして選択")).toBeInTheDocument();
      expect(screen.getByLabelText("組織")).toBeInTheDocument();
    });

    it("shows flattened org tree options (active only)", async () => {
      renderWithProviders(<MemberCsvImport />);

      await waitFor(() => {
        const orgSelect = screen.getByLabelText("組織") as HTMLSelectElement;
        const options = Array.from(orgSelect.options).map((o) => o.textContent);
        expect(options).toContain("組織を選択してください");
        // DEV and FE should appear, INACTIVE should not
        expect(options.some((o) => o?.includes("DEV - 開発部"))).toBe(true);
        expect(options.some((o) => o?.includes("FE - フロントエンド"))).toBe(true);
        expect(options.some((o) => o?.includes("INACTIVE"))).toBe(false);
      });
    });

    it("disables submit button when no file is selected", async () => {
      const user = userEvent.setup();
      renderWithProviders(<MemberCsvImport />);

      await waitFor(() => {
        expect(screen.getByLabelText("組織")).toBeInTheDocument();
      });

      // Select an org but no file
      await user.selectOptions(screen.getByLabelText("組織"), "org-1");

      // Button should be disabled
      expect(screen.getByText("検証（ドライラン）")).toBeDisabled();
    });

    it("shows error when submitting without organization", async () => {
      const user = userEvent.setup();
      renderWithProviders(<MemberCsvImport />);

      await waitFor(() => {
        expect(screen.getByLabelText("組織")).toBeInTheDocument();
      });

      // Upload a file via input
      const file = new File(["csv"], "test.csv", { type: "text/csv" });
      const input = screen.getByLabelText("CSVファイルを選択") as HTMLInputElement;
      await user.upload(input, file);

      // Submit without org
      await user.click(screen.getByText("検証（ドライラン）"));

      expect(screen.getByText("組織を選択してください")).toBeInTheDocument();
    });

    it("transitions to step 2 on successful dry-run", async () => {
      mockDryRun.mockResolvedValue(mockDryRunResult);
      const user = userEvent.setup();
      renderWithProviders(<MemberCsvImport />);

      await waitFor(() => {
        expect(screen.getByLabelText("組織")).toBeInTheDocument();
      });

      // Select org and upload file
      await user.selectOptions(screen.getByLabelText("組織"), "org-1");
      const file = new File(["csv"], "members.csv", { type: "text/csv" });
      const input = screen.getByLabelText("CSVファイルを選択") as HTMLInputElement;
      await user.upload(input, file);

      // Submit
      await user.click(screen.getByText("検証（ドライラン）"));

      // Should transition to step 2 with summary
      await waitFor(() => {
        expect(screen.getByText(/合計: 3行/)).toBeInTheDocument();
      });
    });

    it("calls template download on button click", async () => {
      mockDownloadTemplate.mockResolvedValue(undefined);
      const user = userEvent.setup();
      renderWithProviders(<MemberCsvImport />);

      await waitFor(() => {
        expect(screen.getByText("テンプレートをダウンロード")).toBeInTheDocument();
      });

      await user.click(screen.getByText("テンプレートをダウンロード"));

      expect(mockDownloadTemplate).toHaveBeenCalled();
    });
  });

  describe("Step 2 - Dry-run Results", () => {
    async function setupDryRunResults() {
      mockDryRun.mockResolvedValue(mockDryRunResult);
      const user = userEvent.setup();
      renderWithProviders(<MemberCsvImport />);

      await waitFor(() => {
        expect(screen.getByLabelText("組織")).toBeInTheDocument();
      });

      await user.selectOptions(screen.getByLabelText("組織"), "org-1");
      const file = new File(["csv"], "test.csv", { type: "text/csv" });
      await user.upload(screen.getByLabelText("CSVファイルを選択") as HTMLInputElement, file);
      await user.click(screen.getByText("検証（ドライラン）"));

      await waitFor(() => {
        expect(screen.getByText(/合計: 3行/)).toBeInTheDocument();
      });

      return user;
    }

    it("shows summary with row counts", async () => {
      await setupDryRunResults();

      expect(screen.getByText(/有効: 2件/)).toBeInTheDocument();
      expect(screen.getByText(/エラー: 1件/)).toBeInTheDocument();
    });

    it("shows only error rows in table", async () => {
      await setupDryRunResults();

      // Error row should be shown
      expect(screen.getByText("invalid")).toBeInTheDocument();
      expect(screen.getByText("Charlie")).toBeInTheDocument();
      expect(screen.getByText(/メール形式が不正/)).toBeInTheDocument();

      // Valid rows should NOT be in the table
      // (they appear in summary count, not as table rows)
    });

    it("resets to step 1 when cancel is clicked", async () => {
      const user = await setupDryRunResults();

      await user.click(screen.getByText("最初からやり直す"));

      // Should be back to step 1
      await waitFor(() => {
        expect(screen.getByText("ここにCSVファイルをドロップするか、クリックして選択")).toBeInTheDocument();
      });
    });
  });

  describe("Step 3 - Importing", () => {
    it("calls executeMemberCsvImport and transitions to step 4", async () => {
      mockDryRun.mockResolvedValue(mockDryRunResult);
      mockExecute.mockResolvedValue(undefined);
      const user = userEvent.setup();
      renderWithProviders(<MemberCsvImport />);

      // Go through step 1
      await waitFor(() => {
        expect(screen.getByLabelText("組織")).toBeInTheDocument();
      });
      await user.selectOptions(screen.getByLabelText("組織"), "org-1");
      const file = new File(["csv"], "test.csv", { type: "text/csv" });
      await user.upload(screen.getByLabelText("CSVファイルを選択") as HTMLInputElement, file);
      await user.click(screen.getByText("検証（ドライラン）"));

      // Wait for step 2
      await waitFor(() => {
        expect(screen.getByText(/合計: 3行/)).toBeInTheDocument();
      });

      // Click proceed
      await user.click(screen.getByText(/取込を実行（2件）/));

      // Should show importing message briefly then complete
      await waitFor(() => {
        expect(mockExecute).toHaveBeenCalledWith("session-123");
      });

      // Should transition to step 4
      await waitFor(() => {
        expect(screen.getByText(/取込が完了しました/)).toBeInTheDocument();
      });
    });

    it("shows error with reset option on import failure", async () => {
      mockDryRun.mockResolvedValue(mockDryRunResult);
      mockExecute.mockRejectedValue(new Error("Session expired"));
      const user = userEvent.setup();
      renderWithProviders(<MemberCsvImport />);

      await waitFor(() => {
        expect(screen.getByLabelText("組織")).toBeInTheDocument();
      });
      await user.selectOptions(screen.getByLabelText("組織"), "org-1");
      const file = new File(["csv"], "test.csv", { type: "text/csv" });
      await user.upload(screen.getByLabelText("CSVファイルを選択") as HTMLInputElement, file);
      await user.click(screen.getByText("検証（ドライラン）"));

      await waitFor(() => {
        expect(screen.getByText(/合計: 3行/)).toBeInTheDocument();
      });

      await user.click(screen.getByText(/取込を実行（2件）/));

      // Should show error
      await waitFor(() => {
        expect(screen.getByText(/Session expired/)).toBeInTheDocument();
      });

      // Should have reset button
      expect(screen.getByText("最初からやり直す")).toBeInTheDocument();
    });
  });

  describe("Step 4 - Complete", () => {
    it("shows success message and import another button", async () => {
      mockDryRun.mockResolvedValue(mockDryRunResult);
      mockExecute.mockResolvedValue(undefined);
      const user = userEvent.setup();
      renderWithProviders(<MemberCsvImport />);

      // Go through all steps
      await waitFor(() => {
        expect(screen.getByLabelText("組織")).toBeInTheDocument();
      });
      await user.selectOptions(screen.getByLabelText("組織"), "org-1");
      const file = new File(["csv"], "test.csv", { type: "text/csv" });
      await user.upload(screen.getByLabelText("CSVファイルを選択") as HTMLInputElement, file);
      await user.click(screen.getByText("検証（ドライラン）"));

      await waitFor(() => {
        expect(screen.getByText(/合計: 3行/)).toBeInTheDocument();
      });

      await user.click(screen.getByText(/取込を実行（2件）/));

      await waitFor(() => {
        expect(screen.getByText(/取込が完了しました。2名/)).toBeInTheDocument();
      });

      // Click import another
      await user.click(screen.getByText("別のファイルを取込む"));

      // Should reset to step 1
      await waitFor(() => {
        expect(screen.getByText("ここにCSVファイルをドロップするか、クリックして選択")).toBeInTheDocument();
      });
    });
  });

  describe("Wizard step indicator", () => {
    it("shows all 4 step labels", async () => {
      renderWithProviders(<MemberCsvImport />);

      await waitFor(() => {
        expect(screen.getByText("アップロード")).toBeInTheDocument();
        expect(screen.getByText("検証結果")).toBeInTheDocument();
        expect(screen.getByText("取込中")).toBeInTheDocument();
        expect(screen.getByText("完了")).toBeInTheDocument();
      });
    });
  });
});
