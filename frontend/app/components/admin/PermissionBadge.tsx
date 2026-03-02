"use client";

import { useTranslations } from "next-intl";
import { useAdminContext } from "@/providers/AdminProvider";

interface PermissionBadgeProps {
  editPermission: string | string[];
}

export function PermissionBadge({ editPermission }: PermissionBadgeProps) {
  const t = useTranslations("admin.permission");
  const { hasPermission } = useAdminContext();
  const canEdit = Array.isArray(editPermission)
    ? editPermission.some((p) => hasPermission(p))
    : hasPermission(editPermission);

  return (
    <span
      className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium flex-shrink-0 ${
        canEdit ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
      }`}
    >
      {canEdit ? t("edit") : t("view")}
    </span>
  );
}
