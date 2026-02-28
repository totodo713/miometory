import { beforeEach, describe, expect, it, vi } from "vitest";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

vi.mock("@/services/csrf", () => ({
  getCsrfToken: vi.fn().mockReturnValue("test-csrf-token"),
}));

// Mock DOM APIs for Blob download
const mockClick = vi.fn();
const mockCreateObjectURL = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:test-url");
const mockRevokeObjectURL = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});

const mockAppendChild = vi.fn();
const mockRemoveChild = vi.fn();
Object.defineProperty(document.body, "appendChild", { value: mockAppendChild, writable: true });
Object.defineProperty(document.body, "removeChild", { value: mockRemoveChild, writable: true });

// Mock createElement to capture anchor element — capture original before spying to avoid recursion
const originalCreateElement = document.createElement.bind(document);
vi.spyOn(document, "createElement").mockImplementation((tag: string) => {
  if (tag === "a") {
    return { href: "", download: "", click: mockClick } as unknown as HTMLElement;
  }
  return originalCreateElement(tag);
});

import {
  downloadMemberCsvTemplate,
  dryRunMemberCsv,
  executeMemberCsvImport,
  type MemberCsvDryRunResult,
} from "@/services/memberCsvService";

beforeEach(() => {
  vi.clearAllMocks();
});

describe("downloadMemberCsvTemplate", () => {
  it("sends GET request and triggers blob download", async () => {
    const mockBlob = new Blob(["test"], { type: "text/csv" });
    mockFetch.mockResolvedValue({
      ok: true,
      blob: () => Promise.resolve(mockBlob),
    });

    await downloadMemberCsvTemplate();

    expect(mockFetch).toHaveBeenCalledWith("http://localhost:8080/api/v1/admin/members/csv/template", {
      credentials: "include",
    });
    expect(mockCreateObjectURL).toHaveBeenCalledWith(mockBlob);
    expect(mockClick).toHaveBeenCalled();
    expect(mockRevokeObjectURL).toHaveBeenCalledWith("blob:test-url");
  });

  it("throws error when response is not ok", async () => {
    mockFetch.mockResolvedValue({ ok: false });

    await expect(downloadMemberCsvTemplate()).rejects.toThrow("Failed to download template");
  });
});

describe("dryRunMemberCsv", () => {
  const mockFile = new File(["csv content"], "test.csv", { type: "text/csv" });
  const mockOrgId = "org-123";

  it("sends POST with FormData and CSRF token", async () => {
    const mockResult: MemberCsvDryRunResult = {
      sessionId: "session-1",
      totalRows: 3,
      validRows: 2,
      errorRows: 1,
      rows: [
        { rowNumber: 1, email: "a@test.com", displayName: "A", status: "VALID", errors: [] },
        { rowNumber: 2, email: "b@test.com", displayName: "B", status: "VALID", errors: [] },
        { rowNumber: 3, email: "invalid", displayName: "C", status: "ERROR", errors: ["email: Invalid email"] },
      ],
    };
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockResult),
    });

    const result = await dryRunMemberCsv(mockFile, mockOrgId);

    expect(result.sessionId).toBe("session-1");
    expect(result.totalRows).toBe(3);
    expect(result.validRows).toBe(2);

    const call = mockFetch.mock.calls[0];
    expect(call[0]).toBe("http://localhost:8080/api/v1/admin/members/csv/dry-run");
    expect(call[1].method).toBe("POST");
    expect(call[1].credentials).toBe("include");
    expect(call[1].headers["X-XSRF-TOKEN"]).toBe("test-csrf-token");

    const formData = call[1].body as FormData;
    expect(formData.get("file")).toBe(mockFile);
    expect(formData.get("organizationId")).toBe(mockOrgId);
  });

  it("throws with server error message on failure", async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      json: () => Promise.resolve({ message: "File too large" }),
    });

    await expect(dryRunMemberCsv(mockFile, mockOrgId)).rejects.toThrow("File too large");
  });

  it("throws fallback message when error JSON parsing fails", async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      json: () => Promise.reject(new Error("parse error")),
    });

    await expect(dryRunMemberCsv(mockFile, mockOrgId)).rejects.toThrow("Dry run failed");
  });
});

describe("executeMemberCsvImport", () => {
  it("sends POST with encoded sessionId and triggers blob download", async () => {
    const mockBlob = new Blob(["result csv"], { type: "text/csv" });
    mockFetch.mockResolvedValue({
      ok: true,
      blob: () => Promise.resolve(mockBlob),
    });

    await executeMemberCsvImport("session-abc-123");

    const call = mockFetch.mock.calls[0];
    expect(call[0]).toBe("http://localhost:8080/api/v1/admin/members/csv/import/session-abc-123");
    expect(call[1].method).toBe("POST");
    expect(call[1].credentials).toBe("include");
    expect(call[1].headers["X-XSRF-TOKEN"]).toBe("test-csrf-token");

    expect(mockCreateObjectURL).toHaveBeenCalledWith(mockBlob);
    expect(mockClick).toHaveBeenCalled();
    expect(mockRevokeObjectURL).toHaveBeenCalledWith("blob:test-url");
  });

  it("throws with server error message on failure", async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      json: () => Promise.resolve({ message: "Session expired" }),
    });

    await expect(executeMemberCsvImport("session-abc")).rejects.toThrow("Session expired");
  });
});
