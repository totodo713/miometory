"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAdminContext } from "@/providers/AdminProvider";

interface NavItem {
  href: string;
  label: string;
  permission?: string;
}

const NAV_ITEMS: NavItem[] = [
  { href: "/admin", label: "ダッシュボード" },
  { href: "/admin/tenants", label: "テナント管理", permission: "tenant.view" },
  { href: "/admin/users", label: "ユーザー管理", permission: "user.view" },
  { href: "/admin/members", label: "メンバー管理", permission: "member.view" },
  { href: "/admin/projects", label: "プロジェクト管理", permission: "project.view" },
  { href: "/admin/assignments", label: "アサイン管理", permission: "assignment.view" },
  { href: "/admin/organizations", label: "組織管理", permission: "organization.view" },
];

export function AdminNav() {
  const { hasPermission, adminContext } = useAdminContext();
  const pathname = usePathname();

  const visibleItems = NAV_ITEMS.filter((item) => !item.permission || hasPermission(item.permission));

  return (
    <nav className="w-60 bg-white border-r border-gray-200 min-h-full">
      <div className="p-4 border-b border-gray-200">
        <p className="text-xs font-medium text-gray-500 uppercase tracking-wider">管理メニュー</p>
        {adminContext && <p className="mt-1 text-sm text-gray-700 truncate">{adminContext.role.replace(/_/g, " ")}</p>}
      </div>
      <ul className="py-2">
        {visibleItems.map((item) => {
          const isActive = item.href === "/admin" ? pathname === "/admin" : pathname.startsWith(item.href);
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                className={`block px-4 py-2.5 text-sm transition-colors ${
                  isActive
                    ? "bg-blue-50 text-blue-700 font-medium border-r-2 border-blue-700"
                    : "text-gray-700 hover:bg-gray-50"
                }`}
              >
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
