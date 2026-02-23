"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { AdminNav } from "@/components/admin/AdminNav";
import { AuthGuard } from "@/components/shared/AuthGuard";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { AdminProvider, useAdminContext } from "@/providers/AdminProvider";

function AdminLayoutInner({ children }: { children: React.ReactNode }) {
	const { adminContext, isLoading } = useAdminContext();
	const router = useRouter();
	const isMobile = useMediaQuery("(max-width: 767px)");

	useEffect(() => {
		if (!isLoading && !adminContext) {
			router.replace("/worklog");
		}
	}, [adminContext, isLoading, router]);

	if (isLoading) {
		return (
			<div className="min-h-screen flex items-center justify-center">
				<LoadingSpinner size="lg" label="読み込み中..." />
			</div>
		);
	}

	if (!adminContext) {
		return null;
	}

	return (
		<div className="flex min-h-[calc(100vh-3.5rem)]">
			<AdminNav />
			<main className={`flex-1 bg-gray-50 ${isMobile ? "p-4 pt-16" : "p-6"}`}>{children}</main>
		</div>
	);
}

export default function AdminLayout({ children }: { children: React.ReactNode }) {
	return (
		<AuthGuard>
			<AdminProvider>
				<AdminLayoutInner>{children}</AdminLayoutInner>
			</AdminProvider>
		</AuthGuard>
	);
}
