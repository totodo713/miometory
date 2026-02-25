import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { IntlWrapper } from "../../../helpers/intl";

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      patterns: {
        createFiscalYearPattern: vi.fn(),
      },
    },
  },
}));

import { FiscalYearPatternForm } from "@/components/admin/FiscalYearPatternForm";
import { api } from "@/services/api";

describe("FiscalYearPatternForm", () => {
  const defaultProps = {
    tenantId: "tenant-1",
    open: true,
    onClose: vi.fn(),
    onCreated: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    (api.admin.patterns.createFiscalYearPattern as ReturnType<typeof vi.fn>).mockResolvedValue({
      id: "new-pattern-id",
      tenantId: "tenant-1",
      name: "4月1日開始",
      startMonth: 4,
      startDay: 1,
    });
  });

  it("renders form fields", () => {
    render(
      <IntlWrapper>
        <FiscalYearPatternForm {...defaultProps} />
      </IntlWrapper>,
    );
    expect(screen.getByLabelText(/名前/)).toBeInTheDocument();
    expect(screen.getByLabelText(/開始月/)).toBeInTheDocument();
    expect(screen.getByLabelText(/開始日/)).toBeInTheDocument();
  });

  it("submits form and calls onCreated", async () => {
    const user = userEvent.setup();
    render(
      <IntlWrapper>
        <FiscalYearPatternForm {...defaultProps} />
      </IntlWrapper>,
    );

    await user.clear(screen.getByLabelText(/名前/));
    await user.type(screen.getByLabelText(/名前/), "4月1日開始");
    await user.click(screen.getByRole("button", { name: /作成/ }));

    await waitFor(() => {
      expect(api.admin.patterns.createFiscalYearPattern).toHaveBeenCalledWith(
        "tenant-1",
        expect.objectContaining({ name: "4月1日開始" }),
      );
      expect(defaultProps.onCreated).toHaveBeenCalled();
    });
  });
});
