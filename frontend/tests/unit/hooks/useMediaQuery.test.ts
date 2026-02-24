import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useMediaQuery } from "@/hooks/useMediaQuery";

describe("useMediaQuery", () => {
  let listeners: Array<(e: { matches: boolean }) => void> = [];

  beforeEach(() => {
    listeners = [];
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        addEventListener: (_: string, fn: (e: { matches: boolean }) => void) => {
          listeners.push(fn);
        },
        removeEventListener: vi.fn(),
      })),
    });
  });

  it("returns false when media query does not match", () => {
    const { result } = renderHook(() => useMediaQuery("(min-width: 768px)"));
    expect(result.current).toBe(false);
  });

  it("updates when media query changes", () => {
    const { result } = renderHook(() => useMediaQuery("(min-width: 768px)"));
    act(() => {
      for (const fn of listeners) fn({ matches: true });
    });
    expect(result.current).toBe(true);
  });
});
