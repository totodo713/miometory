"use client";

import { useEffect, useState } from "react";
import { api } from "@/services/api";

interface DateInfo {
  fiscalYear: string;
  fiscalPeriod: string;
  monthlyPeriodStart: string;
  monthlyPeriodEnd: string;
}

export function useDateInfo(tenantId: string | undefined, orgId: string | undefined, year: number, month: number) {
  const [data, setData] = useState<DateInfo | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!tenantId || !orgId) return;
    setIsLoading(true);
    setError(null);
    const date = `${year}-${String(month).padStart(2, "0")}-01`;
    api.admin.organizations
      .getDateInfo(tenantId, orgId, date)
      .then(setData)
      .catch(() => {
        setData(null);
        setError("日付情報の取得に失敗しました");
      })
      .finally(() => setIsLoading(false));
  }, [tenantId, orgId, year, month]);

  return { data, isLoading, error };
}
