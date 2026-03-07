import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { IntlWrapper } from "../../../helpers/intl";

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      rules: {
        createFiscalYearRule: vi.fn(),
      },
    },
  },
}));

import { FiscalYearRuleForm } from "@/components/admin/FiscalYearRuleForm";
import { api } from "@/services/api";

describe("FiscalYearRuleForm", () => {
  const defaultProps = {
    tenantId: "tenant-1",
    open: true,
    onClose: vi.fn(),
    onCreated: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    (api.admin.rules.createFiscalYearRule as ReturnType<typeof vi.fn>).mockResolvedValue({
      id: "new-rule-id",
      tenantId: "tenant-1",
      name: "4月1日開始",
      startMonth: 4,
      startDay: 1,
    });
  });

  it("renders form fields", () => {
    render(
      <IntlWrapper>
        <FiscalYearRuleForm {...defaultProps} />
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
        <FiscalYearRuleForm {...defaultProps} />
      </IntlWrapper>,
    );

    await user.clear(screen.getByLabelText(/名前/));
    await user.type(screen.getByLabelText(/名前/), "4月1日開始");
    await user.click(screen.getByRole("button", { name: /作成/ }));

    await waitFor(() => {
      expect(api.admin.rules.createFiscalYearRule).toHaveBeenCalledWith(
        "tenant-1",
        expect.objectContaining({ name: "4月1日開始" }),
      );
      expect(defaultProps.onCreated).toHaveBeenCalled();
    });
  });
});
