"use client";

import { useRouter } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { useCallback } from "react";
import { useAuthContext } from "@/providers/AuthProvider";
import { api } from "@/services/api";
import { COOKIE_NAME, type Locale, locales } from "../../../i18n/config";

export function LocaleToggle() {
  const locale = useLocale();
  const router = useRouter();
  const t = useTranslations("locale");
  const { user } = useAuthContext();

  const switchLocale = useCallback(
    (newLocale: Locale) => {
      if (newLocale === locale) return;

      // biome-ignore lint/suspicious/noDocumentCookie: direct cookie access needed for locale preference
      document.cookie = `${COOKIE_NAME}=${newLocale};path=/;max-age=31536000;SameSite=Lax`;

      // Fire-and-forget DB update for authenticated users
      if (user) {
        api.user.updateLocale(newLocale).catch(() => {});
      }

      router.refresh();
    },
    [locale, user, router],
  );

  return (
    <div className="flex items-center rounded-md border border-gray-300 overflow-hidden">
      {locales.map((l) => (
        <button
          key={l}
          type="button"
          aria-pressed={locale === l}
          onClick={() => switchLocale(l)}
          className={`px-2 py-1 text-xs font-medium transition-colors ${
            locale === l ? "bg-gray-800 text-white" : "bg-white text-gray-600 hover:bg-gray-100"
          }`}
        >
          {t(l)}
        </button>
      ))}
    </div>
  );
}
