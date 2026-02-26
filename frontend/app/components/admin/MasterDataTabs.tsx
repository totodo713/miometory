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

  const handleKeyDown = (e: React.KeyboardEvent, currentIndex: number) => {
    let newIndex = currentIndex;
    if (e.key === "ArrowRight") {
      newIndex = (currentIndex + 1) % TABS.length;
    } else if (e.key === "ArrowLeft") {
      newIndex = (currentIndex - 1 + TABS.length) % TABS.length;
    } else if (e.key === "Home") {
      newIndex = 0;
    } else if (e.key === "End") {
      newIndex = TABS.length - 1;
    } else {
      return;
    }
    e.preventDefault();
    onTabChange(TABS[newIndex].key);
  };

  return (
    <div role="tablist" aria-label={t("title")} className="flex border-b border-gray-200 mb-6">
      {TABS.map((tab, index) => (
        <button
          key={tab.key}
          type="button"
          role="tab"
          id={`tab-${tab.key}`}
          aria-selected={activeTab === tab.key}
          aria-controls={`tabpanel-${tab.key}`}
          tabIndex={activeTab === tab.key ? 0 : -1}
          onClick={() => onTabChange(tab.key)}
          onKeyDown={(e) => handleKeyDown(e, index)}
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
