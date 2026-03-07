import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MypagePage from "@/mypage/page";
import { IntlWrapper } from "../../helpers/intl";

const mockLogout = vi.fn();
const mockUpdateUser = vi.fn();
const mockToastSuccess = vi.fn();
const mockToastError = vi.fn();

vi.mock("@/providers/AuthProvider", () => ({
  useAuthContext: () => ({
    user: { id: "u1", email: "test@example.com", displayName: "テストユーザー", memberId: "m1" },
    logout: mockLogout,
    updateUser: mockUpdateUser,
  }),
}));

vi.mock("@/hooks/useToast", () => ({
  useToast: () => ({
    success: mockToastSuccess,
    error: mockToastError,
  }),
}));

const mockProfileGet = vi.fn();
const mockProfileUpdate = vi.fn();
const mockGetAssignedProjects = vi.fn();

vi.mock("@/services/api", () => ({
  api: {
    profile: {
      get: () => mockProfileGet(),
      update: (data: any) => mockProfileUpdate(data),
    },
    members: {
      getAssignedProjects: (id: string) => mockGetAssignedProjects(id),
    },
  },
}));

describe("MypagePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockProfileGet.mockResolvedValue({
      id: "m1",
      email: "test@example.com",
      displayName: "テストユーザー",
      organizationName: "開発部",
      managerName: "マネージャー太郎",
      isActive: true,
    });
    mockGetAssignedProjects.mockResolvedValue({
      projects: [
        { id: "p1", code: "PRJ-001", name: "基幹システム開発" },
        { id: "p2", code: "PRJ-002", name: "Web改修" },
      ],
      totalCount: 2,
    });
  });

  test("displays profile information", async () => {
    render(
      <IntlWrapper>
        <MypagePage />
      </IntlWrapper>,
    );
    await waitFor(() => {
      expect(screen.getByText("テストユーザー")).toBeInTheDocument();
    });
    expect(screen.getByText("test@example.com")).toBeInTheDocument();
    expect(screen.getByText("開発部")).toBeInTheDocument();
    expect(screen.getByText("マネージャー太郎")).toBeInTheDocument();
  });

  test("displays assigned projects", async () => {
    render(
      <IntlWrapper>
        <MypagePage />
      </IntlWrapper>,
    );
    await waitFor(() => {
      expect(screen.getByText("PRJ-001")).toBeInTheDocument();
    });
    expect(screen.getByText("基幹システム開発")).toBeInTheDocument();
    expect(screen.getByText("PRJ-002")).toBeInTheDocument();
    expect(screen.getByText("Web改修")).toBeInTheDocument();
  });

  test("shows no projects message when empty", async () => {
    mockGetAssignedProjects.mockResolvedValue({ projects: [], totalCount: 0 });
    render(
      <IntlWrapper>
        <MypagePage />
      </IntlWrapper>,
    );
    await waitFor(() => {
      expect(screen.getByText("プロジェクトが割り当てられていません")).toBeInTheDocument();
    });
  });

  test("opens edit modal and saves profile", async () => {
    mockProfileUpdate.mockResolvedValue({ emailChanged: false });
    render(
      <IntlWrapper>
        <MypagePage />
      </IntlWrapper>,
    );
    await waitFor(() => {
      expect(screen.getByText("テストユーザー")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("プロフィールを編集"));
    expect(screen.getByText("プロフィール編集")).toBeInTheDocument();

    const nameInput = screen.getByLabelText("表示名");
    fireEvent.change(nameInput, { target: { value: "新しい名前" } });
    fireEvent.click(screen.getByText("保存"));

    await waitFor(() => {
      expect(mockProfileUpdate).toHaveBeenCalledWith({
        email: "test@example.com",
        displayName: "新しい名前",
      });
    });
    expect(mockUpdateUser).toHaveBeenCalledWith({
      displayName: "新しい名前",
      email: "test@example.com",
    });
    expect(mockToastSuccess).toHaveBeenCalled();
  });

  test("shows validation errors for empty fields", async () => {
    render(
      <IntlWrapper>
        <MypagePage />
      </IntlWrapper>,
    );
    await waitFor(() => {
      expect(screen.getByText("テストユーザー")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("プロフィールを編集"));

    const nameInput = screen.getByLabelText("表示名");
    const emailInput = screen.getByLabelText("メールアドレス");
    fireEvent.change(nameInput, { target: { value: "" } });
    fireEvent.change(emailInput, { target: { value: "" } });
    fireEvent.click(screen.getByText("保存"));

    expect(screen.getByText("表示名を入力してください")).toBeInTheDocument();
    expect(screen.getByText("メールアドレスを入力してください")).toBeInTheDocument();
    expect(mockProfileUpdate).not.toHaveBeenCalled();
  });

  test("shows error state on profile load failure", async () => {
    mockProfileGet.mockRejectedValue(new Error("network error"));
    render(
      <IntlWrapper>
        <MypagePage />
      </IntlWrapper>,
    );
    await waitFor(() => {
      expect(screen.getByText("プロフィールの読み込みに失敗しました")).toBeInTheDocument();
    });
  });

  test("shows email changed dialog and logs out", async () => {
    mockProfileUpdate.mockResolvedValue({ emailChanged: true });
    render(
      <IntlWrapper>
        <MypagePage />
      </IntlWrapper>,
    );
    await waitFor(() => {
      expect(screen.getByText("テストユーザー")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("プロフィールを編集"));
    const emailInput = screen.getByLabelText("メールアドレス");
    fireEvent.change(emailInput, { target: { value: "new@example.com" } });
    fireEvent.click(screen.getByText("保存"));

    await waitFor(() => {
      expect(screen.getByText("メールアドレスが変更されました")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByText("ログアウト"));
    expect(mockLogout).toHaveBeenCalled();
  });
});
