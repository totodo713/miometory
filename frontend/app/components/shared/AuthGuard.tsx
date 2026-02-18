"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";
import { useAuthContext } from "@/providers/AuthProvider";

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const { user, isLoading } = useAuthContext();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !user) {
      router.replace("/login");
    }
  }, [user, isLoading, router]);

  if (isLoading || !user) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <LoadingSpinner size="lg" label="読み込み中..." />
      </div>
    );
  }

  return <>{children}</>;
}
