import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { Breadcrumbs } from "@/components/shared/Breadcrumbs";

describe("Breadcrumbs", () => {
	it("renders items with links except the last one", () => {
		render(
			<Breadcrumbs
				items={[
					{ label: "管理", href: "/admin" },
					{ label: "メンバー管理" },
				]}
			/>,
		);
		const link = screen.getByRole("link", { name: "管理" });
		expect(link).toHaveAttribute("href", "/admin");
		expect(screen.getByText("メンバー管理")).toBeInTheDocument();
		expect(screen.queryByRole("link", { name: "メンバー管理" })).not.toBeInTheDocument();
	});

	it("renders separator between items", () => {
		const { container } = render(
			<Breadcrumbs
				items={[
					{ label: "管理", href: "/admin" },
					{ label: "テナント管理" },
				]}
			/>,
		);
		expect(container.textContent).toContain("/");
	});
});
