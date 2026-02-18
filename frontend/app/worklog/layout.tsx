"use client";

import { AuthGuard } from "@/components/shared/AuthGuard";

export default function WorklogLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard>{children}</AuthGuard>;
}
