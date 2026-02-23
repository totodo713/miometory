import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, afterEach, vi } from "vitest";
import { ToastProvider } from "@/components/shared/ToastProvider";
import { useToast } from "@/hooks/useToast";

function TestComponent() {
	const toast = useToast();
	return (
		<>
			<button type="button" onClick={() => toast.success("保存しました")}>
				success
			</button>
			<button type="button" onClick={() => toast.error("エラーが発生しました")}>
				error
			</button>
		</>
	);
}

describe("Toast", () => {
	afterEach(() => {
		vi.useRealTimers();
	});

	it("shows success toast and auto-dismisses after 3s", () => {
		vi.useFakeTimers();
		render(
			<ToastProvider>
				<TestComponent />
			</ToastProvider>,
		);
		fireEvent.click(screen.getByText("success"));
		expect(screen.getByText("保存しました")).toBeInTheDocument();

		act(() => {
			vi.advanceTimersByTime(3500);
		});
		expect(screen.queryByText("保存しました")).not.toBeInTheDocument();
	});

	it("shows error toast", async () => {
		const user = userEvent.setup();
		render(
			<ToastProvider>
				<TestComponent />
			</ToastProvider>,
		);
		await user.click(screen.getByText("error"));
		expect(screen.getByText("エラーが発生しました")).toBeInTheDocument();
	});

	it("can be manually dismissed", async () => {
		const user = userEvent.setup();
		render(
			<ToastProvider>
				<TestComponent />
			</ToastProvider>,
		);
		await user.click(screen.getByText("success"));
		const closeButton = screen.getByRole("button", { name: /閉じる/ });
		await user.click(closeButton);
		expect(screen.queryByText("保存しました")).not.toBeInTheDocument();
	});
});
