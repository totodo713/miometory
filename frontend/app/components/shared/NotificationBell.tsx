"use client";

import { useEffect, useRef, useState } from "react";
import { useNotifications } from "@/hooks/useNotifications";

export function NotificationBell() {
  const { notifications, unreadCount, markRead, markAllRead } = useNotifications();
  const [isOpen, setIsOpen] = useState(false);
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
    if (minutes < 1) return "たった今";
    if (minutes < 60) return `${minutes}分前`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}時間前`;
    const days = Math.floor(hours / 24);
    return `${days}日前`;
  };

  return (
    <div ref={dropdownRef} className="relative">
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="relative p-2 text-gray-600 hover:text-gray-800"
        aria-label="通知"
      >
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" role="img" aria-label="通知ベル">
          <title>通知</title>
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
          />
        </svg>
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 inline-flex items-center justify-center w-4 h-4 text-[10px] font-bold text-white bg-red-500 rounded-full">
            {unreadCount > 9 ? "9+" : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div className="absolute right-0 mt-2 w-80 bg-white rounded-lg shadow-lg border border-gray-200 z-50">
          <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
            <h3 className="text-sm font-semibold text-gray-900">通知</h3>
            {unreadCount > 0 && (
              <button type="button" onClick={markAllRead} className="text-xs text-blue-600 hover:text-blue-800">
                すべて既読
              </button>
            )}
          </div>
          <div className="max-h-80 overflow-y-auto">
            {notifications.length === 0 ? (
              <p className="px-4 py-6 text-center text-sm text-gray-500">通知はありません</p>
            ) : (
              notifications.map((notification) => (
                <button
                  type="button"
                  key={notification.id}
                  onClick={() => {
                    if (!notification.isRead) {
                      markRead(notification.id);
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
                      <p className="text-xs text-gray-400 mt-1">{formatTimeAgo(notification.createdAt)}</p>
                    </div>
                  </div>
                </button>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
