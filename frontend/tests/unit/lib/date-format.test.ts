import { describe, expect, it } from "vitest";
import { formatDateJapanese } from "@/lib/date-format";

describe("formatDateJapanese", () => {
  it("formats a standard date with year, month, day, and weekday", () => {
    const result = formatDateJapanese("2026-02-21");
    expect(result).toContain("2026");
    expect(result).toContain("2");
    expect(result).toContain("21");
    // Should contain weekday in parentheses — Saturday
    expect(result).toMatch(/\(.\)/);
  });

  it("formats January 1st correctly", () => {
    const result = formatDateJapanese("2026-01-01");
    expect(result).toContain("2026");
    expect(result).toContain("1");
    // Jan 1 2026 is Thursday (木)
    expect(result).toMatch(/\(木\)/);
  });

  it("formats December 31st correctly", () => {
    const result = formatDateJapanese("2026-12-31");
    expect(result).toContain("12");
    expect(result).toContain("31");
    // Dec 31 2026 is Thursday (木)
    expect(result).toMatch(/\(木\)/);
  });

  it("formats leap year date correctly", () => {
    const result = formatDateJapanese("2028-02-29");
    expect(result).toContain("2028");
    expect(result).toContain("2");
    expect(result).toContain("29");
    // Feb 29 2028 is Tuesday (火)
    expect(result).toMatch(/\(火\)/);
  });

  it("includes correct weekday for a known Monday", () => {
    // 2026-02-16 is Monday
    const result = formatDateJapanese("2026-02-16");
    expect(result).toMatch(/\(月\)/);
  });

  it("includes correct weekday for a known Sunday", () => {
    // 2026-02-22 is Sunday
    const result = formatDateJapanese("2026-02-22");
    expect(result).toMatch(/\(日\)/);
  });
});
