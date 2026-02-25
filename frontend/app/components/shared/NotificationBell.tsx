"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useEffect, useRef, useState } from "react";
import { EmptyState } from "@/components/shared/EmptyState";
import { useNotifications } from "@/hooks/useNotifications";

export function NotificationBell() {
  const t = useTranslations("notifications.bell");
  const tt = useTranslations("notifications.time");
  const { notifications, unreadCount, markRead, markAllRead } = useNotifications();
  const [isOpen, setIsOpen] = useState(false);
  const router = useRouter();
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const formatTimeAgo = (dateStr: string) => {
    const diff = Date.now() - new Date(dateStr).getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 1) return tt("justNow");
    if (minutes < 60) return tt("minutesAgo", { count: minutes });
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return tt("hoursAgo", { count: hours });
    const days = Math.floor(hours / 24);
    return tt("daysAgo", { count: days });
  };

  return (
    <div ref={dropdownRef} className="relative">
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="relative p-2 text-gray-600 hover:text-gray-800"
        aria-label={t("label")}
      >
        <svg
          className="w-5 h-5"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          role="img"
          aria-label={t("label")}
        >
          <title>{t("label")}</title>
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
          />
        </svg>
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 text-[10px] font-bold text-white bg-red-500 rounded-full">
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div className="absolute right-0 mt-2 w-80 bg-white rounded-lg shadow-lg border border-gray-200 z-50">
          <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
            <h3 className="text-sm font-semibold text-gray-900">{t("label")}</h3>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={() => markAllRead().catch(() => {})}
                className="text-xs text-blue-600 hover:text-blue-800"
              >
                {t("markAllRead")}
              </button>
            )}
          </div>
          <div className="max-h-80 overflow-y-auto">
            {notifications.length === 0 ? (
              <EmptyState title={t("noNotifications")} description="" />
            ) : (
              notifications.map((notification) => (
                <button
                  type="button"
                  key={notification.id}
                  onClick={() => {
                    if (!notification.isRead) {
                      markRead(notification.id).catch(() => {});
                    }
                    if (notification.referenceId) {
                      router.push(`/worklog/approval?id=${notification.referenceId}`);
                      setIsOpen(false);
                    }
                  }}
                  className={`w-full text-left px-4 py-3 border-b border-gray-50 hover:bg-gray-50 ${
                    !notification.isRead ? "bg-blue-50" : ""
                  }`}
                >
                  <div className="flex items-start gap-2">
                    {!notification.isRead && <span className="mt-1.5 w-2 h-2 rounded-full bg-blue-500 flex-shrink-0" />}
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-gray-900 truncate">{notification.title}</p>
                      <p className="text-xs text-gray-600 mt-0.5 line-clamp-2">{notification.message}</p>
                      <p className="text-xs text-gray-500 mt-1">{formatTimeAgo(notification.createdAt)}</p>
                    </div>
                  </div>
                </button>
              ))
            )}
          </div>
          <Link
            href="/notifications"
            className="block px-4 py-3 text-center text-xs text-blue-600 hover:text-blue-800 border-t border-gray-100"
            onClick={() => setIsOpen(false)}
          >
            {t("viewAll")}
          </Link>
        </div>
      )}
    </div>
  );
}
