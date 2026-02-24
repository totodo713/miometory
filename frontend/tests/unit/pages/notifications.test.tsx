import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/shared/ToastProvider";

const mockPush = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
}));

vi.mock("@/services/api", () => ({
  api: {
    notification: {
      list: vi.fn(),
      markRead: vi.fn(),
      markAllRead: vi.fn(),
    },
  },
}));

import { api } from "@/services/api";
import NotificationsPage from "../../../app/notifications/page";

function renderWithProviders(ui: ReactElement) {
  return render(<ToastProvider>{ui}</ToastProvider>);
}

describe("NotificationsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (api.notification.list as any).mockResolvedValue({
      content: [
        {
          id: "1",
          type: "APPROVAL",
          title: "承認依頼",
          message: "1月分の承認",
          isRead: false,
          createdAt: new Date().toISOString(),
        },
        {
          id: "2",
          type: "REJECTION",
          title: "却下通知",
          message: "1月分が却下",
          isRead: true,
          createdAt: new Date().toISOString(),
        },
      ],
      totalElements: 2,
      unreadCount: 1,
    });
  });

  it("renders notification list", async () => {
    renderWithProviders(<NotificationsPage />);
    await waitFor(() => {
      expect(screen.getByText("承認依頼")).toBeInTheDocument();
      expect(screen.getByText("却下通知")).toBeInTheDocument();
    });
  });

  it("marks all as read", async () => {
    const user = userEvent.setup();
    (api.notification.markAllRead as any).mockResolvedValue(undefined);
    renderWithProviders(<NotificationsPage />);
    await waitFor(() => {
      expect(screen.getByText("承認依頼")).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: /すべて既読/ }));
    expect(api.notification.markAllRead).toHaveBeenCalled();
  });
});
