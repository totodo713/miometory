"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { useAdminContext } from "@/providers/AdminProvider";

export default function AdminDashboard() {
  const t = useTranslations("admin.dashboard");
  const tn = useTranslations("admin.nav");
  const { adminContext, hasPermission } = useAdminContext();

  if (!adminContext) return null;

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">{t("title")}</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {hasPermission("member.view") && (
          <DashboardCard title={tn("members")} description={t("cards.membersDescription")} href="/admin/members" />
        )}
        {hasPermission("project.view") && (
          <DashboardCard title={tn("projects")} description={t("cards.projectsDescription")} href="/admin/projects" />
        )}
        {hasPermission("assignment.view") && (
          <DashboardCard
            title={tn("assignments")}
            description={t("cards.assignmentsDescription")}
            href="/admin/assignments"
          />
        )}
        {hasPermission("daily_approval.view") && (
          <DashboardCard
            title={tn("dailyApproval")}
            description={t("cards.dailyApprovalDescription")}
            href="/worklog/daily-approval"
          />
        )}
        {hasPermission("organization.view") && (
          <DashboardCard
            title={tn("organizations")}
            description={t("cards.organizationsDescription")}
            href="/admin/organizations"
          />
        )}
        {hasPermission("tenant.view") && (
          <DashboardCard title={tn("tenants")} description={t("cards.tenantsDescription")} href="/admin/tenants" />
        )}
        {hasPermission("user.view") && (
          <DashboardCard title={tn("users")} description={t("cards.usersDescription")} href="/admin/users" />
        )}
      </div>
    </div>
  );
}

function DashboardCard({ title, description, href }: { title: string; description: string; href: string }) {
  return (
    <Link
      href={href}
      className="block p-6 bg-white rounded-lg border border-gray-200 hover:border-blue-300 hover:shadow-sm transition-all"
    >
      <h2 className="text-lg font-semibold text-gray-900 mb-2">{title}</h2>
      <p className="text-sm text-gray-600">{description}</p>
    </Link>
  );
}
