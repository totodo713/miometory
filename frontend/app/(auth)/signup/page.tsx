"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import type React from "react";
import { useState } from "react";
import { AuthLocaleToggle } from "@/components/auth/AuthLocaleToggle";
import { useToast } from "@/hooks/useToast";
import { ApiError, api } from "@/services/api";

export default function SignupPage() {
  const router = useRouter();
  const toast = useToast();
  const t = useTranslations("auth.signup");
  const ta = useTranslations("auth");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [passwordMismatch, setPasswordMismatch] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function handleConfirmBlur() {
    if (passwordConfirm && password !== passwordConfirm) {
      setPasswordMismatch(true);
    } else {
      setPasswordMismatch(false);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    const trimmedName = name.trim();
    if (!trimmedName) {
      toast.error(t("errors.nameRequired"));
      return;
    }

    if (password !== passwordConfirm) {
      setPasswordMismatch(true);
      return;
    }

    setIsSubmitting(true);
    try {
      await api.auth.signup(email, trimmedName, password);
      toast.success(t("confirm.title"));
      router.push("/signup/confirm");
    } catch (err: unknown) {
      if (err instanceof ApiError && err.status === 409) {
        toast.error(t("errors.emailExists"));
      } else if (err instanceof ApiError) {
        toast.error(err.message);
      } else {
        toast.error(t("errors.networkError"));
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div className="max-w-md w-full bg-white rounded-lg shadow p-8">
        <div className="flex justify-end mb-4">
          <AuthLocaleToggle />
        </div>
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold text-gray-900">{t("title")}</h1>
          <p className="text-sm text-gray-500 mt-1">Miometry {ta("brandTagline")}</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label htmlFor="name" className="block text-sm font-medium text-gray-700 mb-1">
              {t("nameLabel")}
            </label>
            <input
              id="name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder={t("namePlaceholder")}
              disabled={isSubmitting}
              required
            />
          </div>

          <div>
            <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1">
              {t("emailLabel")}
            </label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder={t("emailPlaceholder")}
              disabled={isSubmitting}
              required
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">
              {t("passwordLabel")}
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              disabled={isSubmitting}
              required
            />
          </div>

          <div>
            <label htmlFor="passwordConfirm" className="block text-sm font-medium text-gray-700 mb-1">
              {t("confirmPasswordLabel")}
            </label>
            <input
              id="passwordConfirm"
              type="password"
              value={passwordConfirm}
              onChange={(e) => setPasswordConfirm(e.target.value)}
              onBlur={handleConfirmBlur}
              className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              disabled={isSubmitting}
              required
            />
            {passwordMismatch && <p className="mt-1 text-sm text-red-600">{t("errors.passwordMismatch")}</p>}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isSubmitting ? t("submitting") : t("submitButton")}
          </button>

          <div className="text-center">
            <Link href="/login" className="text-sm text-blue-600 hover:underline">
              {t("loginLink")}
            </Link>
          </div>
        </form>
      </div>
    </div>
  );
}
