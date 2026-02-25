export const locales = ["en", "ja"] as const;
export type Locale = (typeof locales)[number];
export const defaultLocale: Locale = "ja";
export const COOKIE_NAME = "NEXT_LOCALE";
