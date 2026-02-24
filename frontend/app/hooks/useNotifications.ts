"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/services/api";

interface Notification {
  id: string;
  type: string;
  referenceId: string | null;
  title: string;
  message: string;
  isRead: boolean;
  createdAt: string;
}

export function useNotifications() {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchNotifications = useCallback(async () => {
    try {
      const result = await api.notification.list({ page: 0, size: 10 });
      setNotifications(result.content);
      setUnreadCount(result.unreadCount);
    } catch {
      // Ignore polling errors
    } finally {
      setIsLoading(false);
    }
  }, []);

  const markRead = useCallback(async (id: string) => {
    // Optimistic update
    setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, isRead: true } : n)));
    setUnreadCount((prev) => Math.max(0, prev - 1));
    try {
      await api.notification.markRead(id);
    } catch (e) {
      // Rollback on failure
      setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, isRead: false } : n)));
      setUnreadCount((prev) => prev + 1);
      throw e;
    }
  }, []);

  const markAllRead = useCallback(async () => {
    const prevNotifications = notifications;
    const prevUnreadCount = unreadCount;
    // Optimistic update
    setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
    setUnreadCount(0);
    try {
      await api.notification.markAllRead();
    } catch (e) {
      // Rollback on failure
      setNotifications(prevNotifications);
      setUnreadCount(prevUnreadCount);
      throw e;
    }
  }, [notifications, unreadCount]);

  useEffect(() => {
    fetchNotifications();

    const startPolling = () => {
      intervalRef.current = setInterval(() => {
        if (!document.hidden) {
          fetchNotifications();
        }
      }, 30000);
    };

    startPolling();

    const handleVisibilityChange = () => {
      if (!document.hidden) {
        fetchNotifications();
      }
    };
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [fetchNotifications]);

  return { notifications, unreadCount, markRead, markAllRead, isLoading };
}
