import { render } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { Skeleton } from "@/components/shared/Skeleton";

describe("Skeleton", () => {
	it("renders table skeleton with correct rows and cols", () => {
		const { container } = render(<Skeleton.Table rows={3} cols={4} />);
		const rows = container.querySelectorAll("tr");
		expect(rows).toHaveLength(3);
		rows.forEach((row) => {
			expect(row.querySelectorAll("td")).toHaveLength(4);
		});
	});

	it("renders text skeleton with correct lines", () => {
		const { container } = render(<Skeleton.Text lines={3} />);
		const bars = container.querySelectorAll('[data-testid="skeleton-line"]');
		expect(bars).toHaveLength(3);
	});

	it("renders card skeleton", () => {
		const { container } = render(<Skeleton.Card />);
		expect(container.querySelector('[data-testid="skeleton-card"]')).toBeInTheDocument();
	});
});
