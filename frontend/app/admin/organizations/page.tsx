"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { FiscalYearPatternForm } from "@/components/admin/FiscalYearPatternForm";
import { MemberManagerForm } from "@/components/admin/MemberManagerForm";
import { MonthlyPeriodPatternForm } from "@/components/admin/MonthlyPeriodPatternForm";
import { OrganizationForm } from "@/components/admin/OrganizationForm";
import { OrganizationList } from "@/components/admin/OrganizationList";
import { OrganizationTree } from "@/components/admin/OrganizationTree";
import { Breadcrumbs } from "@/components/shared/Breadcrumbs";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { useToast } from "@/hooks/useToast";
import { useAdminContext } from "@/providers/AdminProvider";
import type {
  EffectivePatterns,
  FiscalYearPatternOption,
  MonthlyPeriodPatternOption,
  OrganizationMemberRow,
  OrganizationRow,
  OrganizationTreeNode,
} from "@/services/api";
import { ApiError, api } from "@/services/api";

type ViewMode = "list" | "tree";

export default function AdminOrganizationsPage() {
  const t = useTranslations("admin.organizations");
  const tc = useTranslations("common");
  const tb = useTranslations("breadcrumbs");
  const { adminContext } = useAdminContext();
  const toast = useToast();
  const [viewMode, setViewMode] = useState<ViewMode>("list");
  const [editingOrg, setEditingOrg] = useState<OrganizationRow | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [confirmTarget, setConfirmTarget] = useState<{
    id: string;
    action: "deactivate" | "activate" | "removeManager";
    memberName?: string;
  } | null>(null);

  // Member detail view state (shared between list and tree views)
  const [selectedOrg, setSelectedOrg] = useState<{ id: string; name: string } | null>(null);
  const [members, setMembers] = useState<OrganizationMemberRow[]>([]);
  const [memberTotalPages, setMemberTotalPages] = useState(0);
  const [memberPage, setMemberPage] = useState(0);
  const [isMembersLoading, setIsMembersLoading] = useState(false);
  const [membersLoadError, setMembersLoadError] = useState<string | null>(null);
  const [memberRefreshKey, setMemberRefreshKey] = useState(0);

  // MemberManagerForm state
  const [memberFormMode, setMemberFormMode] = useState<"assignManager" | "transferOrg" | "createMember" | null>(null);
  const [targetMember, setTargetMember] = useState<OrganizationMemberRow | undefined>(undefined);

  // Pattern assignment state
  const [fiscalYearPatterns, setFiscalYearPatterns] = useState<FiscalYearPatternOption[]>([]);
  const [monthlyPeriodPatterns, setMonthlyPeriodPatterns] = useState<MonthlyPeriodPatternOption[]>([]);
  const [selectedFiscalYearPatternId, setSelectedFiscalYearPatternId] = useState<string>("");
  const [selectedMonthlyPeriodPatternId, setSelectedMonthlyPeriodPatternId] = useState<string>("");
  const [currentFiscalYearPatternId, setCurrentFiscalYearPatternId] = useState<string | null>(null);
  const [currentMonthlyPeriodPatternId, setCurrentMonthlyPeriodPatternId] = useState<string | null>(null);
  const [isPatternsLoading, setIsPatternsLoading] = useState(false);
  const [isPatternSaving, setIsPatternSaving] = useState(false);
  const [patternError, setPatternError] = useState<string | null>(null);
  const [patternSuccess, setPatternSuccess] = useState<string | null>(null);
  const [showFiscalYearForm, setShowFiscalYearForm] = useState(false);
  const [showMonthlyPeriodForm, setShowMonthlyPeriodForm] = useState(false);

  // Effective patterns state
  const [effectivePatterns, setEffectivePatterns] = useState<EffectivePatterns | null>(null);
  const [isEffectivePatternsLoading, setIsEffectivePatternsLoading] = useState(false);

  const refresh = useCallback(() => {
    setRefreshKey((k) => k + 1);
  }, []);

  const refreshMembers = useCallback(() => {
    setMemberRefreshKey((k) => k + 1);
  }, []);

  // Load members when an org is selected
  // biome-ignore lint/correctness/useExhaustiveDependencies: t from useTranslations is stable
  const loadMembers = useCallback(async () => {
    if (!selectedOrg) return;
    setIsMembersLoading(true);
    setMembersLoadError(null);
    try {
      const result = await api.admin.organizations.listMembers(selectedOrg.id, {
        page: memberPage,
        size: 20,
      });
      setMembers(result.content);
      setMemberTotalPages(result.totalPages);
    } catch (err: unknown) {
      setMembersLoadError(err instanceof ApiError ? err.message : t("membersFetchError"));
    } finally {
      setIsMembersLoading(false);
    }
  }, [selectedOrg, memberPage]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: memberRefreshKey triggers reload
  useEffect(() => {
    loadMembers();
  }, [loadMembers, memberRefreshKey]);

  // Load patterns when an org is selected
  useEffect(() => {
    const tenantId = adminContext?.tenantId;
    if (!selectedOrg || !tenantId) return;
    let cancelled = false;

    const loadPatterns = async () => {
      setIsPatternsLoading(true);
      setPatternError(null);
      setPatternSuccess(null);
      try {
        const [fyPatterns, mpPatterns] = await Promise.all([
          api.admin.patterns.listFiscalYearPatterns(tenantId),
          api.admin.patterns.listMonthlyPeriodPatterns(tenantId),
        ]);
        if (cancelled) return;
        setFiscalYearPatterns(fyPatterns);
        setMonthlyPeriodPatterns(mpPatterns);
      } catch {
        if (cancelled) return;
        setFiscalYearPatterns([]);
        setMonthlyPeriodPatterns([]);
      } finally {
        if (!cancelled) {
          setIsPatternsLoading(false);
        }
      }
    };

    loadPatterns();
    return () => {
      cancelled = true;
    };
  }, [selectedOrg, adminContext?.tenantId]);

  // Load effective patterns when org is selected
  useEffect(() => {
    if (!selectedOrg) {
      setEffectivePatterns(null);
      return;
    }
    let cancelled = false;

    const loadEffective = async () => {
      setIsEffectivePatternsLoading(true);
      try {
        const result = await api.admin.organizations.getEffectivePatterns(selectedOrg.id);
        if (!cancelled) setEffectivePatterns(result);
      } catch {
        if (!cancelled) setEffectivePatterns(null);
      } finally {
        if (!cancelled) setIsEffectivePatternsLoading(false);
      }
    };

    loadEffective();
    return () => {
      cancelled = true;
    };
  }, [selectedOrg]);

  // Initialize pattern selections from org data when org is selected from list
  const initPatternSelections = useCallback(
    (fiscalYearPatternId: string | null, monthlyPeriodPatternId: string | null) => {
      setCurrentFiscalYearPatternId(fiscalYearPatternId);
      setCurrentMonthlyPeriodPatternId(monthlyPeriodPatternId);
      setSelectedFiscalYearPatternId(fiscalYearPatternId ?? "");
      setSelectedMonthlyPeriodPatternId(monthlyPeriodPatternId ?? "");
    },
    [],
  );

  // biome-ignore lint/correctness/useExhaustiveDependencies: t/tc from useTranslations are stable
  const handlePatternSave = useCallback(async () => {
    if (!selectedOrg) return;
    setPatternError(null);
    setPatternSuccess(null);
    setIsPatternSaving(true);
    try {
      await api.admin.organizations.assignPatterns(
        selectedOrg.id,
        selectedFiscalYearPatternId || null,
        selectedMonthlyPeriodPatternId || null,
      );
      setCurrentFiscalYearPatternId(selectedFiscalYearPatternId || null);
      setCurrentMonthlyPeriodPatternId(selectedMonthlyPeriodPatternId || null);
      setPatternSuccess(t("patternSaved"));
      setTimeout(() => setPatternSuccess(null), 3000);
    } catch (err: unknown) {
      setPatternError(err instanceof ApiError ? err.message : tc("error"));
    } finally {
      setIsPatternSaving(false);
    }
  }, [selectedOrg, selectedFiscalYearPatternId, selectedMonthlyPeriodPatternId]);

  const handleDeactivate = useCallback((id: string) => {
    setConfirmTarget({ id, action: "deactivate" });
  }, []);

  const handleActivate = useCallback((id: string) => {
    setConfirmTarget({ id, action: "activate" });
  }, []);

  // biome-ignore lint/correctness/useExhaustiveDependencies: t/tc from useTranslations are stable
  const executeAction = useCallback(
    async (target: { id: string; action: "deactivate" | "activate" | "removeManager" }) => {
      try {
        if (target.action === "deactivate") {
          const result = await api.admin.organizations.deactivate(target.id);
          if (result.warnings && result.warnings.length > 0) {
            toast.warning(t("deactivateWarning", { warnings: result.warnings.join(", ") }));
          } else {
            toast.success(t("deactivated"));
          }
          refresh();
        } else if (target.action === "activate") {
          await api.admin.organizations.activate(target.id);
          toast.success(t("activated"));
          refresh();
        } else if (target.action === "removeManager") {
          await api.admin.members.removeManager(target.id);
          toast.success(t("managerRemoved"));
          refreshMembers();
        }
      } catch (err: unknown) {
        toast.error(err instanceof ApiError ? err.message : tc("error"));
      }
    },
    [refresh, refreshMembers, toast],
  );

  const handleEdit = useCallback((org: OrganizationRow) => {
    setEditingOrg(org);
    setShowForm(true);
  }, []);

  const handleSaved = useCallback(() => {
    setShowForm(false);
    setEditingOrg(null);
    refresh();
  }, [refresh]);

  const handleClose = useCallback(() => {
    setShowForm(false);
    setEditingOrg(null);
  }, []);

  const handleSelectOrg = useCallback(
    (org: OrganizationRow) => {
      setSelectedOrg({ id: org.id, name: org.name });
      setMemberPage(0);
      setMembers([]);
      initPatternSelections(org.fiscalYearPatternId, org.monthlyPeriodPatternId);
    },
    [initPatternSelections],
  );

  const handleTreeSelectOrg = useCallback(async (org: OrganizationTreeNode) => {
    setSelectedOrg({ id: org.id, name: org.name });
    setMemberPage(0);
    setMembers([]);
    // Fetch full org data to get pattern IDs
    try {
      const result = await api.admin.organizations.list({ search: org.code, size: 1 });
      const fullOrg = result.content.find((o) => o.id === org.id);
      if (fullOrg) {
        setCurrentFiscalYearPatternId(fullOrg.fiscalYearPatternId ?? null);
        setCurrentMonthlyPeriodPatternId(fullOrg.monthlyPeriodPatternId ?? null);
        setSelectedFiscalYearPatternId(fullOrg.fiscalYearPatternId ?? "");
        setSelectedMonthlyPeriodPatternId(fullOrg.monthlyPeriodPatternId ?? "");
      } else {
        setCurrentFiscalYearPatternId(null);
        setCurrentMonthlyPeriodPatternId(null);
        setSelectedFiscalYearPatternId("");
        setSelectedMonthlyPeriodPatternId("");
      }
    } catch {
      setCurrentFiscalYearPatternId(null);
      setCurrentMonthlyPeriodPatternId(null);
      setSelectedFiscalYearPatternId("");
      setSelectedMonthlyPeriodPatternId("");
    }
  }, []);

  const handleBackToList = useCallback(() => {
    setSelectedOrg(null);
    setMembers([]);
    setMemberPage(0);
    setFiscalYearPatterns([]);
    setMonthlyPeriodPatterns([]);
    setPatternError(null);
    setPatternSuccess(null);
  }, []);

  const handleAssignManager = useCallback((member: OrganizationMemberRow) => {
    setTargetMember(member);
    setMemberFormMode("assignManager");
  }, []);

  const handleRemoveManager = useCallback((member: OrganizationMemberRow) => {
    setConfirmTarget({ id: member.id, action: "removeManager", memberName: member.displayName });
  }, []);

  const handleTransferOrg = useCallback((member: OrganizationMemberRow) => {
    setTargetMember(member);
    setMemberFormMode("transferOrg");
  }, []);

  const handleCreateMember = useCallback(() => {
    setTargetMember(undefined);
    setMemberFormMode("createMember");
  }, []);

  const handleMemberFormSaved = useCallback(() => {
    setMemberFormMode(null);
    setTargetMember(undefined);
    refreshMembers();
    refresh();
  }, [refreshMembers, refresh]);

  const handleMemberFormClose = useCallback(() => {
    setMemberFormMode(null);
    setTargetMember(undefined);
  }, []);

  const handleViewModeChange = useCallback(
    (mode: ViewMode) => {
      setViewMode(mode);
      // Clear selection when switching views
      if (selectedOrg) {
        setSelectedOrg(null);
        setMembers([]);
        setMemberPage(0);
      }
    },
    [selectedOrg],
  );

  const patternsDirty =
    selectedFiscalYearPatternId !== (currentFiscalYearPatternId ?? "") ||
    selectedMonthlyPeriodPatternId !== (currentMonthlyPeriodPatternId ?? "");

  return (
    <div>
      <Breadcrumbs items={[{ label: tb("admin"), href: "/admin" }, { label: tb("organizations") }]} />

      <div className="flex items-center justify-between mb-6 mt-4">
        <div className="flex items-center gap-3">
          {selectedOrg && (
            <button
              type="button"
              onClick={handleBackToList}
              className="px-3 py-1.5 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50"
            >
              {t("back")}
            </button>
          )}
          <h1 className="text-2xl font-bold text-gray-900">
            {selectedOrg ? t("orgMembers", { name: selectedOrg.name }) : t("title")}
          </h1>
        </div>
        {!selectedOrg ? (
          <button
            type="button"
            onClick={() => {
              setEditingOrg(null);
              setShowForm(true);
            }}
            className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700"
          >
            {tc("create")}
          </button>
        ) : (
          <button
            type="button"
            onClick={handleCreateMember}
            className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700"
          >
            {t("createMember")}
          </button>
        )}
      </div>

      {/* View mode tabs - only show when no org is selected */}
      {!selectedOrg && (
        <div role="tablist" className="flex gap-0 mb-4 border-b border-gray-200">
          <button
            type="button"
            role="tab"
            aria-selected={viewMode === "list"}
            onClick={() => handleViewModeChange("list")}
            className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px ${
              viewMode === "list"
                ? "border-blue-600 text-blue-600"
                : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
            }`}
          >
            {t("listView")}
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={viewMode === "tree"}
            onClick={() => handleViewModeChange("tree")}
            className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px ${
              viewMode === "tree"
                ? "border-blue-600 text-blue-600"
                : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
            }`}
          >
            {t("treeView")}
          </button>
        </div>
      )}

      {!selectedOrg ? (
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          {viewMode === "list" ? (
            <OrganizationList
              onEdit={handleEdit}
              onDeactivate={handleDeactivate}
              onActivate={handleActivate}
              refreshKey={refreshKey}
              onSelectOrg={handleSelectOrg}
            />
          ) : (
            <OrganizationTree refreshKey={refreshKey} onSelectOrg={handleTreeSelectOrg} />
          )}
        </div>
      ) : (
        <div className="space-y-4">
          {/* Pattern assignment section */}
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">{t("patternSettings")}</h2>
            {isPatternsLoading ? (
              <div className="text-sm text-gray-500">{tc("loading")}</div>
            ) : fiscalYearPatterns.length === 0 && monthlyPeriodPatterns.length === 0 ? (
              <div className="space-y-3">
                <p className="text-sm text-gray-500">{t("noPatternsRegistered")}</p>
                {currentFiscalYearPatternId && (
                  <p className="text-xs text-gray-500">
                    {t("currentFiscalYearPatternId", { id: currentFiscalYearPatternId })}
                  </p>
                )}
                {currentMonthlyPeriodPatternId && (
                  <p className="text-xs text-gray-500">
                    {t("currentMonthlyPeriodPatternId", { id: currentMonthlyPeriodPatternId })}
                  </p>
                )}
              </div>
            ) : (
              <div className="space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label htmlFor="fiscal-year-pattern" className="block text-sm font-medium text-gray-700 mb-1">
                      {t("fiscalYearPattern")}
                    </label>
                    <div className="flex items-center gap-2">
                      <select
                        id="fiscal-year-pattern"
                        value={selectedFiscalYearPatternId}
                        onChange={(e) => {
                          setSelectedFiscalYearPatternId(e.target.value);
                          setPatternError(null);
                          setPatternSuccess(null);
                        }}
                        className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      >
                        <option value="">{t("unset")}</option>
                        {fiscalYearPatterns.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.name} ({p.startMonth}/{p.startDay})
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        onClick={() => setShowFiscalYearForm(true)}
                        className="text-sm text-blue-600 hover:text-blue-800 whitespace-nowrap"
                      >
                        {t("createNew")}
                      </button>
                    </div>
                  </div>
                  <div>
                    <label htmlFor="monthly-period-pattern" className="block text-sm font-medium text-gray-700 mb-1">
                      {t("monthlyPeriodPattern")}
                    </label>
                    <div className="flex items-center gap-2">
                      <select
                        id="monthly-period-pattern"
                        value={selectedMonthlyPeriodPatternId}
                        onChange={(e) => {
                          setSelectedMonthlyPeriodPatternId(e.target.value);
                          setPatternError(null);
                          setPatternSuccess(null);
                        }}
                        className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      >
                        <option value="">{t("unset")}</option>
                        {monthlyPeriodPatterns.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.name} ({p.startDay})
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        onClick={() => setShowMonthlyPeriodForm(true)}
                        className="text-sm text-blue-600 hover:text-blue-800 whitespace-nowrap"
                      >
                        {t("createNew")}
                      </button>
                    </div>
                  </div>
                </div>

                {patternError && <p className="text-sm text-red-600">{patternError}</p>}
                {patternSuccess && <p className="text-sm text-green-600">{patternSuccess}</p>}

                <div className="flex justify-end">
                  <button
                    type="button"
                    onClick={handlePatternSave}
                    disabled={isPatternSaving || !patternsDirty}
                    className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50"
                  >
                    {isPatternSaving ? tc("saving") : tc("save")}
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Effective patterns (inheritance display) */}
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">{t("effectivePatterns")}</h2>
            {isEffectivePatternsLoading ? (
              <div className="text-sm text-gray-500">{tc("loading")}</div>
            ) : effectivePatterns ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <p className="text-sm font-medium text-gray-700">{t("fiscalYearPattern")}</p>
                  <p className="text-sm text-gray-500 mt-1">
                    {t("inheritedFrom")}:{" "}
                    {effectivePatterns.fiscalYearSource === "system"
                      ? t("source.system")
                      : effectivePatterns.fiscalYearSource === "tenant"
                        ? t("source.tenant")
                        : t("source.organization", {
                            name: effectivePatterns.fiscalYearSource.replace("organization:", ""),
                          })}
                  </p>
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-700">{t("monthlyPeriodPattern")}</p>
                  <p className="text-sm text-gray-500 mt-1">
                    {t("inheritedFrom")}:{" "}
                    {effectivePatterns.monthlyPeriodSource === "system"
                      ? t("source.system")
                      : effectivePatterns.monthlyPeriodSource === "tenant"
                        ? t("source.tenant")
                        : t("source.organization", {
                            name: effectivePatterns.monthlyPeriodSource.replace("organization:", ""),
                          })}
                  </p>
                </div>
              </div>
            ) : (
              <div className="text-sm text-gray-500">{tc("noData")}</div>
            )}
          </div>

          {/* Members table */}
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            {membersLoadError ? (
              <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-center">
                <p className="text-sm text-red-800">{membersLoadError}</p>
                <button
                  type="button"
                  onClick={loadMembers}
                  className="mt-2 text-sm text-red-600 hover:text-red-800 underline"
                >
                  {tc("retry")}
                </button>
              </div>
            ) : isMembersLoading ? (
              <div className="text-center py-8 text-gray-500">{tc("loading")}</div>
            ) : members.length === 0 ? (
              <div className="text-center py-8 text-gray-500">{tc("noData")}</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200">
                      <th className="text-left py-3 px-4 font-medium text-gray-700">{t("memberTable.name")}</th>
                      <th className="text-left py-3 px-4 font-medium text-gray-700">{t("memberTable.email")}</th>
                      <th className="text-left py-3 px-4 font-medium text-gray-700">{t("memberTable.manager")}</th>
                      <th className="text-left py-3 px-4 font-medium text-gray-700">{t("memberTable.status")}</th>
                      <th className="text-right py-3 px-4 font-medium text-gray-700">{t("memberTable.actions")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {members.map((member) => (
                      <tr key={member.id} className="border-b border-gray-100 hover:bg-gray-50">
                        <td className="py-3 px-4">{member.displayName}</td>
                        <td className="py-3 px-4 text-gray-600">{member.email}</td>
                        <td className="py-3 px-4">
                          {member.managerName ? (
                            <span className="flex items-center gap-1.5">
                              <span className="text-gray-700">{member.managerName}</span>
                              {member.managerIsActive === false && (
                                <span
                                  className="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium bg-yellow-100 text-yellow-800"
                                  title={t("managerInactive")}
                                >
                                  {tc("inactive")}
                                </span>
                              )}
                            </span>
                          ) : (
                            <span className="text-gray-400">--</span>
                          )}
                        </td>
                        <td className="py-3 px-4">
                          <span
                            className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                              member.isActive ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
                            }`}
                          >
                            {member.isActive ? tc("active") : tc("inactive")}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-right">
                          <div className="flex justify-end gap-2">
                            <button
                              type="button"
                              onClick={() => handleAssignManager(member)}
                              className="text-blue-600 hover:text-blue-800 text-xs"
                            >
                              {member.managerId ? t("changeManager") : t("assignManager")}
                            </button>
                            {member.managerId && (
                              <button
                                type="button"
                                onClick={() => handleRemoveManager(member)}
                                className="text-red-600 hover:text-red-800 text-xs"
                              >
                                {t("removeManager")}
                              </button>
                            )}
                            <button
                              type="button"
                              onClick={() => handleTransferOrg(member)}
                              className="text-purple-600 hover:text-purple-800 text-xs"
                            >
                              {t("transferOrg")}
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {memberTotalPages > 1 && (
              <div className="flex justify-center gap-2 mt-4">
                <button
                  type="button"
                  onClick={() => setMemberPage((p) => Math.max(0, p - 1))}
                  disabled={memberPage === 0}
                  className="px-3 py-1 text-sm border rounded disabled:opacity-50"
                >
                  {tc("previous")}
                </button>
                <span className="px-3 py-1 text-sm text-gray-600">
                  {tc("pagination.page", { current: memberPage + 1, total: memberTotalPages })}
                </span>
                <button
                  type="button"
                  onClick={() => setMemberPage((p) => Math.min(memberTotalPages - 1, p + 1))}
                  disabled={memberPage >= memberTotalPages - 1}
                  className="px-3 py-1 text-sm border rounded disabled:opacity-50"
                >
                  {tc("next")}
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {showForm && <OrganizationForm organization={editingOrg} onClose={handleClose} onSaved={handleSaved} />}

      {memberFormMode && selectedOrg && (
        <MemberManagerForm
          mode={memberFormMode}
          organizationId={selectedOrg.id}
          member={targetMember}
          onClose={handleMemberFormClose}
          onSaved={handleMemberFormSaved}
        />
      )}

      {showFiscalYearForm && adminContext?.tenantId && (
        <FiscalYearPatternForm
          tenantId={adminContext.tenantId}
          open={showFiscalYearForm}
          onClose={() => setShowFiscalYearForm(false)}
          onCreated={(pattern) => {
            setFiscalYearPatterns((prev) => [...prev, { ...pattern, tenantId: adminContext.tenantId ?? "" }]);
            setSelectedFiscalYearPatternId(pattern.id);
            setShowFiscalYearForm(false);
          }}
        />
      )}

      {showMonthlyPeriodForm && adminContext?.tenantId && (
        <MonthlyPeriodPatternForm
          tenantId={adminContext.tenantId}
          open={showMonthlyPeriodForm}
          onClose={() => setShowMonthlyPeriodForm(false)}
          onCreated={(pattern) => {
            setMonthlyPeriodPatterns((prev) => [...prev, { ...pattern, tenantId: adminContext.tenantId ?? "" }]);
            setSelectedMonthlyPeriodPatternId(pattern.id);
            setShowMonthlyPeriodForm(false);
          }}
        />
      )}

      <ConfirmDialog
        open={confirmTarget !== null}
        title={tc("confirm")}
        message={
          confirmTarget?.action === "removeManager"
            ? t("confirmRemoveManager", { name: confirmTarget.memberName ?? "" })
            : confirmTarget?.action === "deactivate"
              ? t("confirmDeactivate")
              : t("confirmActivate")
        }
        confirmLabel={
          confirmTarget?.action === "removeManager"
            ? t("removeManagerLabel")
            : confirmTarget?.action === "deactivate"
              ? tc("disable")
              : tc("enable")
        }
        variant={confirmTarget?.action === "activate" ? "warning" : "danger"}
        onConfirm={async () => {
          if (confirmTarget) await executeAction(confirmTarget);
          setConfirmTarget(null);
        }}
        onCancel={() => setConfirmTarget(null)}
      />
    </div>
  );
}
