"use client";

import type { ToastItem } from "@/hooks/useToast";

const variantStyles: Record<string, string> = {
  success: "bg-green-50 border-green-200 text-green-800",
  error: "bg-red-50 border-red-200 text-red-800",
  warning: "bg-yellow-50 border-yellow-200 text-yellow-800",
  info: "bg-blue-50 border-blue-200 text-blue-800",
};

interface ToastProps {
  toast: ToastItem;
  onClose: () => void;
}

export function Toast({ toast, onClose }: ToastProps) {
  return (
    <div
      className={`flex items-center gap-3 px-4 py-3 rounded-lg border shadow-lg min-w-72 animate-in slide-in-from-right ${variantStyles[toast.variant]}`}
      role={toast.variant === "error" ? "alert" : "status"}
    >
      <span className="flex-1 text-sm font-medium">{toast.message}</span>
      <button type="button" onClick={onClose} className="text-current opacity-60 hover:opacity-100" aria-label="閉じる">
        ✕
      </button>
    </div>
  );
}
