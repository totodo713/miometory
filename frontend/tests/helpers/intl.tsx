import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import messages from "../../messages/ja.json";

interface IntlWrapperProps {
  children: ReactNode;
  locale?: string;
}

export function IntlWrapper({ children, locale = "ja" }: IntlWrapperProps) {
  return (
    <NextIntlClientProvider locale={locale} messages={messages}>
      {children}
    </NextIntlClientProvider>
  );
}
