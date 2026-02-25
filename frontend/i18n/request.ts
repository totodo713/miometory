import { cookies, headers } from "next/headers";
import { getRequestConfig } from "next-intl/server";
import type { Locale } from "./config";
import { COOKIE_NAME, defaultLocale, locales } from "./config";

export default getRequestConfig(async () => {
  let locale: Locale = defaultLocale;

  // Priority 1: Cookie
  const cookieStore = await cookies();
  const cookieLocale = cookieStore.get(COOKIE_NAME)?.value;
  if (cookieLocale && locales.includes(cookieLocale as Locale)) {
    locale = cookieLocale as Locale;
  } else {
    // Priority 2: Accept-Language header
    const headerStore = await headers();
    const acceptLang = headerStore.get("accept-language");
    if (acceptLang) {
      const preferred = acceptLang.split(",").map((l) => l.split(";")[0].trim().toLowerCase());
      for (const lang of preferred) {
        if (lang.startsWith("en")) {
          locale = "en";
          break;
        }
        if (lang.startsWith("ja")) {
          locale = "ja";
          break;
        }
      }
    }
  }

  return {
    locale,
    messages: (await import(`../messages/${locale}.json`)).default,
    timeZone: "Asia/Tokyo",
  };
});
