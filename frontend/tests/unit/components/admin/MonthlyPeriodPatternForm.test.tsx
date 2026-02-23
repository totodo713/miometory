import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@/services/api", () => ({
	api: {
		admin: {
			patterns: {
				createMonthlyPeriodPattern: vi.fn(),
			},
		},
	},
}));

import { MonthlyPeriodPatternForm } from "@/components/admin/MonthlyPeriodPatternForm";
import { api } from "@/services/api";

describe("MonthlyPeriodPatternForm", () => {
	const defaultProps = {
		tenantId: "tenant-1",
		open: true,
		onClose: vi.fn(),
		onCreated: vi.fn(),
	};

	beforeEach(() => {
		vi.clearAllMocks();
		(api.admin.patterns.createMonthlyPeriodPattern as ReturnType<typeof vi.fn>).mockResolvedValue({
			id: "new-pattern-id",
			tenantId: "tenant-1",
			name: "21日開始",
			startDay: 21,
		});
	});

	it("renders form fields", () => {
		render(<MonthlyPeriodPatternForm {...defaultProps} />);
		expect(screen.getByLabelText(/名前/)).toBeInTheDocument();
		expect(screen.getByLabelText(/開始日/)).toBeInTheDocument();
	});

	it("submits form and calls onCreated", async () => {
		const user = userEvent.setup();
		render(<MonthlyPeriodPatternForm {...defaultProps} />);

		await user.clear(screen.getByLabelText(/名前/));
		await user.type(screen.getByLabelText(/名前/), "21日開始");
		await user.click(screen.getByRole("button", { name: /作成/ }));

		await waitFor(() => {
			expect(api.admin.patterns.createMonthlyPeriodPattern).toHaveBeenCalledWith(
				"tenant-1",
				expect.objectContaining({ name: "21日開始" }),
			);
			expect(defaultProps.onCreated).toHaveBeenCalled();
		});
	});
});
