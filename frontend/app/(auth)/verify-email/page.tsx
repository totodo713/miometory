"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";
import { ApiError, api } from "@/services/api";

type VerifyStatus = "loading" | "success" | "error" | "network_error";

function VerifyEmailContent() {
  const params = useSearchParams();
  const token = params?.get("token") || "";
  const [status, setStatus] = useState<VerifyStatus>("loading");

  const verify = () => {
    if (!token) {
      setStatus("error");
      return;
    }

    setStatus("loading");
    let cancelled = false;
    api.auth
      .verifyEmail(token)
      .then(() => {
        if (!cancelled) setStatus("success");
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        if (err instanceof ApiError) {
          setStatus("error");
        } else {
          setStatus("network_error");
        }
      });

    return () => {
      cancelled = true;
    };
  };

  // biome-ignore lint/correctness/useExhaustiveDependencies: verify depends on token
  useEffect(() => {
    return verify();
  }, [token]);

  if (status === "loading") {
    return (
      <div className="text-center">
        <LoadingSpinner size="lg" label="メールアドレスを確認中..." />
      </div>
    );
  }

  if (status === "success") {
    return (
      <div className="text-center space-y-4">
        <div className="flex justify-center">
          <svg
            className="h-16 w-16 text-green-500"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <h1 className="text-2xl font-bold text-gray-900">認証完了</h1>
        <p className="text-gray-600">メールアドレスの認証が完了しました。</p>
        <Link
          href="/login"
          className="inline-block mt-4 px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
        >
          ログインページへ
        </Link>
      </div>
    );
  }

  if (status === "network_error") {
    return (
      <div className="text-center space-y-4">
        <div className="flex justify-center">
          <svg
            className="h-16 w-16 text-yellow-500"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
            />
          </svg>
        </div>
        <h1 className="text-2xl font-bold text-gray-900">接続エラー</h1>
        <p className="text-gray-600" role="alert">
          サーバーに接続できませんでした。ネットワーク接続を確認してください。
        </p>
        <button
          type="button"
          onClick={verify}
          className="inline-block mt-4 px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
        >
          再試行
        </button>
      </div>
    );
  }

  return (
    <div className="text-center space-y-4">
      <div className="flex justify-center">
        <svg
          className="h-16 w-16 text-red-500"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          aria-hidden="true"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </div>
      <h1 className="text-2xl font-bold text-gray-900">認証エラー</h1>
      <p className="text-gray-600" role="alert">
        トークンが無効です。リンクの有効期限が切れている可能性があります。
      </p>
      <Link
        href="/login"
        className="inline-block mt-4 px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
      >
        ログインページへ
      </Link>
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div className="max-w-md w-full bg-white rounded-lg shadow p-8">
        <Suspense
          fallback={
            <div className="text-center">
              <LoadingSpinner size="lg" label="読み込み中..." />
            </div>
          }
        >
          <VerifyEmailContent />
        </Suspense>
      </div>
    </div>
  );
}
