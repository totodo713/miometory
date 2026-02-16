import {
  checkRateLimit,
  clearAttempts,
  getMinutesUntilReset,
  recordAttempt,
  setupStorageListener,
} from "@/lib/utils/rate-limit";

const STORAGE_KEY = "password_reset_rate_limit";

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-02-17T10:00:00.000Z"));
  localStorage.clear();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("checkRateLimit", () => {
  test("returns isAllowed true when no attempts recorded", () => {
    const state = checkRateLimit();
    expect(state.isAllowed).toBe(true);
    expect(state.remainingAttempts).toBe(3);
    expect(state.resetTime).toBeNull();
  });

  test("returns isAllowed true when under limit (< 3 attempts)", () => {
    const now = Date.now();
    localStorage.setItem(STORAGE_KEY, JSON.stringify([now - 1000, now - 2000]));

    const state = checkRateLimit();
    expect(state.isAllowed).toBe(true);
    expect(state.remainingAttempts).toBe(1);
  });

  test("returns isAllowed false when at limit (>= 3 attempts within window)", () => {
    const now = Date.now();
    localStorage.setItem(STORAGE_KEY, JSON.stringify([now - 1000, now - 2000, now - 3000]));

    const state = checkRateLimit();
    expect(state.isAllowed).toBe(false);
    expect(state.remainingAttempts).toBe(0);
    expect(state.resetTime).not.toBeNull();
  });

  test("correctly counts remainingAttempts", () => {
    const now = Date.now();

    // 0 attempts → 3 remaining
    expect(checkRateLimit().remainingAttempts).toBe(3);

    // 1 attempt → 2 remaining
    localStorage.setItem(STORAGE_KEY, JSON.stringify([now - 1000]));
    expect(checkRateLimit().remainingAttempts).toBe(2);

    // 2 attempts → 1 remaining
    localStorage.setItem(STORAGE_KEY, JSON.stringify([now - 1000, now - 2000]));
    expect(checkRateLimit().remainingAttempts).toBe(1);

    // 3 attempts → 0 remaining
    localStorage.setItem(STORAGE_KEY, JSON.stringify([now - 1000, now - 2000, now - 3000]));
    expect(checkRateLimit().remainingAttempts).toBe(0);
  });

  test("sets resetTime when rate limited", () => {
    const now = Date.now();
    const oldest = now - 60000; // 1 minute ago
    localStorage.setItem(STORAGE_KEY, JSON.stringify([oldest, now - 30000, now - 10000]));

    const state = checkRateLimit();
    expect(state.isAllowed).toBe(false);
    // resetTime = oldest attempt + 5 min window
    expect(state.resetTime).toBe(oldest + 5 * 60 * 1000);
  });

  test("cleans up expired attempts older than 5 minutes", () => {
    const now = Date.now();
    const expiredTimestamp = now - 5 * 60 * 1000 - 1; // Just over 5 minutes ago
    localStorage.setItem(STORAGE_KEY, JSON.stringify([expiredTimestamp, now - 1000]));

    const state = checkRateLimit();
    expect(state.isAllowed).toBe(true);
    expect(state.remainingAttempts).toBe(2);
    expect(state.attempts).toHaveLength(1);
  });

  test("ignores future timestamps (clock adjustment detection)", () => {
    const now = Date.now();
    const futureTimestamp = now + 60000; // 1 minute in the future
    localStorage.setItem(STORAGE_KEY, JSON.stringify([futureTimestamp, now - 1000]));

    const state = checkRateLimit();
    // Future timestamp is filtered out, only 1 valid attempt
    expect(state.attempts).toHaveLength(1);
    expect(state.remainingAttempts).toBe(2);
  });
});

describe("recordAttempt", () => {
  test("adds current timestamp to localStorage", () => {
    recordAttempt();

    const raw = localStorage.getItem(STORAGE_KEY);
    expect(raw).not.toBeNull();
    const data = JSON.parse(raw as string);
    expect(data).toHaveLength(1);
    expect(data[0]).toBe(Date.now());
  });

  test("appends to existing attempts", () => {
    const now = Date.now();
    localStorage.setItem(STORAGE_KEY, JSON.stringify([now - 1000]));

    recordAttempt();

    const raw = localStorage.getItem(STORAGE_KEY);
    expect(raw).not.toBeNull();
    const data = JSON.parse(raw as string);
    expect(data).toHaveLength(2);
  });

  test("stores under correct localStorage key", () => {
    recordAttempt();
    expect(localStorage.getItem(STORAGE_KEY)).not.toBeNull();
    expect(localStorage.getItem("wrong_key")).toBeNull();
  });
});

describe("getMinutesUntilReset", () => {
  test("returns correct minutes rounded up", () => {
    const now = Date.now();
    // 3.5 minutes from now → 4 minutes (rounded up)
    expect(getMinutesUntilReset(now + 3.5 * 60 * 1000)).toBe(4);
  });

  test("returns 0 for time in the past", () => {
    const now = Date.now();
    expect(getMinutesUntilReset(now - 60000)).toBe(0);
  });

  test("returns 0 when resetTime equals current time", () => {
    const now = Date.now();
    expect(getMinutesUntilReset(now)).toBe(0);
  });

  test("returns 1 for exactly 1 minute from now", () => {
    const now = Date.now();
    expect(getMinutesUntilReset(now + 60000)).toBe(1);
  });

  test("returns 5 for exactly 5 minutes from now", () => {
    const now = Date.now();
    expect(getMinutesUntilReset(now + 5 * 60 * 1000)).toBe(5);
  });

  test("rounds up partial minutes", () => {
    const now = Date.now();
    // 1ms over 2 minutes → rounds up to 3
    expect(getMinutesUntilReset(now + 2 * 60 * 1000 + 1)).toBe(3);
  });
});

describe("clearAttempts", () => {
  test("removes rate limit entry from localStorage", () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([Date.now()]));
    expect(localStorage.getItem(STORAGE_KEY)).not.toBeNull();

    clearAttempts();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  test("does not affect other localStorage entries", () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([Date.now()]));
    localStorage.setItem("other_key", "value");

    clearAttempts();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem("other_key")).toBe("value");
  });
});

describe("setupStorageListener", () => {
  test("returns a cleanup function", () => {
    const cleanup = setupStorageListener(vi.fn());
    expect(typeof cleanup).toBe("function");
    cleanup();
  });

  test("calls callback when storage event fires for rate limit key", () => {
    const callback = vi.fn();
    const cleanup = setupStorageListener(callback);

    window.dispatchEvent(
      new StorageEvent("storage", {
        key: STORAGE_KEY,
        newValue: JSON.stringify([Date.now()]),
      }),
    );

    expect(callback).toHaveBeenCalledTimes(1);
    cleanup();
  });

  test("does not call callback for unrelated storage events", () => {
    const callback = vi.fn();
    const cleanup = setupStorageListener(callback);

    window.dispatchEvent(
      new StorageEvent("storage", {
        key: "other_key",
        newValue: "some-value",
      }),
    );

    expect(callback).not.toHaveBeenCalled();
    cleanup();
  });

  test("cleanup removes event listener", () => {
    const callback = vi.fn();
    const cleanup = setupStorageListener(callback);
    cleanup();

    window.dispatchEvent(
      new StorageEvent("storage", {
        key: STORAGE_KEY,
        newValue: JSON.stringify([Date.now()]),
      }),
    );

    expect(callback).not.toHaveBeenCalled();
  });
});
