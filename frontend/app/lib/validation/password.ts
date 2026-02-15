/**
 * Password validation and strength analysis utilities
 *
 * Uses Zod for schema validation and zxcvbn-ts for password strength analysis.
 *
 * Validation Rules:
 * - Email: Valid email format (RFC 5322)
 * - Password: 8-128 characters
 * - Confirm password: Must match new password
 *
 * Password Strength:
 * - zxcvbn score 0-1 = weak (red)
 * - zxcvbn score 2-3 = medium (yellow)
 * - zxcvbn score 4 = strong (green)
 * - Performance: < 10ms per calculation (verified via benchmarking)
 * - Debounce: 300ms to avoid excessive calculations during typing
 *
 * @see specs/005-password-reset-frontend/research.md (Question 1)
 */

import { zxcvbn, zxcvbnOptions } from "@zxcvbn-ts/core";
import * as zxcvbnCommonPackage from "@zxcvbn-ts/language-common";
import * as zxcvbnEnPackage from "@zxcvbn-ts/language-en";
import { z } from "zod";
import type { PasswordStrengthResult, ValidationError } from "../types/password-reset";

// Re-export types for external consumers
export type { PasswordStrengthResult, ValidationError };

/**
 * Initialize zxcvbn with English language dictionary
 *
 * Call this once at application startup or lazy-load when first needed.
 * Initialization adds ~50KB to bundle size.
 */
let isZxcvbnInitialized = false;

function initializeZxcvbn(): void {
  if (isZxcvbnInitialized) {
    return;
  }

  const options = {
    dictionary: {
      ...zxcvbnCommonPackage.dictionary,
      ...zxcvbnEnPackage.dictionary,
    },
    graphs: zxcvbnCommonPackage.adjacencyGraphs,
    translations: zxcvbnEnPackage.translations,
  };

  zxcvbnOptions.setOptions(options);
  isZxcvbnInitialized = true;
}

// Initialize on module load (lazy execution on first import)
if (typeof window !== "undefined") {
  initializeZxcvbn();
}

/**
 * Zod schema for password reset request form
 */
export const passwordResetRequestSchema = z.object({
  email: z
    .string()
    .min(1, { message: "メールアドレスを入力してください" })
    .email({ message: "有効なメールアドレスを入力してください" }),
});

/**
 * Zod schema for password reset confirmation form
 */
export const passwordResetConfirmSchema = z
  .object({
    token: z.string().min(1, { message: "トークンが必要です" }),
    newPassword: z
      .string()
      .min(8, { message: "パスワードは8文字以上で入力してください" })
      .max(128, { message: "パスワードは128文字以内で入力してください" }),
    confirmPassword: z.string().min(1, { message: "確認用パスワードを入力してください" }),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "パスワードが一致しません",
    path: ["confirmPassword"],
  });

/**
 * Validate email address
 *
 * @param email - Email address to validate
 * @returns Validation error or null if valid
 *
 * @example
 * ```ts
 * const error = validateEmail('invalid');
 * if (error) {
 *   console.error(error.message); // "有効なメールアドレスを入力してください"
 * }
 * ```
 */
export function validateEmail(email: string): ValidationError | null {
  const result = passwordResetRequestSchema.safeParse({ email });
  if (result.success) {
    return null;
  }

  const zodError = result.error.errors[0];
  return {
    field: "email",
    message: zodError.message,
    type: zodError.code === "too_small" ? "required" : "format",
  };
}

/**
 * Validate new password and confirm password
 *
 * @param newPassword - New password to set
 * @param confirmPassword - Confirmation password
 * @param token - Password reset token (required but not validated here)
 * @returns Record of validation errors keyed by field name
 *
 * @example
 * ```ts
 * const errors = validatePasswordConfirm('short', 'different', 'token123');
 * if (errors.newPassword) {
 *   console.error(errors.newPassword.message);
 * }
 * if (errors.confirmPassword) {
 *   console.error(errors.confirmPassword.message);
 * }
 * ```
 */
export function validatePasswordConfirm(
  newPassword: string,
  confirmPassword: string,
  token: string,
): Record<string, ValidationError> {
  const result = passwordResetConfirmSchema.safeParse({
    token,
    newPassword,
    confirmPassword,
  });

  if (result.success) {
    return {};
  }

  const errors: Record<string, ValidationError> = {};
  for (const zodError of result.error.errors) {
    const field = zodError.path[0] as string;
    errors[field] = {
      field,
      message: zodError.message,
      type: getErrorType(zodError.code),
    };
  }

  return errors;
}

/**
 * Map Zod error code to ValidationError type
 */
function getErrorType(zodErrorCode: string): ValidationError["type"] {
  switch (zodErrorCode) {
    case "too_small":
      return "length";
    case "too_big":
      return "length";
    case "invalid_string":
      return "format";
    case "custom":
      return "mismatch";
    default:
      return "format";
  }
}

/**
 * Analyze password strength using zxcvbn
 *
 * Performance: < 10ms per calculation (verified via benchmarking)
 * Recommended: Debounce by 300ms to avoid excessive calculations during typing
 *
 * @param password - Password to analyze
 * @returns Password strength result with score, strength category, and feedback
 *
 * @example
 * ```ts
 * const result = analyzePasswordStrength('MyP@ssw0rd123');
 * console.log(result.strength); // "medium"
 * console.log(result.score); // 3
 * console.log(result.feedback); // ["Add more unique words or characters"]
 * ```
 */
export function analyzePasswordStrength(password: string): PasswordStrengthResult {
  // Ensure zxcvbn is initialized
  initializeZxcvbn();

  // Early return for empty/short passwords
  if (password.length === 0) {
    return {
      strength: "weak",
      score: 0,
      feedback: ["短すぎます"],
      crackTimeDisplay: "即座",
    };
  }

  const result = zxcvbn(password);

  // Map zxcvbn score (0-4) to strength category
  let strength: "weak" | "medium" | "strong";
  if (result.score <= 1) {
    strength = "weak";
  } else if (result.score <= 3) {
    strength = "medium";
  } else {
    strength = "strong";
  }

  // Translate zxcvbn feedback to Japanese
  const feedback: string[] = [];
  if (result.feedback.warning) {
    feedback.push(translateFeedback(result.feedback.warning));
  }
  for (const suggestion of result.feedback.suggestions) {
    feedback.push(translateFeedback(suggestion));
  }

  return {
    strength,
    score: result.score,
    feedback,
    crackTimeDisplay: result.crackTimesDisplay.offlineSlowHashing1e4PerSecond || "不明",
  };
}

/**
 * Translate zxcvbn feedback messages to Japanese
 *
 * Maps common English feedback messages to Japanese equivalents.
 * Falls back to original message if no translation available.
 */
function translateFeedback(message: string): string {
  const translations: Record<string, string> = {
    // Warnings
    "This is a top-10 common password": "よく使われるパスワードです",
    "This is a top-100 common password": "よく使われるパスワードです",
    "This is a very common password": "よく使われるパスワードです",
    "This is similar to a commonly used password": "よく使われるパスワードに似ています",
    "A word by itself is easy to guess": "単語だけでは推測されやすいです",
    "Names and surnames by themselves are easy to guess": "名前や苗字だけでは推測されやすいです",
    "Common names and surnames are easy to guess": "一般的な名前は推測されやすいです",
    "Straight rows of keys are easy to guess": "キーボードの並び順です",
    "Short keyboard patterns are easy to guess": "キーボードパターンは推測されやすいです",
    'Repeats like "aaa" are easy to guess': "繰り返しは推測されやすいです",
    'Repeats like "abcabcabc" are only slightly harder to guess than "abc"': "繰り返しパターンは推測されやすいです",
    'Sequences like "abc" or "6543" are easy to guess': "連続した文字は推測されやすいです",
    "Recent years are easy to guess": "最近の年は推測されやすいです",
    "Dates are often easy to guess": "日付は推測されやすいです",

    // Suggestions
    "Use a few words, avoid common phrases": "複数の単語を使い、一般的なフレーズを避けてください",
    "No need for symbols, digits, or uppercase letters": "記号・数字・大文字は必須ではありません",
    "Add another word or two. Uncommon words are better.": "単語を1～2語追加してください。珍しい単語が望ましいです",
    "Use a longer keyboard pattern with more turns": "より長く複雑なキーボードパターンを使用してください",
    "Avoid repeated words and characters": "単語や文字の繰り返しを避けてください",
    "Avoid sequences": "連続した文字を避けてください",
    "Avoid recent years": "最近の年を避けてください",
    "Avoid years that are associated with you": "あなたに関連する年を避けてください",
    "Avoid dates and years that are associated with you": "あなたに関連する日付や年を避けてください",
    "Capitalization doesn't help very much": "大文字化はあまり効果がありません",
    "All-uppercase is almost as easy to guess as all-lowercase": "全て大文字でも推測されやすいです",
    "Reversed words aren't much harder to guess": "逆順の単語も推測されやすいです",
    'Predictable substitutions like "@" instead of "a" don\'t help very much':
      "予測可能な置換（例: a → @）はあまり効果がありません",
  };

  return translations[message] || message;
}

/**
 * Check if password meets minimum strength requirement
 *
 * Recommended: Require at least "medium" strength (score >= 2) for password reset.
 *
 * @param password - Password to check
 * @param minStrength - Minimum required strength ("weak", "medium", or "strong")
 * @returns True if password meets minimum strength
 *
 * @example
 * ```ts
 * const isValid = meetsMinimumStrength('MyP@ssw0rd123', 'medium');
 * if (!isValid) {
 *   alert('パスワードが弱すぎます');
 * }
 * ```
 */
export function meetsMinimumStrength(password: string, minStrength: "weak" | "medium" | "strong"): boolean {
  const result = analyzePasswordStrength(password);

  const strengthOrder = ["weak", "medium", "strong"];
  const passwordStrengthIndex = strengthOrder.indexOf(result.strength);
  const minStrengthIndex = strengthOrder.indexOf(minStrength);

  return passwordStrengthIndex >= minStrengthIndex;
}
