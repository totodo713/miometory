"use client";

import { useCallback, useState } from "react";
import { Breadcrumbs } from "@/components/shared/Breadcrumbs";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { MemberForm } from "@/components/admin/MemberForm";
import type { MemberRow } from "@/components/admin/MemberList";
import { MemberList } from "@/components/admin/MemberList";
import { useToast } from "@/hooks/useToast";
import { ApiError, api } from "@/services/api";

export default function AdminMembersPage() {
	const toast = useToast();
	const [editingMember, setEditingMember] = useState<MemberRow | null>(null);
	const [showForm, setShowForm] = useState(false);
	const [refreshKey, setRefreshKey] = useState(0);
	const [confirmTarget, setConfirmTarget] = useState<{ id: string; action: "deactivate" | "activate" } | null>(null);

	const refresh = useCallback(() => {
		setRefreshKey((k) => k + 1);
	}, []);

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
					await api.admin.members.deactivate(target.id);
					toast.success("メンバーを無効化しました");
				} else {
					await api.admin.members.activate(target.id);
					toast.success("メンバーを有効化しました");
				}
				refresh();
			} catch (err: unknown) {
				toast.error(err instanceof ApiError ? err.message : "エラーが発生しました");
			}
		},
		[refresh, toast],
	);

	const handleEdit = useCallback((member: MemberRow) => {
		setEditingMember(member);
		setShowForm(true);
	}, []);

	const handleSaved = useCallback(() => {
		setShowForm(false);
		setEditingMember(null);
		refresh();
	}, [refresh]);

	const handleClose = useCallback(() => {
		setShowForm(false);
		setEditingMember(null);
	}, []);

	return (
		<div>
			<Breadcrumbs items={[{ label: "管理", href: "/admin" }, { label: "メンバー管理" }]} />

			<div className="flex items-center justify-between mb-6 mt-4">
				<h1 className="text-2xl font-bold text-gray-900">メンバー管理</h1>
				<button
					type="button"
					onClick={() => {
						setEditingMember(null);
						setShowForm(true);
					}}
					className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700"
				>
					メンバー招待
				</button>
			</div>

			<div className="bg-white rounded-lg border border-gray-200 p-4">
				<MemberList
					onEdit={handleEdit}
					onDeactivate={handleDeactivate}
					onActivate={handleActivate}
					refreshKey={refreshKey}
				/>
			</div>

			{showForm && <MemberForm member={editingMember} onClose={handleClose} onSaved={handleSaved} />}

			<ConfirmDialog
				open={confirmTarget !== null}
				title="確認"
				message={`このメンバーを${confirmTarget?.action === "deactivate" ? "無効化" : "有効化"}しますか？`}
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
