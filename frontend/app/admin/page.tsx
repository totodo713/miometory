"use client";

import { useAdminContext } from "@/providers/AdminProvider";

export default function AdminDashboard() {
  const { adminContext, hasPermission } = useAdminContext();

  if (!adminContext) return null;

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">管理ダッシュボード</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {hasPermission("member.view") && (
          <DashboardCard
            title="メンバー管理"
            description="メンバーの招待・編集・無効化を行います"
            href="/admin/members"
          />
        )}
        {hasPermission("project.view") && (
          <DashboardCard
            title="プロジェクト管理"
            description="プロジェクトの作成・編集・無効化を行います"
            href="/admin/projects"
          />
        )}
        {hasPermission("assignment.view") && (
          <DashboardCard
            title="アサイン管理"
            description="メンバーとプロジェクトの割り当てを管理します"
            href="/admin/assignments"
          />
        )}
        {hasPermission("daily_approval.view") && (
          <DashboardCard
            title="日次承認"
            description="チームメンバーの日次記録を承認・却下します"
            href="/worklog/daily-approval"
          />
        )}
        {hasPermission("tenant.view") && (
          <DashboardCard
            title="テナント管理"
            description="テナントの作成・編集・無効化を行います"
            href="/admin/tenants"
          />
        )}
        {hasPermission("user.view") && (
          <DashboardCard
            title="ユーザー管理"
            description="ユーザーアカウントのロール変更・ロック管理を行います"
            href="/admin/users"
          />
        )}
      </div>
    </div>
  );
}

function DashboardCard({ title, description, href }: { title: string; description: string; href: string }) {
  return (
    <a
      href={href}
      className="block p-6 bg-white rounded-lg border border-gray-200 hover:border-blue-300 hover:shadow-sm transition-all"
    >
      <h2 className="text-lg font-semibold text-gray-900 mb-2">{title}</h2>
      <p className="text-sm text-gray-600">{description}</p>
    </a>
  );
}
