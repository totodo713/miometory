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

import { create } from 'zustand'
import { devtools, persist } from 'zustand/middleware'

/**
 * Calendar view modes
 */
export type ViewMode = 'month' | 'week' | 'day'

/**
 * Work log entry status
 */
export type EntryStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED'

/**
 * Work log entry type (partial - full type defined by backend API)
 */
export interface WorkLogEntry {
  id: string
  memberId: string
  date: string // ISO date string
  projectId: string
  hours: number
  description: string | null
  status: EntryStatus
  version: number // For optimistic locking
  enteredBy: string
  createdAt: string
  updatedAt: string
}

/**
 * UI state for the work log application
 */
interface WorkLogState {
  // Calendar view state
  selectedDate: Date
  viewMode: ViewMode
  fiscalMonthStart: Date | null
  
  // Entry selection
  selectedEntryId: string | null
  
  // UI state
  isEntryFormOpen: boolean
  isLoading: boolean
  
  // Actions
  setSelectedDate: (date: Date) => void
  setViewMode: (mode: ViewMode) => void
  setFiscalMonthStart: (date: Date | null) => void
  setSelectedEntryId: (id: string | null) => void
  setEntryFormOpen: (isOpen: boolean) => void
  setLoading: (isLoading: boolean) => void
  
  // Computed helpers
  getSelectedDateISO: () => string
  
  // Reset state
  reset: () => void
}

/**
 * Initial state values
 */
const initialState = {
  selectedDate: new Date(),
  viewMode: 'month' as ViewMode,
  fiscalMonthStart: null,
  selectedEntryId: null,
  isEntryFormOpen: false,
  isLoading: false,
}

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
          set({ isEntryFormOpen: isOpen, selectedEntryId: isOpen ? get().selectedEntryId : null }),
        
        setLoading: (isLoading) => set({ isLoading }),
        
        getSelectedDateISO: () => {
          const date = get().selectedDate
          return date.toISOString().split('T')[0]
        },
        
        reset: () => set(initialState),
      }),
      {
        name: 'worklog-ui-state', // localStorage key
        partialize: (state) => ({
          // Only persist UI preferences, not transient state
          viewMode: state.viewMode,
        }),
      }
    ),
    {
      name: 'WorkLogStore', // DevTools name
      enabled: process.env.NODE_ENV === 'development',
    }
  )
)

/**
 * Helper hook to get selected date in various formats
 */
export function useSelectedDate() {
  const selectedDate = useWorkLogStore((state) => state.selectedDate)
  const setSelectedDate = useWorkLogStore((state) => state.setSelectedDate)
  
  return {
    date: selectedDate,
    setDate: setSelectedDate,
    isoDate: selectedDate.toISOString().split('T')[0],
    year: selectedDate.getFullYear(),
    month: selectedDate.getMonth(), // 0-indexed
    day: selectedDate.getDate(),
  }
}

/**
 * Helper hook to check if an entry is editable
 * Draft entries are always editable. Submitted/Approved/Rejected are read-only.
 */
export function useIsEntryEditable(status: EntryStatus): boolean {
  return status === 'DRAFT'
}
