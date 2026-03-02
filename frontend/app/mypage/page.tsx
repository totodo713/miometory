"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useId, useRef, useState } from "react";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";
import { useToast } from "@/hooks/useToast";
import { useAuthContext } from "@/providers/AuthProvider";
import { api } from "@/services/api";

interface ProfileData {
  id: string;
  email: string;
  displayName: string;
  organizationName: string | null;
  managerName: string | null;
  isActive: boolean;
}

interface AssignedProject {
  id: string;
  code: string;
  name: string;
}

export default function MypagePage() {
  const t = useTranslations("mypage");
  const tc = useTranslations("common");
  const { user, logout, updateUser } = useAuthContext();
  const toast = useToast();

  const [profile, setProfile] = useState<ProfileData | null>(null);
  const [projects, setProjects] = useState<AssignedProject[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Edit modal state
  const [isEditing, setIsEditing] = useState(false);
  const [editDisplayName, setEditDisplayName] = useState("");
  const [editEmail, setEditEmail] = useState("");
  const [editErrors, setEditErrors] = useState<{ displayName?: string; email?: string }>({});
  const [saving, setSaving] = useState(false);

  // Email changed dialog
  const [showEmailChanged, setShowEmailChanged] = useState(false);

  // ARIA IDs
  const uniqueId = useId();
  const editTitleId = `${uniqueId}-edit-title`;
  const emailChangedTitleId = `${uniqueId}-email-changed-title`;
  const emailChangedMessageId = `${uniqueId}-email-changed-message`;

  // Dialog ref for focus trap
  const dialogRef = useRef<HTMLDivElement>(null);

  const loadProfile = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const profileData = await api.profile.get();
      setProfile(profileData);

      if (user?.memberId) {
        const projectData = await api.members.getAssignedProjects(user.memberId);
        setProjects(projectData.projects);
      }
    } catch {
      setError(t("profile.loadError"));
    } finally {
      setLoading(false);
    }
  }, [user?.memberId, t]);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  // Escape key + focus trap for edit modal
  useEffect(() => {
    if (!isEditing) return;
    document.getElementById("edit-displayName")?.focus();

    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setIsEditing(false);
        return;
      }
      if (e.key === "Tab" && dialogRef.current) {
        const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
        );
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [isEditing]);

  const openEditModal = () => {
    if (!profile) return;
    setEditDisplayName(profile.displayName);
    setEditEmail(profile.email);
    setEditErrors({});
    setIsEditing(true);
  };

  const validateForm = (): boolean => {
    const errors: { displayName?: string; email?: string } = {};
    if (!editDisplayName.trim()) {
      errors.displayName = t("profile.displayNameRequired");
    } else if (editDisplayName.trim().length > 100) {
      errors.displayName = t("profile.displayNameRequired");
    }
    if (!editEmail.trim()) {
      errors.email = t("profile.emailRequired");
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(editEmail)) {
      errors.email = t("profile.emailInvalid");
    } else if (editEmail.trim().length > 254) {
      errors.email = t("profile.emailInvalid");
    }
    setEditErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSave = async () => {
    if (!validateForm()) return;
    try {
      setSaving(true);
      const result = await api.profile.update({
        email: editEmail.trim(),
        displayName: editDisplayName.trim(),
      });
      setIsEditing(false);

      if (result.emailChanged) {
        setShowEmailChanged(true);
      } else {
        updateUser({ displayName: editDisplayName.trim(), email: editEmail.trim() });
        toast.success(t("profile.updated"));
        loadProfile();
      }
    } catch {
      toast.error(t("profile.updateError"));
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] gap-4">
        <p className="text-red-500" role="alert">
          {error}
        </p>
        <button
          type="button"
          onClick={loadProfile}
          className="px-4 py-2 text-sm text-blue-600 hover:text-blue-800 border border-blue-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          {tc("retry")}
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto p-6 space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">{t("title")}</h1>

      {/* Profile Section */}
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-gray-900">{t("profile.title")}</h2>
          <button
            type="button"
            onClick={openEditModal}
            className="text-sm text-blue-600 hover:text-blue-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 rounded"
          >
            {t("profile.edit")}
          </button>
        </div>
        <dl className="space-y-3">
          <div className="flex">
            <dt className="w-40 text-sm text-gray-500">{t("profile.displayName")}</dt>
            <dd className="text-sm text-gray-900">{profile?.displayName}</dd>
          </div>
          <div className="flex">
            <dt className="w-40 text-sm text-gray-500">{t("profile.email")}</dt>
            <dd className="text-sm text-gray-900">{profile?.email}</dd>
          </div>
          <div className="flex">
            <dt className="w-40 text-sm text-gray-500">{t("profile.organization")}</dt>
            <dd className="text-sm text-gray-900">{profile?.organizationName ?? t("profile.noOrganization")}</dd>
          </div>
          <div className="flex">
            <dt className="w-40 text-sm text-gray-500">{t("profile.manager")}</dt>
            <dd className="text-sm text-gray-900">{profile?.managerName ?? t("profile.noManager")}</dd>
          </div>
        </dl>
      </div>

      {/* Assigned Projects Section */}
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">{t("projects.title")}</h2>
        {projects.length === 0 ? (
          <p className="text-sm text-gray-500">{t("projects.noProjects")}</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="text-left py-2 text-gray-500 font-medium">{t("projects.code")}</th>
                <th className="text-left py-2 text-gray-500 font-medium">{t("projects.name")}</th>
              </tr>
            </thead>
            <tbody>
              {projects.map((project) => (
                <tr key={project.id} className="border-b border-gray-100">
                  <td className="py-2 text-gray-900">{project.code}</td>
                  <td className="py-2 text-gray-900">{project.name}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Edit Modal */}
      {isEditing && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          {/* biome-ignore lint/a11y/useKeyWithClickEvents: backdrop click to close */}
          {/* biome-ignore lint/a11y/noStaticElementInteractions: backdrop click to close */}
          <div className="absolute inset-0 bg-black/50" onClick={() => setIsEditing(false)} />
          {/* biome-ignore lint/a11y/useKeyWithClickEvents: stopPropagation for dialog content */}
          <div
            ref={dialogRef}
            className="relative bg-white rounded-lg shadow-xl p-6 w-full max-w-md mx-4"
            role="dialog"
            aria-modal="true"
            aria-labelledby={editTitleId}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 id={editTitleId} className="text-lg font-semibold text-gray-900 mb-4">
              {t("profile.editTitle")}
            </h3>
            <div className="space-y-4">
              <div>
                <label htmlFor="edit-displayName" className="block text-sm font-medium text-gray-700 mb-1">
                  {t("profile.displayName")}
                </label>
                <input
                  id="edit-displayName"
                  type="text"
                  value={editDisplayName}
                  onChange={(e) => setEditDisplayName(e.target.value)}
                  maxLength={100}
                  aria-invalid={!!editErrors.displayName}
                  aria-describedby={editErrors.displayName ? "edit-displayName-error" : undefined}
                  className={`w-full px-3 py-2 border rounded-md text-sm ${
                    editErrors.displayName ? "border-red-500" : "border-gray-300"
                  } focus:outline-none focus:ring-2 focus:ring-blue-500`}
                />
                {editErrors.displayName && (
                  <p id="edit-displayName-error" className="text-xs text-red-500 mt-1" role="alert">
                    {editErrors.displayName}
                  </p>
                )}
              </div>
              <div>
                <label htmlFor="edit-email" className="block text-sm font-medium text-gray-700 mb-1">
                  {t("profile.email")}
                </label>
                <input
                  id="edit-email"
                  type="email"
                  value={editEmail}
                  onChange={(e) => setEditEmail(e.target.value)}
                  maxLength={254}
                  aria-invalid={!!editErrors.email}
                  aria-describedby={editErrors.email ? "edit-email-error" : undefined}
                  className={`w-full px-3 py-2 border rounded-md text-sm ${
                    editErrors.email ? "border-red-500" : "border-gray-300"
                  } focus:outline-none focus:ring-2 focus:ring-blue-500`}
                />
                {editErrors.email && (
                  <p id="edit-email-error" className="text-xs text-red-500 mt-1" role="alert">
                    {editErrors.email}
                  </p>
                )}
              </div>
            </div>
            <div className="flex justify-end gap-3 mt-6">
              <button
                type="button"
                onClick={() => setIsEditing(false)}
                className="px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-md"
              >
                {t("profile.cancel")}
              </button>
              <button
                type="button"
                onClick={handleSave}
                disabled={saving}
                className="px-4 py-2 text-sm text-white bg-blue-600 hover:bg-blue-700 rounded-md disabled:opacity-50"
              >
                {saving ? tc("saving") : t("profile.save")}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Email Changed Dialog */}
      {showEmailChanged && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/50" />
          <div
            className="relative bg-white rounded-lg shadow-xl p-6 w-full max-w-md mx-4"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby={emailChangedTitleId}
            aria-describedby={emailChangedMessageId}
          >
            <h3 id={emailChangedTitleId} className="text-lg font-semibold text-gray-900 mb-2">
              {t("profile.emailChangedTitle")}
            </h3>
            <p id={emailChangedMessageId} className="text-sm text-gray-600 mb-4">
              {t("profile.emailChangedMessage")}
            </p>
            <div className="flex justify-end">
              <button
                type="button"
                onClick={() => logout()}
                className="px-4 py-2 text-sm text-white bg-blue-600 hover:bg-blue-700 rounded-md"
              >
                {t("profile.emailChangedConfirm")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
