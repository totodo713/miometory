import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getMessages } from "next-intl/server";
import "./globals.css";
import { Header } from "./components/shared/Header";
import { ToastProvider } from "./components/shared/ToastProvider";
import { AuthProvider } from "./providers/AuthProvider";
import { SessionProvider } from "./providers/SessionProvider";
import { TenantProvider } from "./providers/TenantProvider";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Miometry - Time Entry System",
  description: "Miometry time entry and tracking system",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const locale = await getLocale();
  const messages = await getMessages();

  return (
    <html lang={locale}>
      <body className={`${geistSans.variable} ${geistMono.variable} antialiased`}>
        <NextIntlClientProvider messages={messages}>
          <AuthProvider>
            <TenantProvider>
              <SessionProvider>
                <ToastProvider>
                  <Header />
                  {children}
                </ToastProvider>
              </SessionProvider>
            </TenantProvider>
          </AuthProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
