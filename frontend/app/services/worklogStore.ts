/**
 * Work Log State Management Store
 *
 * Manages global state for the work log application using Zustand.
 * Handles calendar view state, selected date, and UI mode.
 *
 * This store is optimized for performance with React 19 and supports
 * concurrent rendering. Auto-save and mutations are handled separately
 * via TanStack Query hooks (see useAutoSave hook).
 */

import { create } from "zustand";
import { devtools, persist } from "zustand/middleware";

/**
 * Calendar view modes
 */
export type ViewMode = "month" | "week" | "day";

/**
 * Work log entry status
 */
export type EntryStatus = "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED";

/**
 * Work log entry type (partial - full type defined by backend API)
 */
export interface WorkLogEntry {
  id: string;
  memberId: string;
  date: string; // ISO date string
  projectId: string;
  hours: number;
  description: string | null;
  status: EntryStatus;
  version: number; // For optimistic locking
  enteredBy: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Copied projects from previous month
 */
export interface CopiedProjects {
  projectIds: string[];
  year: number;
  month: number;
  copiedAt: string;
}

/**
 * Subordinate member for proxy entry
 */
export interface SubordinateMember {
  id: string;
  email: string;
  displayName: string;
  managerId: string | null;
  isActive: boolean;
}

/**
 * Proxy entry mode state
 */
export interface ProxyMode {
  enabled: boolean;
  targetMember: SubordinateMember | null;
  managerId: string;
}

/**
 * UI state for the work log application
 */
interface WorkLogState {
  // Calendar view state
  selectedDate: Date;
  viewMode: ViewMode;
  fiscalMonthStart: Date | null;

  // Entry selection
  selectedEntryId: string | null;

  // UI state
  isEntryFormOpen: boolean;
  isLoading: boolean;

  // Copied projects from previous month (T151)
  copiedProjects: CopiedProjects | null;

  // Proxy entry mode (T156-T160)
  proxyMode: ProxyMode | null;

  // Calendar refresh trigger (incremented after save to force data reload)
  calendarRefreshKey: number;

  // Actions
  setSelectedDate: (date: Date) => void;
  setViewMode: (mode: ViewMode) => void;
  setFiscalMonthStart: (date: Date | null) => void;
  setSelectedEntryId: (id: string | null) => void;
  setEntryFormOpen: (isOpen: boolean) => void;
  setLoading: (isLoading: boolean) => void;
  setCopiedProjects: (projects: CopiedProjects | null) => void;
  clearCopiedProjects: () => void;

  // Calendar refresh
  incrementCalendarRefreshKey: () => void;

  // Proxy mode actions
  enableProxyMode: (managerId: string, targetMember: SubordinateMember) => void;
  disableProxyMode: () => void;
  setProxyTargetMember: (member: SubordinateMember | null) => void;

  // Computed helpers
  getSelectedDateISO: () => string;
  getEffectiveMemberId: (currentUserId: string) => string;

  // Reset state
  reset: () => void;
}

/**
 * Initial state values
 */
const initialState = {
  selectedDate: new Date(),
  viewMode: "month" as ViewMode,
  fiscalMonthStart: null,
  selectedEntryId: null,
  isEntryFormOpen: false,
  isLoading: false,
  copiedProjects: null,
  proxyMode: null,
  calendarRefreshKey: 0,
};

/**
 * Work Log Store
 *
 * Persists view preferences (viewMode) to localStorage.
 * Does NOT persist draft entries (handled by TanStack Query + localStorage in useAutoSave).
 */
export const useWorkLogStore = create<WorkLogState>()(
  devtools(
    persist(
      (set, get) => ({
        ...initialState,

        setSelectedDate: (date) => set({ selectedDate: date }),

        setViewMode: (mode) => set({ viewMode: mode }),

        setFiscalMonthStart: (date) => set({ fiscalMonthStart: date }),

        setSelectedEntryId: (id) => set({ selectedEntryId: id }),

        setEntryFormOpen: (isOpen) =>
          set({
            isEntryFormOpen: isOpen,
            selectedEntryId: isOpen ? get().selectedEntryId : null,
          }),

        setLoading: (isLoading) => set({ isLoading }),

        setCopiedProjects: (projects) => set({ copiedProjects: projects }),

        clearCopiedProjects: () => set({ copiedProjects: null }),

        incrementCalendarRefreshKey: () => set((state) => ({ calendarRefreshKey: state.calendarRefreshKey + 1 })),

        enableProxyMode: (managerId, targetMember) =>
          set({
            proxyMode: {
              enabled: true,
              targetMember,
              managerId,
            },
          }),

        disableProxyMode: () => set({ proxyMode: null }),

        setProxyTargetMember: (member) => {
          const current = get().proxyMode;
          if (current) {
            set({
              proxyMode: {
                ...current,
                targetMember: member,
              },
            });
          }
        },

        getSelectedDateISO: () => {
          const date = get().selectedDate;
          return date.toISOString().split("T")[0];
        },

        getEffectiveMemberId: (currentUserId: string) => {
          const proxyMode = get().proxyMode;
          if (proxyMode?.enabled && proxyMode.targetMember) {
            return proxyMode.targetMember.id;
          }
          return currentUserId;
        },

        reset: () => set(initialState),
      }),
      {
        name: "worklog-ui-state", // localStorage key
        partialize: (state) => ({
          // Only persist UI preferences, not transient state
          viewMode: state.viewMode,
        }),
      },
    ),
    {
      name: "WorkLogStore", // DevTools name
      enabled: process.env.NODE_ENV === "development",
    },
  ),
);

/**
 * Helper hook to get selected date in various formats
 */
export function useSelectedDate() {
  const selectedDate = useWorkLogStore((state) => state.selectedDate);
  const setSelectedDate = useWorkLogStore((state) => state.setSelectedDate);

  return {
    date: selectedDate,
    setDate: setSelectedDate,
    isoDate: selectedDate.toISOString().split("T")[0],
    year: selectedDate.getFullYear(),
    month: selectedDate.getMonth(), // 0-indexed
    day: selectedDate.getDate(),
  };
}

/**
 * Helper hook to check if an entry is editable
 * Draft entries are always editable. Submitted/Approved/Rejected are read-only.
 */
export function useIsEntryEditable(status: EntryStatus): boolean {
  return status === "DRAFT";
}

/**
 * Helper hook to access copied projects state
 */
export function useCopiedProjects() {
  const copiedProjects = useWorkLogStore((state) => state.copiedProjects);
  const setCopiedProjects = useWorkLogStore((state) => state.setCopiedProjects);
  const clearCopiedProjects = useWorkLogStore((state) => state.clearCopiedProjects);

  return {
    copiedProjects,
    setCopiedProjects,
    clearCopiedProjects,
    hasProjects: copiedProjects !== null && copiedProjects.projectIds.length > 0,
  };
}

/**
 * Helper hook to trigger calendar data refresh after save
 */
export function useCalendarRefresh() {
  const calendarRefreshKey = useWorkLogStore((state) => state.calendarRefreshKey);
  const triggerRefresh = useWorkLogStore((state) => state.incrementCalendarRefreshKey);

  return { calendarRefreshKey, triggerRefresh };
}

/**
 * Helper hook to access proxy mode state
 */
export function useProxyMode() {
  const proxyMode = useWorkLogStore((state) => state.proxyMode);
  const enableProxyMode = useWorkLogStore((state) => state.enableProxyMode);
  const disableProxyMode = useWorkLogStore((state) => state.disableProxyMode);
  const setProxyTargetMember = useWorkLogStore((state) => state.setProxyTargetMember);
  const getEffectiveMemberId = useWorkLogStore((state) => state.getEffectiveMemberId);

  return {
    proxyMode,
    isProxyMode: proxyMode?.enabled ?? false,
    targetMember: proxyMode?.targetMember ?? null,
    managerId: proxyMode?.managerId ?? null,
    enableProxyMode,
    disableProxyMode,
    setProxyTargetMember,
    getEffectiveMemberId,
  };
}
