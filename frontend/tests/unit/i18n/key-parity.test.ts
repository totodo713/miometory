import { describe, expect, it } from "vitest";
import en from "../../../messages/en.json";
import ja from "../../../messages/ja.json";

function getKeys(obj: Record<string, unknown>, prefix = ""): string[] {
  const keys: string[] = [];
  for (const [k, v] of Object.entries(obj)) {
    const fullKey = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === "object" && !Array.isArray(v)) {
      keys.push(...getKeys(v as Record<string, unknown>, fullKey));
    } else {
      keys.push(fullKey);
    }
  }
  return keys.sort();
}

describe("i18n key parity", () => {
  const jaKeys = getKeys(ja);
  const enKeys = getKeys(en);

  it("ja.json and en.json should have the same number of keys", () => {
    expect(jaKeys.length).toBe(enKeys.length);
  });

  it("every ja key should exist in en", () => {
    const missingInEn = jaKeys.filter((k) => !enKeys.includes(k));
    expect(missingInEn).toEqual([]);
  });

  it("every en key should exist in ja", () => {
    const missingInJa = enKeys.filter((k) => !jaKeys.includes(k));
    expect(missingInJa).toEqual([]);
  });

  it("no translation value should be empty", () => {
    const emptyJa = jaKeys.filter((k) => {
      const val = k.split(".").reduce((o: unknown, p) => (o as Record<string, unknown>)?.[p], ja);
      return val === "";
    });
    const emptyEn = enKeys.filter((k) => {
      const val = k.split(".").reduce((o: unknown, p) => (o as Record<string, unknown>)?.[p], en);
      return val === "";
    });
    expect(emptyJa).toEqual([]);
    expect(emptyEn).toEqual([]);
  });
});
