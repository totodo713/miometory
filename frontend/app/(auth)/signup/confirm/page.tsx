"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";

export default function SignupConfirmPage() {
  const t = useTranslations("auth.signup.confirm");

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div className="max-w-md w-full bg-white rounded-lg shadow p-8 text-center">
        <div className="mb-6">
          <svg
            className="mx-auto h-12 w-12 text-green-500"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
        </div>
        <h1 className="text-xl font-bold text-gray-900 mb-4">{t("title")}</h1>
        <p className="text-gray-600 mb-8">{t("message")}</p>
        <Link
          href="/login"
          className="inline-block py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
        >
          {t("backToLogin")}
        </Link>
      </div>
    </div>
  );
}
