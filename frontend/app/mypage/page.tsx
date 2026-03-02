"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
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
    }
    if (!editEmail.trim()) {
      errors.email = t("profile.emailRequired");
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(editEmail)) {
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
        <p className="text-gray-500">{t("profile.title")}...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <p className="text-red-500">{error}</p>
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
          <button type="button" onClick={openEditModal} className="text-sm text-blue-600 hover:text-blue-800">
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
          <div className="relative bg-white rounded-lg shadow-xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">{t("profile.editTitle")}</h3>
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
                  className={`w-full px-3 py-2 border rounded-md text-sm ${
                    editErrors.displayName ? "border-red-500" : "border-gray-300"
                  } focus:outline-none focus:ring-2 focus:ring-blue-500`}
                />
                {editErrors.displayName && <p className="text-xs text-red-500 mt-1">{editErrors.displayName}</p>}
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
                  className={`w-full px-3 py-2 border rounded-md text-sm ${
                    editErrors.email ? "border-red-500" : "border-gray-300"
                  } focus:outline-none focus:ring-2 focus:ring-blue-500`}
                />
                {editErrors.email && <p className="text-xs text-red-500 mt-1">{editErrors.email}</p>}
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
                {saving ? "..." : t("profile.save")}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Email Changed Dialog */}
      {showEmailChanged && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/50" />
          <div className="relative bg-white rounded-lg shadow-xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-semibold text-gray-900 mb-2">{t("profile.emailChangedTitle")}</h3>
            <p className="text-sm text-gray-600 mb-4">{t("profile.emailChangedMessage")}</p>
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
