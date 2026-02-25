"use client";

import { useRouter } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { useCallback } from "react";
import { COOKIE_NAME, type Locale, locales } from "../../../i18n/config";

export function AuthLocaleToggle() {
  const locale = useLocale();
  const router = useRouter();
  const t = useTranslations("locale");

  const switchLocale = useCallback(
    (newLocale: Locale) => {
      if (newLocale === locale) return;
      // biome-ignore lint/suspicious/noDocumentCookie: direct cookie access needed for locale preference
      document.cookie = `${COOKIE_NAME}=${newLocale};path=/;max-age=31536000;SameSite=Lax`;
      router.refresh();
    },
    [locale, router],
  );

  return (
    <div className="flex items-center rounded-md border border-gray-200 overflow-hidden">
      {locales.map((l) => (
        <button
          key={l}
          type="button"
          aria-pressed={locale === l}
          onClick={() => switchLocale(l)}
          className={`px-2 py-0.5 text-xs font-medium transition-colors ${
            locale === l ? "bg-gray-700 text-white" : "bg-white text-gray-500 hover:bg-gray-50"
          }`}
        >
          {t(l)}
        </button>
      ))}
    </div>
  );
}
