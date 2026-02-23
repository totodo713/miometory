"use client";

import { useCallback, useState } from "react";
import { Breadcrumbs } from "@/components/shared/Breadcrumbs";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { TenantForm } from "@/components/admin/TenantForm";
import type { TenantRow } from "@/components/admin/TenantList";
import { TenantList } from "@/components/admin/TenantList";
import { useToast } from "@/hooks/useToast";
import { ApiError, api } from "@/services/api";

export default function AdminTenantsPage() {
	const toast = useToast();
	const [editingTenant, setEditingTenant] = useState<TenantRow | null>(null);
	const [showForm, setShowForm] = useState(false);
	const [refreshKey, setRefreshKey] = useState(0);
	const [confirmTarget, setConfirmTarget] = useState<{ id: string; action: "deactivate" | "activate" } | null>(null);

	const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);

	const handleDeactivate = useCallback((id: string) => {
		setConfirmTarget({ id, action: "deactivate" });
	}, []);

	const handleActivate = useCallback((id: string) => {
		setConfirmTarget({ id, action: "activate" });
	}, []);

	const executeAction = useCallback(
		async (target: { id: string; action: "deactivate" | "activate" }) => {
			try {
				if (target.action === "deactivate") {
					await api.admin.tenants.deactivate(target.id);
					toast.success("テナントを無効化しました");
				} else {
					await api.admin.tenants.activate(target.id);
					toast.success("テナントを有効化しました");
				}
				refresh();
			} catch (err: unknown) {
				toast.error(err instanceof ApiError ? err.message : "エラーが発生しました");
			}
		},
		[refresh, toast],
	);

	const handleEdit = useCallback((tenant: TenantRow) => {
		setEditingTenant(tenant);
		setShowForm(true);
	}, []);

	const handleSaved = useCallback(() => {
		setShowForm(false);
		setEditingTenant(null);
		refresh();
	}, [refresh]);

	const handleClose = useCallback(() => {
		setShowForm(false);
		setEditingTenant(null);
	}, []);

	return (
		<div>
			<Breadcrumbs items={[{ label: "管理", href: "/admin" }, { label: "テナント管理" }]} />

			<div className="flex items-center justify-between mb-6 mt-4">
				<h1 className="text-2xl font-bold text-gray-900">テナント管理</h1>
				<button
					type="button"
					onClick={() => {
						setEditingTenant(null);
						setShowForm(true);
					}}
					className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700"
				>
					テナント作成
				</button>
			</div>

			<div className="bg-white rounded-lg border border-gray-200 p-4">
				<TenantList
					onEdit={handleEdit}
					onDeactivate={handleDeactivate}
					onActivate={handleActivate}
					refreshKey={refreshKey}
				/>
			</div>

			{showForm && <TenantForm tenant={editingTenant} onClose={handleClose} onSaved={handleSaved} />}

			<ConfirmDialog
				open={confirmTarget !== null}
				title="確認"
				message={`このテナントを${confirmTarget?.action === "deactivate" ? "無効化" : "有効化"}しますか？`}
				confirmLabel={confirmTarget?.action === "deactivate" ? "無効化" : "有効化"}
				variant={confirmTarget?.action === "deactivate" ? "danger" : "warning"}
				onConfirm={() => {
					if (confirmTarget) executeAction(confirmTarget);
					setConfirmTarget(null);
				}}
				onCancel={() => setConfirmTarget(null)}
			/>
		</div>
	);
}
