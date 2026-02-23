import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { EmptyState } from "@/components/shared/EmptyState";

describe("EmptyState", () => {
	it("renders title and description", () => {
		render(<EmptyState title="データなし" description="まだデータがありません" />);
		expect(screen.getByText("データなし")).toBeInTheDocument();
		expect(screen.getByText("まだデータがありません")).toBeInTheDocument();
	});

	it("renders action button when provided", async () => {
		const onClick = vi.fn();
		const user = userEvent.setup();
		render(
			<EmptyState
				title="プロジェクトなし"
				description="プロジェクトを作成してください"
				action={{ label: "新規作成", onClick }}
			/>,
		);
		await user.click(screen.getByRole("button", { name: "新規作成" }));
		expect(onClick).toHaveBeenCalled();
	});

	it("does not render action button when not provided", () => {
		render(<EmptyState title="なし" description="なし" />);
		expect(screen.queryByRole("button")).not.toBeInTheDocument();
	});
});
