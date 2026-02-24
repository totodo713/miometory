"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Breadcrumbs } from "@/components/shared/Breadcrumbs";
import { EmptyState } from "@/components/shared/EmptyState";
import { Skeleton } from "@/components/shared/Skeleton";
import { useToast } from "@/hooks/useToast";
import { api } from "@/services/api";

interface Notification {
  id: string;
  type: string;
  referenceId: string;
  title: string;
  message: string;
  isRead: boolean;
  createdAt: string;
}

type FilterValue = "all" | "unread" | "read";

function getRelativeTime(dateString: string): string {
  const now = new Date();
  const date = new Date(dateString);
  const diffMs = now.getTime() - date.getTime();
  const diffMinutes = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMinutes < 1) return "たった今";
  if (diffMinutes < 60) return `${diffMinutes}分前`;
  if (diffHours < 24) return `${diffHours}時間前`;
  if (diffDays < 30) return `${diffDays}日前`;
  return date.toLocaleDateString("ja-JP");
}

function getTypeIcon(type: string): string {
  switch (type) {
    case "APPROVAL":
      return "check-circle";
    case "REJECTION":
      return "x-circle";
    case "REMINDER":
      return "clock";
    case "SYSTEM":
      return "info";
    default:
      return "bell";
  }
}

function getTypeColor(type: string): string {
  switch (type) {
    case "APPROVAL":
      return "text-green-600";
    case "REJECTION":
      return "text-red-600";
    case "REMINDER":
      return "text-yellow-600";
    case "SYSTEM":
      return "text-blue-600";
    default:
      return "text-gray-600";
  }
}

function TypeIcon({ type }: { type: string }) {
  const icon = getTypeIcon(type);
  const color = getTypeColor(type);

  const iconPaths: Record<string, React.ReactNode> = {
    "check-circle": (
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
      />
    ),
    "x-circle": (
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"
      />
    ),
    clock: (
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
      />
    ),
    info: (
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
      />
    ),
    bell: (
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
      />
    ),
  };

  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      className={`h-6 w-6 ${color}`}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      role="img"
      aria-label={type}
    >
      {iconPaths[icon]}
    </svg>
  );
}

const PAGE_SIZE = 20;

export default function NotificationsPage() {
  const router = useRouter();
  const toast = useToast();
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [filter, setFilter] = useState<FilterValue>("all");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [unreadCount, setUnreadCount] = useState(0);

  const loadNotifications = useCallback(async () => {
    setIsLoading(true);
    try {
      const isReadParam = filter === "unread" ? false : filter === "read" ? true : undefined;
      const data = await api.notification.list({ page, size: PAGE_SIZE, isRead: isReadParam });
      setNotifications(data.content);
      setTotalPages(data.totalPages ?? 0);
      setTotalElements(data.totalElements);
      setUnreadCount(data.unreadCount);
    } catch {
      toast.error("通知の取得に失敗しました");
    } finally {
      setIsLoading(false);
    }
  }, [filter, page, toast.error]);

  useEffect(() => {
    loadNotifications();
  }, [loadNotifications]);

  const handleMarkAllRead = async () => {
    try {
      await api.notification.markAllRead();
      await loadNotifications();
    } catch {
      toast.error("既読処理に失敗しました");
    }
  };

  const handleNotificationClick = async (notification: Notification) => {
    if (!notification.isRead) {
      // Fire-and-forget: don't block navigation for mark-read
      api.notification.markRead(notification.id).catch(() => {
        // Silent — next poll will re-sync
      });
    }
    if (notification.referenceId) {
      router.push(`/worklog/approval?id=${notification.referenceId}`);
    } else {
      await loadNotifications();
    }
  };

  const handleFilterChange = (newFilter: FilterValue) => {
    setFilter(newFilter);
    setPage(0);
  };

  const filterTabs: { label: string; value: FilterValue }[] = [
    { label: "すべて", value: "all" },
    { label: "未読", value: "unread" },
    { label: "既読", value: "read" },
  ];

  return (
    <div>
      <Breadcrumbs items={[{ label: "ホーム", href: "/" }, { label: "通知" }]} />

      <div className="flex items-center justify-between mb-6 mt-4">
        <h1 className="text-2xl font-bold text-gray-900">
          通知
          {unreadCount > 0 && (
            <span className="ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800">
              {unreadCount}
            </span>
          )}
        </h1>
        <button
          type="button"
          onClick={handleMarkAllRead}
          className="px-4 py-2 text-sm text-blue-600 bg-blue-50 rounded-md hover:bg-blue-100"
        >
          すべて既読にする
        </button>
      </div>

      {/* Filter tabs */}
      <div className="flex gap-1 mb-6 border-b border-gray-200">
        {filterTabs.map((tab) => (
          <button
            key={tab.value}
            type="button"
            onClick={() => handleFilterChange(tab.value)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              filter === tab.value
                ? "border-blue-600 text-blue-600"
                : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <Skeleton.Table rows={5} cols={3} />
        </div>
      ) : notifications.length === 0 ? (
        <EmptyState title="通知はありません" description="新しい通知はまだありません" />
      ) : (
        <div className="space-y-2">
          {notifications.map((notification) => (
            <button
              key={notification.id}
              type="button"
              onClick={() => handleNotificationClick(notification)}
              className={`w-full text-left bg-white rounded-lg border p-4 transition-colors hover:bg-gray-50 ${
                notification.isRead ? "border-gray-200" : "border-blue-200 bg-blue-50/30"
              }`}
            >
              <div className="flex items-start gap-3">
                <div className="flex-shrink-0 mt-0.5">
                  <TypeIcon type={notification.type} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <h3
                      className={`text-sm font-medium ${notification.isRead ? "text-gray-900" : "text-gray-900 font-semibold"}`}
                    >
                      {notification.title}
                    </h3>
                    {!notification.isRead && (
                      <span
                        aria-hidden="true"
                        className="inline-block w-2 h-2 rounded-full bg-blue-600 flex-shrink-0"
                      />
                    )}
                  </div>
                  <p className="text-sm text-gray-600 mt-0.5 truncate">{notification.message}</p>
                </div>
                <div className="flex-shrink-0 text-xs text-gray-500">{getRelativeTime(notification.createdAt)}</div>
              </div>
            </button>
          ))}
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-6">
          <p className="text-sm text-gray-600">
            全{totalElements}件中 {page * PAGE_SIZE + 1}-{Math.min((page + 1) * PAGE_SIZE, totalElements)}件
          </p>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1 text-sm border rounded-md disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
            >
              前へ
            </button>
            <span className="text-sm text-gray-600">
              {page + 1} / {totalPages}
            </span>
            <button
              type="button"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="px-3 py-1 text-sm border rounded-md disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
            >
              次へ
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
