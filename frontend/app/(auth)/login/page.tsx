"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import type React from "react";
import { useEffect, useState } from "react";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";
import { useAuthContext } from "@/providers/AuthProvider";
import { ApiError } from "@/services/api";

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 401:
        return "メールアドレスまたはパスワードが正しくありません。";
      case 400:
        return "入力内容を確認してください。";
      case 503:
        return "サーバーエラーが発生しました。しばらくしてから再試行してください。";
      default:
        return "サーバーエラーが発生しました。しばらくしてから再試行してください。";
    }
  }
  return "ネットワークエラーが発生しました。接続を確認してください。";
}

export default function LoginPage() {
  const router = useRouter();
  const { user, isLoading, login } = useAuthContext();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const validateField = (name: string, value: string) => {
    if (name === "email" && !value.trim()) return "メールアドレスは必須です";
    if (name === "email" && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return "メールアドレスの形式が正しくありません";
    if (name === "password" && !value.trim()) return "パスワードは必須です";
    return "";
  };

  const handleBlur = (name: string, value: string) => {
    const fieldError = validateField(name, value);
    setFieldErrors((prev) => ({ ...prev, [name]: fieldError }));
  };

  const hasFieldErrors = Object.values(fieldErrors).some((e) => e !== "");

  // Redirect if already authenticated
  useEffect(() => {
    if (!isLoading && user) {
      router.replace("/worklog");
    }
  }, [user, isLoading, router]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (!email || !password) {
      setError("入力内容を確認してください。");
      return;
    }

    setIsSubmitting(true);
    try {
      await login(email.toLowerCase(), password, remember);
      router.replace("/worklog");
    } catch (err: unknown) {
      setError(getErrorMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  }

  // Show loading spinner while checking auth state
  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <LoadingSpinner size="lg" label="読み込み中..." />
      </div>
    );
  }

  // Already authenticated, redirecting
  if (user) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <LoadingSpinner size="lg" label="リダイレクト中..." />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div className="max-w-md w-full bg-white rounded-lg shadow p-8">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold text-gray-900">Miometry</h1>
          <p className="text-sm text-gray-500 mt-1">Time Entry System</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1">
              メールアドレス
            </label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (fieldErrors.email) setFieldErrors((prev) => ({ ...prev, email: "" }));
              }}
              onBlur={() => handleBlur("email", email)}
              className={`w-full px-3 py-2 border ${fieldErrors.email ? "border-red-500" : "border-gray-300"} rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500`}
              placeholder="you@example.com"
              disabled={isSubmitting}
              aria-invalid={!!fieldErrors.email}
              aria-describedby={fieldErrors.email ? "email-error" : undefined}
            />
            {fieldErrors.email && (
              <p id="email-error" className="text-xs text-red-600 mt-1">
                {fieldErrors.email}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">
              パスワード
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                if (fieldErrors.password) setFieldErrors((prev) => ({ ...prev, password: "" }));
              }}
              onBlur={() => handleBlur("password", password)}
              className={`w-full px-3 py-2 border ${fieldErrors.password ? "border-red-500" : "border-gray-300"} rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500`}
              disabled={isSubmitting}
              aria-invalid={!!fieldErrors.password}
              aria-describedby={fieldErrors.password ? "password-error" : undefined}
            />
            {fieldErrors.password && (
              <p id="password-error" className="text-xs text-red-600 mt-1">
                {fieldErrors.password}
              </p>
            )}
          </div>

          <div className="flex items-center">
            <input
              id="remember"
              type="checkbox"
              checked={remember}
              onChange={(e) => setRemember(e.target.checked)}
              className="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
              disabled={isSubmitting}
            />
            <label htmlFor="remember" className="ml-2 block text-sm text-gray-700">
              ログイン状態を保持する
            </label>
          </div>

          {error && (
            <div role="alert" className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-md p-3">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={isSubmitting || hasFieldErrors}
            className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isSubmitting ? "ログイン中..." : "ログイン"}
          </button>

          <div className="text-center">
            <Link href="/password-reset/request" className="text-sm text-blue-600 hover:underline">
              パスワードをお忘れですか？
            </Link>
          </div>
        </form>
      </div>
    </div>
  );
}
