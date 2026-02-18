"use client";

import { useAuthContext } from "@/providers/AuthProvider";

export function Header() {
  const { user, logout } = useAuthContext();

  if (!user) {
    return null;
  }

  return (
    <header className="h-14 bg-white border-b border-gray-200 flex items-center justify-between px-6">
      <span className="text-lg font-semibold text-gray-900">Miometry</span>
      <div className="flex items-center gap-4">
        <span className="text-sm text-gray-700">{user.displayName}</span>
        <button type="button" onClick={logout} className="text-sm text-gray-500 hover:text-gray-700">
          ログアウト
        </button>
      </div>
    </header>
  );
}
