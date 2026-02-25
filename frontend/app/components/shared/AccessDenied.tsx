"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";

export function AccessDenied() {
  const t = useTranslations("accessDenied");

  return (
    <div className="flex flex-col items-center justify-center py-16 px-4">
      <svg
        className="w-16 h-16 text-gray-400 mb-4"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={1.5}
          d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
        />
      </svg>
      <h2 className="text-xl font-semibold text-gray-900 mb-2">{t("title")}</h2>
      <p className="text-sm text-gray-600 mb-6 text-center max-w-md">{t("message")}</p>
      <Link
        href="/admin"
        className="px-4 py-2 text-sm text-blue-600 bg-blue-50 rounded-md hover:bg-blue-100 transition-colors"
      >
        {t("backToDashboard")}
      </Link>
    </div>
  );
}
