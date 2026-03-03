import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import messagesEn from "../../messages/en.json";
import messagesJa from "../../messages/ja.json";

interface IntlWrapperProps {
  children: ReactNode;
  locale?: string;
}

export function IntlWrapper({ children, locale = "ja" }: IntlWrapperProps) {
  const messages = locale === "en" ? messagesEn : messagesJa;

  return (
    <NextIntlClientProvider locale={locale} messages={messages} timeZone="Asia/Tokyo">
      {children}
    </NextIntlClientProvider>
  );
}
