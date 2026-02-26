"use client";

import { useTranslations } from "next-intl";

export type TabType = "fiscal-year" | "monthly-period" | "holiday-calendar";

interface MasterDataTabsProps {
  activeTab: TabType;
  onTabChange: (tab: TabType) => void;
}

const TABS: { key: TabType; labelKey: string }[] = [
  { key: "fiscal-year", labelKey: "tabs.fiscalYearPatterns" },
  { key: "monthly-period", labelKey: "tabs.monthlyPeriodPatterns" },
  { key: "holiday-calendar", labelKey: "tabs.holidayCalendars" },
];

export function MasterDataTabs({ activeTab, onTabChange }: MasterDataTabsProps) {
  const t = useTranslations("admin.masterData");

  return (
    <div className="flex border-b border-gray-200 mb-6">
      {TABS.map((tab) => (
        <button
          key={tab.key}
          type="button"
          onClick={() => onTabChange(tab.key)}
          className={`px-4 py-2.5 text-sm font-medium transition-colors border-b-2 -mb-px ${
            activeTab === tab.key
              ? "border-blue-600 text-blue-600"
              : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
          }`}
        >
          {t(tab.labelKey)}
        </button>
      ))}
    </div>
  );
}
