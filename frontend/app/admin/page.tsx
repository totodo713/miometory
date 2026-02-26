"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { useAdminContext } from "@/providers/AdminProvider";

interface CardDef {
  permission: string;
  titleKey: string;
  descriptionKey: string;
  href: string;
}

const CARDS: CardDef[] = [
  {
    permission: "member.view",
    titleKey: "members",
    descriptionKey: "cards.membersDescription",
    href: "/admin/members",
  },
  {
    permission: "project.view",
    titleKey: "projects",
    descriptionKey: "cards.projectsDescription",
    href: "/admin/projects",
  },
  {
    permission: "assignment.view",
    titleKey: "assignments",
    descriptionKey: "cards.assignmentsDescription",
    href: "/admin/assignments",
  },
  {
    permission: "daily_approval.view",
    titleKey: "dailyApproval",
    descriptionKey: "cards.dailyApprovalDescription",
    href: "/worklog/daily-approval",
  },
  {
    permission: "organization.view",
    titleKey: "organizations",
    descriptionKey: "cards.organizationsDescription",
    href: "/admin/organizations",
  },
  {
    permission: "tenant.view",
    titleKey: "tenants",
    descriptionKey: "cards.tenantsDescription",
    href: "/admin/tenants",
  },
  {
    permission: "master_data.view",
    titleKey: "masterData",
    descriptionKey: "cards.masterDataDescription",
    href: "/admin/master-data",
  },
  { permission: "user.view", titleKey: "users", descriptionKey: "cards.usersDescription", href: "/admin/users" },
];

export default function AdminDashboard() {
  const t = useTranslations("admin.dashboard");
  const tn = useTranslations("admin.nav");
  const { adminContext, hasPermission } = useAdminContext();

  if (!adminContext) return null;

  const visibleCards = CARDS.filter((c) => hasPermission(c.permission));
  const gridClass =
    visibleCards.length <= 2
      ? "grid grid-cols-1 md:grid-cols-2 gap-4"
      : "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4";

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">{t("title")}</h1>
      <div className={gridClass}>
        {visibleCards.map((card) => (
          <DashboardCard
            key={card.href}
            title={tn(card.titleKey)}
            description={t(card.descriptionKey)}
            href={card.href}
          />
        ))}
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
