import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@/services/api", () => ({
	api: {
		admin: {
			organizations: {
				getDateInfo: vi.fn(),
			},
		},
	},
}));

import { useDateInfo } from "@/hooks/useDateInfo";
import { api } from "@/services/api";

describe("useDateInfo", () => {
	beforeEach(() => {
		vi.clearAllMocks();
		(api.admin.organizations.getDateInfo as ReturnType<typeof vi.fn>).mockResolvedValue({
			fiscalYear: "2025年度",
			fiscalPeriod: "第11期",
			monthlyPeriodStart: "2026-01-21",
			monthlyPeriodEnd: "2026-02-20",
		});
	});

	it("fetches date info for given parameters", async () => {
		const { result } = renderHook(() => useDateInfo("tenant-1", "org-1", 2026, 2));
		await waitFor(() => {
			expect(result.current.data).toBeDefined();
			expect(result.current.data?.fiscalYear).toBe("2025年度");
		});
	});

	it("returns isLoading while fetching", () => {
		const { result } = renderHook(() => useDateInfo("tenant-1", "org-1", 2026, 2));
		expect(result.current.isLoading).toBe(true);
	});
});
