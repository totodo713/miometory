import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";

describe("ConfirmDialog", () => {
  const defaultProps = {
    open: true,
    title: "確認",
    message: "本当に無効化しますか？",
    confirmLabel: "無効化",
    variant: "danger" as const,
    onConfirm: vi.fn(),
    onCancel: vi.fn(),
  };

  it("renders title and message when open", () => {
    render(<ConfirmDialog {...defaultProps} />);
    expect(screen.getByText("確認")).toBeInTheDocument();
    expect(screen.getByText("本当に無効化しますか？")).toBeInTheDocument();
  });

  it("has aria-modal=true on the dialog", () => {
    render(<ConfirmDialog {...defaultProps} />);
    const dialog = screen.getByRole("alertdialog");
    expect(dialog).toHaveAttribute("aria-modal", "true");
  });

  it("does not render when closed", () => {
    render(<ConfirmDialog {...defaultProps} open={false} />);
    expect(screen.queryByText("確認")).not.toBeInTheDocument();
  });

  it("calls onConfirm when confirm button clicked", async () => {
    const user = userEvent.setup();
    render(<ConfirmDialog {...defaultProps} />);
    await user.click(screen.getByRole("button", { name: "無効化" }));
    expect(defaultProps.onConfirm).toHaveBeenCalled();
  });

  it("calls onCancel when cancel button clicked", async () => {
    const user = userEvent.setup();
    render(<ConfirmDialog {...defaultProps} />);
    await user.click(screen.getByRole("button", { name: "キャンセル" }));
    expect(defaultProps.onCancel).toHaveBeenCalled();
  });

  it("calls onCancel when Escape is pressed", async () => {
    const user = userEvent.setup();
    render(<ConfirmDialog {...defaultProps} />);
    await user.keyboard("{Escape}");
    expect(defaultProps.onCancel).toHaveBeenCalled();
  });
});
