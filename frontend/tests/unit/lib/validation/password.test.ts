import {
  analyzePasswordStrength,
  meetsMinimumStrength,
  passwordResetConfirmSchema,
  passwordResetRequestSchema,
  validateEmail,
  validatePasswordConfirm,
} from "@/lib/validation/password";

describe("validateEmail", () => {
  test("returns null for valid standard email", () => {
    expect(validateEmail("user@example.com")).toBeNull();
  });

  test("returns null for valid email with subdomain", () => {
    expect(validateEmail("user@mail.example.co.jp")).toBeNull();
  });

  test("returns error with required type for empty string", () => {
    const error = validateEmail("");
    expect(error).not.toBeNull();
    expect(error?.field).toBe("email");
    expect(error?.message).toBe("メールアドレスを入力してください");
    expect(error?.type).toBe("required");
  });

  test("returns error for email missing @ symbol", () => {
    const error = validateEmail("userexample.com");
    expect(error).not.toBeNull();
    expect(error?.message).toBe("有効なメールアドレスを入力してください");
    expect(error?.type).toBe("format");
  });

  test("returns error for email without domain", () => {
    const error = validateEmail("user@");
    expect(error).not.toBeNull();
    expect(error?.message).toBe("有効なメールアドレスを入力してください");
  });
});

describe("passwordResetRequestSchema", () => {
  test("parses valid email input", () => {
    const result = passwordResetRequestSchema.safeParse({ email: "test@example.com" });
    expect(result.success).toBe(true);
  });

  test("rejects invalid email format", () => {
    const result = passwordResetRequestSchema.safeParse({ email: "not-an-email" });
    expect(result.success).toBe(false);
  });

  test("rejects empty email with Japanese message", () => {
    const result = passwordResetRequestSchema.safeParse({ email: "" });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.errors[0].message).toBe("メールアドレスを入力してください");
    }
  });
});

describe("passwordResetConfirmSchema", () => {
  const validInput = {
    token: "valid-token",
    newPassword: "ValidPass1",
    confirmPassword: "ValidPass1",
  };

  test("parses valid input", () => {
    const result = passwordResetConfirmSchema.safeParse(validInput);
    expect(result.success).toBe(true);
  });

  test("rejects password shorter than 8 characters", () => {
    const result = passwordResetConfirmSchema.safeParse({
      ...validInput,
      newPassword: "short1",
      confirmPassword: "short1",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.errors.map((e) => e.message);
      expect(messages).toContain("パスワードは8文字以上で入力してください");
    }
  });

  test("rejects password longer than 128 characters", () => {
    const longPassword = "A".repeat(129);
    const result = passwordResetConfirmSchema.safeParse({
      ...validInput,
      newPassword: longPassword,
      confirmPassword: longPassword,
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.errors.map((e) => e.message);
      expect(messages).toContain("パスワードは128文字以内で入力してください");
    }
  });

  test("rejects mismatched passwords", () => {
    const result = passwordResetConfirmSchema.safeParse({
      ...validInput,
      newPassword: "ValidPass1",
      confirmPassword: "DifferentPass1",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.errors.map((e) => e.message);
      expect(messages).toContain("パスワードが一致しません");
    }
  });

  test("rejects empty token", () => {
    const result = passwordResetConfirmSchema.safeParse({
      ...validInput,
      token: "",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.errors.map((e) => e.message);
      expect(messages).toContain("トークンが必要です");
    }
  });

  test("rejects empty confirm password", () => {
    const result = passwordResetConfirmSchema.safeParse({
      ...validInput,
      confirmPassword: "",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.errors.map((e) => e.message);
      expect(messages).toContain("確認用パスワードを入力してください");
    }
  });
});

describe("analyzePasswordStrength", () => {
  test("returns weak with score 0 for empty password", () => {
    const result = analyzePasswordStrength("");
    expect(result.strength).toBe("weak");
    expect(result.score).toBe(0);
    expect(result.feedback).toContain("短すぎます");
    expect(result.crackTimeDisplay).toBe("即座");
  });

  test("returns weak for common password (score 0-1)", () => {
    const result = analyzePasswordStrength("password");
    expect(result.strength).toBe("weak");
    expect(result.score).toBeLessThanOrEqual(1);
  });

  test("returns result matching PasswordStrengthResult structure", () => {
    const result = analyzePasswordStrength("TestPassword123!");
    expect(result).toHaveProperty("strength");
    expect(result).toHaveProperty("score");
    expect(result).toHaveProperty("feedback");
    expect(result).toHaveProperty("crackTimeDisplay");
    expect(["weak", "medium", "strong"]).toContain(result.strength);
    expect(result.score).toBeGreaterThanOrEqual(0);
    expect(result.score).toBeLessThanOrEqual(4);
    expect(Array.isArray(result.feedback)).toBe(true);
    expect(typeof result.crackTimeDisplay).toBe("string");
  });

  test("scores higher for more complex passwords", () => {
    const weakResult = analyzePasswordStrength("abc");
    const strongResult = analyzePasswordStrength("Xk9#mWp$L2vQn&R4tY7zBc");
    expect(strongResult.score).toBeGreaterThan(weakResult.score);
  });

  test("returns strong for highly complex password (score 4)", () => {
    const result = analyzePasswordStrength("Xk9#mWp$L2vQn&R4tY7zBc");
    expect(result.strength).toBe("strong");
    expect(result.score).toBe(4);
  });
});

describe("analyzePasswordStrength - FR-006 character type enforcement", () => {
  // FR-006: Character type diversity enforcement via zxcvbn scoring.
  // The zod schema only validates length (8-128) and match.
  // Character diversity is enforced through zxcvbn strength scoring.

  test("password without uppercase is not strong", () => {
    // "abcdefgh1" — no uppercase letter
    const result = analyzePasswordStrength("abcdefgh1");
    expect(result.strength).not.toBe("strong");
  });

  test("password without lowercase is not strong", () => {
    // "ABCDEFGH1" — no lowercase letter
    const result = analyzePasswordStrength("ABCDEFGH1");
    expect(result.strength).not.toBe("strong");
  });

  test("password without digit is not strong", () => {
    // "Abcdefgh" — no digit
    const result = analyzePasswordStrength("Abcdefgh");
    expect(result.strength).not.toBe("strong");
  });

  test("password with all character types at minimum length is accepted by schema", () => {
    // "Abcdefg1" — uppercase, lowercase, digit, exactly 8 chars
    const schemaResult = passwordResetConfirmSchema.safeParse({
      token: "test-token",
      newPassword: "Abcdefg1",
      confirmPassword: "Abcdefg1",
    });
    expect(schemaResult.success).toBe(true);
  });
});

describe("meetsMinimumStrength", () => {
  test("returns true when password meets weak threshold", () => {
    // Any password meets weak threshold since weak is the lowest
    expect(meetsMinimumStrength("password", "weak")).toBe(true);
  });

  test("returns false when weak password checked against medium threshold", () => {
    expect(meetsMinimumStrength("abc", "medium")).toBe(false);
  });

  test("returns true when strong password meets medium threshold", () => {
    expect(meetsMinimumStrength("Xk9#mWp$L2vQn&R4tY7zBc", "medium")).toBe(true);
  });

  test("returns true when strong password meets strong threshold", () => {
    expect(meetsMinimumStrength("Xk9#mWp$L2vQn&R4tY7zBc", "strong")).toBe(true);
  });

  test("returns false when weak password checked against strong threshold", () => {
    expect(meetsMinimumStrength("password", "strong")).toBe(false);
  });
});

describe("validatePasswordConfirm", () => {
  const validToken = "test-token";

  test("returns empty object when passwords match and are valid", () => {
    const errors = validatePasswordConfirm("ValidPass1", "ValidPass1", validToken);
    expect(errors).toEqual({});
  });

  test("returns mismatch error when passwords do not match", () => {
    const errors = validatePasswordConfirm("ValidPass1", "DifferentPass1", validToken);
    expect(errors.confirmPassword).toBeDefined();
    expect(errors.confirmPassword.type).toBe("mismatch");
    expect(errors.confirmPassword.message).toBe("パスワードが一致しません");
  });

  test("returns error when confirmPassword is empty", () => {
    const errors = validatePasswordConfirm("ValidPass1", "", validToken);
    expect(errors.confirmPassword).toBeDefined();
    expect(errors.confirmPassword.message).toBe("パスワードが一致しません");
    expect(errors.confirmPassword.type).toBe("mismatch");
  });

  test("returns length error when password is too short", () => {
    const errors = validatePasswordConfirm("short", "short", validToken);
    expect(errors.newPassword).toBeDefined();
    expect(errors.newPassword.type).toBe("length");
  });

  test("returns token error when token is empty", () => {
    const errors = validatePasswordConfirm("ValidPass1", "ValidPass1", "");
    expect(errors.token).toBeDefined();
  });
});
