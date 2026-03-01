"use client";

import { useTranslations } from "next-intl";
import { useState } from "react";
import { api } from "@/services/api";

interface AssignTenantDialogProps {
  onClose: () => void;
  onAssigned: () => void;
}

interface SearchResult {
  userId: string;
  email: string;
  name: string;
  isAlreadyInTenant: boolean;
}

export function AssignTenantDialog({ onClose, onAssigned }: AssignTenantDialogProps) {
  const t = useTranslations("admin.members.assignTenant");
  const [searchEmail, setSearchEmail] = useState("");
  const [results, setResults] = useState<SearchResult[]>([]);
  const [searching, setSearching] = useState(false);
  const [selectedUser, setSelectedUser] = useState<SearchResult | null>(null);
  const [displayName, setDisplayName] = useState("");
  const [assigning, setAssigning] = useState(false);
  const [searched, setSearched] = useState(false);

  const handleSearch = async () => {
    if (!searchEmail.trim()) return;
    setSearching(true);
    setSelectedUser(null);
    try {
      const response = await api.admin.users.searchForAssignment(searchEmail);
      setResults(response.users);
      setSearched(true);
    } catch {
      setResults([]);
    } finally {
      setSearching(false);
    }
  };

  const handleSelectUser = (user: SearchResult) => {
    if (user.isAlreadyInTenant) return;
    setSelectedUser(user);
    setDisplayName(user.name || "");
  };

  const handleAssign = async () => {
    if (!selectedUser || !displayName.trim()) return;
    setAssigning(true);
    try {
      await api.admin.members.assignTenant(selectedUser.userId, displayName.trim());
      onAssigned();
    } catch {
      setAssigning(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") onClose();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      onKeyDown={handleKeyDown}
      role="dialog"
      aria-modal="true"
      aria-label={t("title")}
    >
      <div className="mx-4 w-full max-w-lg rounded-lg bg-white p-6 shadow-xl">
        <h2 className="mb-4 text-lg font-semibold text-gray-900">{t("title")}</h2>

        {/* Search */}
        <div className="mb-4 flex gap-2">
          <input
            type="email"
            value={searchEmail}
            onChange={(e) => setSearchEmail(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleSearch();
            }}
            placeholder={t("searchPlaceholder")}
            className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm"
          />
          <button
            type="button"
            onClick={handleSearch}
            disabled={searching || !searchEmail.trim()}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {t("search")}
          </button>
        </div>

        {/* Results */}
        {searched && results.length === 0 && <p className="mb-4 text-sm text-gray-500">{t("noResults")}</p>}
        {results.length > 0 && (
          <div className="mb-4 max-h-48 overflow-y-auto rounded-md border border-gray-200">
            {results.map((user) => (
              <button
                key={user.userId}
                type="button"
                disabled={user.isAlreadyInTenant}
                onClick={() => handleSelectUser(user)}
                className={`w-full border-b border-gray-100 px-4 py-3 text-left last:border-0 ${
                  selectedUser?.userId === user.userId
                    ? "bg-blue-50"
                    : user.isAlreadyInTenant
                      ? "cursor-not-allowed opacity-50"
                      : "hover:bg-gray-50"
                }`}
              >
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-sm font-medium text-gray-900">{user.name || user.email}</div>
                    <div className="text-xs text-gray-500">{user.email}</div>
                  </div>
                  {user.isAlreadyInTenant && (
                    <span className="rounded-full bg-gray-100 px-2 py-1 text-xs text-gray-500">
                      {t("alreadyAssigned")}
                    </span>
                  )}
                </div>
              </button>
            ))}
          </div>
        )}

        {/* Display Name + Assign */}
        {selectedUser && (
          <div className="mb-4">
            <label htmlFor="assign-display-name" className="mb-1 block text-sm font-medium text-gray-700">
              {t("displayName")}
            </label>
            <input
              id="assign-display-name"
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
          </div>
        )}

        {/* Actions */}
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            {t("cancel")}
          </button>
          {selectedUser && (
            <button
              type="button"
              onClick={handleAssign}
              disabled={assigning || !displayName.trim()}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {t("assign")}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
