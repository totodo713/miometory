# Frontend Admin Panel Patterns - Complete Guide

## Overview
The admin panel uses a standardized, repeatable pattern for CRUD operations across different resource types (Members, Projects, Tenants, Users). All admin pages are protected by the `AdminProvider` which enforces permission-based access control.

## Directory Structure

```
frontend/app/
├── admin/
│   ├── layout.tsx                 # Admin layout with navigation & auth guard
│   ├── page.tsx                   # Admin dashboard (permission-gated cards)
│   ├── members/
│   │   └── page.tsx               # Members management page
│   ├── projects/
│   │   └── page.tsx               # Projects management page
│   ├── tenants/
│   │   └── page.tsx               # Tenants management page
│   ├── users/
│   │   └── page.tsx               # Users management page
│   └── assignments/
│       └── page.tsx               # Assignment manager page
├── components/admin/
│   ├── AdminNav.tsx               # Left sidebar navigation
│   ├── MemberList.tsx             # Member table with search/pagination
│   ├── MemberForm.tsx             # Member create/edit modal
│   ├── ProjectList.tsx            # Project table with search/pagination
│   ├── ProjectForm.tsx            # Project create/edit modal
│   ├── TenantList.tsx             # Tenant table with filtering
│   ├── TenantForm.tsx             # Tenant create/edit modal
│   ├── UserList.tsx               # User table with filtering
│   ├── AssignmentManager.tsx       # Assignment management interface
│   └── DailyApprovalDashboard.tsx # Daily entry approval/rejection
├── providers/
│   └── AdminProvider.tsx           # Admin context, permissions, auth check
└── services/
    └── api.ts                      # API client (admin section at lines 738-913)

tests/unit/components/admin/
├── MemberForm.test.tsx
├── MemberList.test.tsx
├── ProjectForm.test.tsx
└── ProjectList.test.tsx
```

---

## 1. Layout & Navigation Pattern

### Admin Layout (`admin/layout.tsx`)
- **Wrapped with**: `AuthGuard` → `AdminProvider` → `AdminLayoutInner`
- **AuthGuard**: Redirects unauthenticated users to login
- **AdminProvider**: Fetches admin context, checks permissions, redirects non-admin users to `/worklog`
- **Structure**: Flex layout with `AdminNav` sidebar + main content area (padding p-6, bg-gray-50)

```typescript
// Pattern:
// 1. Check auth with AuthGuard
// 2. Load admin context with AdminProvider
// 3. Block navigation if no admin context
// 4. Render sidebar + main area
```

### AdminNav Component (`components/admin/AdminNav.tsx`)
- **Static NAV_ITEMS array**: Dashboard, Tenants, Users, Members, Projects, Assignments
- **Permission-based visibility**: `hasPermission("permission.view")` filters visible items
- **Active route detection**: 
  - Exact match for `/admin` (dashboard)
  - Prefix match for others (e.g., `/admin/members` matches `/admin/members/*`)
- **Styling**: 
  - Active: `bg-blue-50 text-blue-700 font-medium border-r-2 border-blue-700`
  - Inactive: `text-gray-700 hover:bg-gray-50`

---

## 2. List Component Pattern

All list components (MemberList, ProjectList, TenantList, UserList) follow this structure:

### Common Props
```typescript
interface ListProps {
  onEdit: (item: ItemRow) => void;
  onDeactivate: (id: string) => void;
  onActivate: (id: string) => void;
  refreshKey: number;  // Triggers reload when changed
}
```

### State Management
```typescript
const [items, setItems] = useState<ItemRow[]>([]);
const [totalPages, setTotalPages] = useState(0);
const [page, setPage] = useState(0);
const [search, setSearch] = useState("");          // User input
const [debouncedSearch, setDebouncedSearch] = useState("");  // Debounced
const [showInactive, setShowInactive] = useState(false);    // Filter toggle
const [isLoading, setIsLoading] = useState(true);
```

### Search Debouncing Pattern
```typescript
useEffect(() => {
  const timer = setTimeout(() => {
    setDebouncedSearch(search);  // Update after 300ms of inactivity
  }, 300);
  return () => clearTimeout(timer);
}, [search]);
```

### Data Loading Pattern
```typescript
const loadItems = useCallback(async () => {
  setIsLoading(true);
  try {
    const result = await api.admin.resourceType.list({
      page,
      size: 20,
      search: debouncedSearch || undefined,
      isActive: showInactive ? undefined : true,  // undefined = show all
    });
    setItems(result.content);
    setTotalPages(result.totalPages);
  } catch {
    // Error handled by API client, silently fail
  } finally {
    setIsLoading(false);
  }
}, [page, debouncedSearch, showInactive]);

useEffect(() => {
  loadItems();
}, [loadItems, refreshKey]);
```

### Pagination Pattern
```typescript
{totalPages > 1 && (
  <div className="flex justify-center gap-2 mt-4">
    <button ... onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}>
      前へ
    </button>
    <span>{page + 1} / {totalPages}</span>
    <button ... onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} 
            disabled={page >= totalPages - 1}>
      次へ
    </button>
  </div>
)}
```

### Table Structure
```typescript
<table className="w-full text-sm">
  <thead>
    <tr className="border-b border-gray-200">
      <th className="text-left py-3 px-4 font-medium text-gray-700">Column</th>
      ...
    </tr>
  </thead>
  <tbody>
    {items.map(item => (
      <tr key={item.id} className="border-b border-gray-100 hover:bg-gray-50">
        {/* Status badge */}
        <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
          item.isActive ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
        }`}>
          {item.isActive ? "有効" : "無効"}
        </span>
        {/* Action buttons */}
        <button onClick={() => onEdit(item)} className="text-blue-600 hover:text-blue-800 text-xs">
          編集
        </button>
      </tr>
    ))}
  </tbody>
</table>
```

### Search/Filter UI Pattern
```typescript
<div className="flex items-center gap-4 mb-4">
  <input
    type="text"
    placeholder="名前またはメールで検索..."
    value={search}
    onChange={(e) => {
      setSearch(e.target.value);
      setPage(0);  // Reset to first page
    }}
    aria-label="検索"
    className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
  />
  <label className="flex items-center gap-2 text-sm text-gray-600 whitespace-nowrap">
    <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
    無効も表示
  </label>
</div>
```

---

## 3. Form Component Pattern

All form components (MemberForm, ProjectForm, TenantForm) follow this modal pattern:

### Props
```typescript
interface FormProps {
  item: ItemRow | null;     // null = create mode, item = edit mode
  onClose: () => void;      // Close modal (Escape key & cancel button)
  onSaved: () => void;      // Callback after successful save
}
```

### State
```typescript
const isEdit = item !== null;
const [field1, setField1] = useState(item?.field1 ?? "");
const [error, setError] = useState<string | null>(null);
const [isSubmitting, setIsSubmitting] = useState(false);
```

### Escape Key Handler
```typescript
useEffect(() => {
  const handleKeyDown = (e: KeyboardEvent) => {
    if (e.key === "Escape") onClose();
  };
  document.addEventListener("keydown", handleKeyDown);
  return () => document.removeEventListener("keydown", handleKeyDown);
}, [onClose]);
```

### Form Submission Pattern
```typescript
const handleSubmit = async (e: React.FormEvent) => {
  e.preventDefault();
  setError(null);
  
  // Client-side validation
  if (!field1.trim() || !field2.trim()) {
    setError("必須フィールドです");
    return;
  }
  
  setIsSubmitting(true);
  try {
    if (isEdit && item) {
      await api.admin.resourceType.update(item.id, { field1, field2 });
    } else {
      await api.admin.resourceType.create({ field1, field2 });
    }
    onSaved();  // Parent handles refresh
  } catch (err: unknown) {
    if (err instanceof ApiError) {
      setError(err.message);  // Use server error message
    } else {
      setError("エラーが発生しました");
    }
  } finally {
    setIsSubmitting(false);
  }
};
```

### Modal UI Pattern
```typescript
<div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
  <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
    <h2 className="text-lg font-semibold text-gray-900 mb-4">
      {isEdit ? "編集" : "作成"}
    </h2>
    
    <form onSubmit={handleSubmit} className="space-y-4">
      {/* Form fields */}
      <div>
        <label htmlFor="field-id" className="block text-sm font-medium text-gray-700 mb-1">
          フィールド名
        </label>
        <input
          id="field-id"
          type="text"
          value={field1}
          onChange={(e) => setField1(e.target.value)}
          disabled={isEdit && isReadOnly}  // Code field disabled in edit mode
          required
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
        />
      </div>
      
      {error && <p className="text-sm text-red-600">{error}</p>}
      
      {/* Action buttons */}
      <div className="flex justify-end gap-3 pt-2">
        <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50">
          キャンセル
        </button>
        <button
          type="submit"
          disabled={isSubmitting}
          className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50"
        >
          {isSubmitting ? "保存中..." : isEdit ? "更新" : "作成"}
        </button>
      </div>
    </form>
  </div>
</div>
```

---

## 4. Page Component Pattern

All management pages (members, projects, tenants, users) follow this pattern:

### Structure
```typescript
"use client";

export default function AdminResourcePage() {
  const [editingItem, setEditingItem] = useState<ItemRow | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  
  const refresh = useCallback(() => setRefreshKey(k => k + 1), []);
  
  const handleEdit = useCallback((item: ItemRow) => {
    setEditingItem(item);
    setShowForm(true);
  }, []);
  
  const handleSaved = useCallback(() => {
    setShowForm(false);
    setEditingItem(null);
    refresh();
  }, [refresh]);
  
  const handleClose = useCallback(() => {
    setShowForm(false);
    setEditingItem(null);
  }, []);
  
  const handleDeactivate = useCallback(
    async (id: string) => {
      if (!confirm("このアイテムを無効化しますか？")) return;
      try {
        await api.admin.resourceType.deactivate(id);
        refresh();
      } catch (err: unknown) {
        alert(err instanceof ApiError ? err.message : "エラーが発生しました");
      }
    },
    [refresh],
  );
  
  const handleActivate = useCallback(
    async (id: string) => {
      try {
        await api.admin.resourceType.activate(id);
        refresh();
      } catch (err: unknown) {
        alert(err instanceof ApiError ? err.message : "エラーが発生しました");
      }
    },
    [refresh],
  );
  
  return (
    <div>
      {/* Header with title and create button */}
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">リソース管理</h1>
        <button
          type="button"
          onClick={() => {
            setEditingItem(null);
            setShowForm(true);
          }}
          className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700"
        >
          作成
        </button>
      </div>
      
      {/* List component */}
      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <ResourceList
          onEdit={handleEdit}
          onDeactivate={handleDeactivate}
          onActivate={handleActivate}
          refreshKey={refreshKey}
        />
      </div>
      
      {/* Form modal */}
      {showForm && (
        <ResourceForm
          item={editingItem}
          onClose={handleClose}
          onSaved={handleSaved}
        />
      )}
    </div>
  );
}
```

---

## 5. Admin Provider & Permission Pattern

### AdminProvider (`providers/AdminProvider.tsx`)

```typescript
interface AdminContext {
  role: string;
  permissions: string[];
  tenantId: string | null;
  tenantName: string | null;
  memberId: string | null;
}

interface AdminContextValue {
  adminContext: AdminContext | null;
  isLoading: boolean;
  hasPermission: (permission: string) => boolean;
}
```

### Key Features
1. **Lazy loading**: Fetches admin context on mount with cancellation support
2. **Permission-based access**: `hasPermission(permission)` checks if user has permission
3. **Memoization**: Prevents unnecessary re-renders of context consumers

### Usage in Components
```typescript
const { adminContext, hasPermission } = useAdminContext();

if (!adminContext) return null;

{hasPermission("member.view") && (
  <DashboardCard
    title="メンバー管理"
    href="/admin/members"
  />
)}
```

---

## 6. API Service Layer Pattern

### Admin API Structure (`services/api.ts` lines 738-913)

```typescript
api.admin = {
  // Get admin context and permissions
  getContext: () => apiClient.get<AdminContext>("/api/v1/admin/context"),
  
  members: {
    list: (params?: ListParams) => apiClient.get<PaginatedResponse>(...)
    create: (data: CreateData) => apiClient.post<{ id: string }>(...)
    update: (id: string, data: UpdateData) => apiClient.put<void>(...)
    deactivate: (id: string) => apiClient.patch<void>(...)
    activate: (id: string) => apiClient.patch<void>(...)
    assignTenantAdmin: (id: string) => apiClient.post<void>(...)
  },
  
  projects: { /* similar pattern */ },
  tenants: { /* similar pattern */ },
  users: { /* similar pattern */ },
  assignments: { /* similar pattern */ }
}
```

### Pagination Response Pattern
```typescript
{
  content: Array<ItemRow>;
  totalElements: number;
  totalPages: number;
  number: number;  // current page
}
```

### Query Parameter Pattern
```typescript
const query = new URLSearchParams();
if (params?.page !== undefined) query.set("page", params.page.toString());
if (params?.size !== undefined) query.set("size", params.size.toString());
if (params?.search) query.set("search", params.search);
if (params?.isActive !== undefined) query.set("isActive", params.isActive.toString());
return apiClient.get(`/api/v1/admin/members?${query}`);
```

### Error Handling Pattern
```typescript
try {
  const result = await api.admin.members.list({ ... });
} catch (err: unknown) {
  if (err instanceof ApiError) {
    setError(err.message);  // Display server error
  } else {
    setError("エラーが発生しました");  // Generic error
  }
}
```

---

## 7. Test Patterns

### Setup Pattern
```typescript
const mockListMembers = vi.fn();

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      members: {
        list: (...args: unknown[]) => mockListMembers(...args),
      },
    },
  },
}));

beforeEach(() => {
  vi.clearAllMocks();
  mockListMembers.mockResolvedValue({
    content: [activeMember],
    totalPages: 1,
    totalElements: 1,
    number: 0,
  });
});
```

### List Component Tests
- **Loading state**: Renders "読み込み中..." initially
- **Data display**: After load, shows table with correct data
- **Empty state**: Shows message when no items
- **Status badges**: Active/inactive display correct colors
- **Search**: Debounced search passes to API with correct params
- **Filter toggle**: `isActive: undefined` when showing inactive
- **Pagination**: Shows/hides based on totalPages, handles navigation
- **Refresh**: Reloads when refreshKey prop changes
- **Callbacks**: `onEdit(item)`, `onDeactivate(id)`, `onActivate(id)` called with correct params

### Form Component Tests
- **Create mode**: Empty fields, "招待" button, calls `create()` API
- **Edit mode**: Pre-filled fields, "更新" button, calls `update()` API
- **Validation**: Shows error for empty required fields (whitespace trimmed)
- **Read-only fields**: Code field disabled in edit mode
- **Date validation**: End date >= start date
- **API errors**: Displays ApiError message
- **Generic errors**: Shows generic error for non-API errors
- **Loading state**: Button disabled while submitting, shows "保存中..."
- **Close on Escape**: Modal dismisses when Escape key pressed
- **Callbacks**: `onSaved()` called on success, `onClose()` on cancel

### Key Testing Libraries
- `@testing-library/react`: render, screen, waitFor
- `@testing-library/user-event`: userEvent.setup(), userEvent.type(), userEvent.click()
- `vitest`: vi.fn(), vi.mock(), vi.clearAllMocks()

---

## 8. Naming Conventions & Key Patterns

### Component Naming
- List components: `ResourceList` (MemberList, ProjectList, TenantList, UserList)
- Form components: `ResourceForm` (MemberForm, ProjectForm, TenantForm)
- Page components: `AdminResourcePage` (e.g., AdminMembersPage)

### State Variables
- Item state: `editing{Resource}` (e.g., editingMember, editingProject)
- Form state: `field1`, `field2` (not nested objects for simplicity)
- Submission state: `isSubmitting` (boolean)
- Error state: `error` (null | string)
- UI state: `showForm` (boolean)
- Refresh trigger: `refreshKey` (number, increment to trigger reload)

### List Item Row Interface Naming
- `MemberRow`, `ProjectRow`, `TenantRow`, `UserRow`
- Includes all fields needed for display and actions
- Exported from list component for type reuse in forms

### Callback Props Naming
- `onEdit`, `onDeactivate`, `onActivate`, `onLock`, `onUnlock`
- `onClose`, `onSaved`, `onRefresh`
- Callbacks passed through props down from pages to lists to forms

### API Endpoint Pattern
- GET list: `/api/v1/admin/{resource}?page=0&size=20&search=...&isActive=true`
- POST create: `/api/v1/admin/{resource}`
- PUT update: `/api/v1/admin/{resource}/{id}`
- PATCH deactivate: `/api/v1/admin/{resource}/{id}/deactivate`
- PATCH activate: `/api/v1/admin/{resource}/{id}/activate`

### CSS Classes Pattern
- Containers: `bg-white rounded-lg border border-gray-200 p-4`
- Tables: `w-full text-sm`, headers `border-b border-gray-200`
- Buttons: `px-4 py-2 text-sm {color} rounded-md hover:{hover-color}`
- Status badges: `inline-block px-2 py-0.5 rounded-full text-xs font-medium {bg-color}`
- Modals: `fixed inset-0 bg-black/50 flex items-center justify-center z-50`

---

## 9. Special Component Patterns

### AssignmentManager
- **Two view modes**: "by-member" and "by-project" (toggleable)
- **Supervisor restriction**: Only supervisors see "by-project" mode
- **Dynamic form**: Changes which field is shown based on view mode
- **Bulk operations**: Add assignment by selecting member/project
- **List operations**: Toggle active/inactive for assignments

### DailyApprovalDashboard
- **Date filtering**: dateFrom and dateTo for filtering
- **Hierarchical grouping**: By date → by member → entries
- **Bulk approval**: Select multiple entries and approve together
- **Per-entry rejection**: Modal with comment field for rejections
- **Recall functionality**: Recall approved entries
- **Status indicators**: Unapproved (yellow), approved (green), rejected (red)

---

## 10. Key Takeaways for Implementation

1. **Modular design**: Each resource has independent list + form components
2. **Refresh pattern**: Page-level refreshKey forces list reload after mutations
3. **Debounced search**: 300ms delay before API calls for performance
4. **Permission gating**: Use `hasPermission()` for UI access control
5. **Type reuse**: Export Row types from lists for form type safety
6. **Error handling**: Show server errors, fall back to generic message
7. **Confirmation dialogs**: Confirm deactivate, but not activate
8. **Modal pattern**: Fixed overlay with escape key support
9. **Pagination UI**: Hide pagination when totalPages === 1
10. **State management**: Keep form state flat (not nested objects)
