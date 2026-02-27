"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useTranslations } from "next-intl";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";
import { useAuthContext } from "@/providers/AuthProvider";

export default function Home() {
  const { user, isLoading } = useAuthContext();
  const router = useRouter();
  const t = useTranslations("common");

  useEffect(() => {
    if (!isLoading) {
      if (user) {
        router.replace("/worklog");
      } else {
        router.replace("/login");
      }
    }
  }, [user, isLoading, router]);

  return (
    <div className="min-h-screen flex items-center justify-center">
      <LoadingSpinner size="lg" label={t("loading")} />
    </div>
  );
}
