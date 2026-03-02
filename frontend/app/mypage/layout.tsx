"use client";

import { AuthGuard } from "@/components/shared/AuthGuard";

export default function MypageLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard>{children}</AuthGuard>;
}
