import { act, render, screen } from "@testing-library/react";
import { PasswordStrengthIndicator } from "@/components/auth/PasswordStrengthIndicator";
import type { PasswordStrengthResult } from "@/lib/types/password-reset";

// Mock next-intl useTranslations and useLocale
vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => {
    const translations: Record<string, string> = {
      "strength.weak": "弱い",
      "strength.medium": "普通",
      "strength.strong": "強い",
      "strength.label": "パスワード強度",
      "common.loading": "計算中...",
    };
    return translations[key] ?? key;
  },
  useLocale: () => "ja",
}));

// Mock analyzePasswordStrength to control test results
vi.mock("@/lib/validation/password", () => ({
  analyzePasswordStrength: vi.fn(),
}));

import { analyzePasswordStrength } from "@/lib/validation/password";

const mockAnalyze = vi.mocked(analyzePasswordStrength);

describe("PasswordStrengthIndicator", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe("Empty password", () => {
    test("renders nothing when password is empty", () => {
      const { container } = render(<PasswordStrengthIndicator password="" />);
      expect(container.firstChild).toBeNull();
    });

    test("calls onChange with weak defaults for empty password", () => {
      const onChange = vi.fn();
      render(<PasswordStrengthIndicator password="" onChange={onChange} />);
      expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ strength: "weak", score: 0, feedback: [] }));
    });
  });

  describe("Weak password", () => {
    test("renders weak label after debounce", () => {
      mockAnalyze.mockReturnValue({
        strength: "weak",
        score: 1,
        feedback: ["もっと文字を追加してください"],
        crackTimeDisplay: "即座",
      });

      render(<PasswordStrengthIndicator password="abc" />);

      act(() => {
        vi.advanceTimersByTime(300);
      });

      expect(screen.getByText("弱い")).toBeInTheDocument();
    });
  });

  describe("Medium password", () => {
    test("renders medium label after debounce", () => {
      mockAnalyze.mockReturnValue({
        strength: "medium",
        score: 3,
        feedback: [],
        crackTimeDisplay: "数時間",
      });

      render(<PasswordStrengthIndicator password="TestPass1" />);

      act(() => {
        vi.advanceTimersByTime(300);
      });

      expect(screen.getByText("普通")).toBeInTheDocument();
    });
  });

  describe("Strong password", () => {
    test("renders strong label after debounce", () => {
      mockAnalyze.mockReturnValue({
        strength: "strong",
        score: 4,
        feedback: [],
        crackTimeDisplay: "数世紀",
      });

      render(<PasswordStrengthIndicator password="C0mpl3x!P@ssw0rd#2024" />);

      act(() => {
        vi.advanceTimersByTime(300);
      });

      expect(screen.getByText("強い")).toBeInTheDocument();
    });
  });

  describe("onChange callback", () => {
    test("calls onChange with PasswordStrengthResult after debounce", () => {
      const expectedResult: PasswordStrengthResult = {
        strength: "medium",
        score: 2,
        feedback: ["大文字を追加してください"],
        crackTimeDisplay: "数分",
      };
      mockAnalyze.mockReturnValue(expectedResult);

      const onChange = vi.fn();
      render(<PasswordStrengthIndicator password="test1234" onChange={onChange} />);

      act(() => {
        vi.advanceTimersByTime(300);
      });

      expect(onChange).toHaveBeenCalledWith(expectedResult);
    });
  });

  describe("Feedback messages", () => {
    test("renders feedback suggestions when present", () => {
      mockAnalyze.mockReturnValue({
        strength: "weak",
        score: 1,
        feedback: ["大文字を追加してください", "記号を追加してください"],
        crackTimeDisplay: "即座",
      });

      render(<PasswordStrengthIndicator password="abc" showFeedback={true} />);

      act(() => {
        vi.advanceTimersByTime(300);
      });

      expect(screen.getByText("大文字を追加してください")).toBeInTheDocument();
      expect(screen.getByText("記号を追加してください")).toBeInTheDocument();
    });

    test("hides feedback when showFeedback is false", () => {
      mockAnalyze.mockReturnValue({
        strength: "weak",
        score: 1,
        feedback: ["大文字を追加してください"],
        crackTimeDisplay: "即座",
      });

      render(<PasswordStrengthIndicator password="abc" showFeedback={false} />);

      act(() => {
        vi.advanceTimersByTime(300);
      });

      expect(screen.getByText("弱い")).toBeInTheDocument();
      expect(screen.queryByText("大文字を追加してください")).not.toBeInTheDocument();
    });
  });

  describe("ARIA attributes", () => {
    test("strength label has aria-live polite", () => {
      mockAnalyze.mockReturnValue({
        strength: "strong",
        score: 4,
        feedback: [],
        crackTimeDisplay: "数世紀",
      });

      render(<PasswordStrengthIndicator password="C0mpl3x!P@ss" />);

      act(() => {
        vi.advanceTimersByTime(300);
      });

      const output = screen.getByRole("status");
      expect(output).toHaveAttribute("aria-live", "polite");
    });

    test("visual bar has aria-hidden true", () => {
      mockAnalyze.mockReturnValue({
        strength: "strong",
        score: 4,
        feedback: [],
        crackTimeDisplay: "数世紀",
      });

      const { container } = render(<PasswordStrengthIndicator password="C0mpl3x!P@ss" />);

      act(() => {
        vi.advanceTimersByTime(300);
      });

      const barContainer = container.querySelector("[aria-hidden='true']");
      expect(barContainer).toBeInTheDocument();
    });
  });

  describe("Debounce behavior", () => {
    test("does not call analyzePasswordStrength before debounce delay", () => {
      render(<PasswordStrengthIndicator password="test" />);

      act(() => {
        vi.advanceTimersByTime(299);
      });

      expect(mockAnalyze).not.toHaveBeenCalled();
    });

    test("calls analyzePasswordStrength after 300ms debounce", () => {
      mockAnalyze.mockReturnValue({
        strength: "weak",
        score: 1,
        feedback: [],
        crackTimeDisplay: "即座",
      });

      render(<PasswordStrengthIndicator password="test" />);

      act(() => {
        vi.advanceTimersByTime(300);
      });

      expect(mockAnalyze).toHaveBeenCalledWith("test", "ja");
    });
  });

  describe("Loading state", () => {
    test("shows loading indicator before calculation completes", () => {
      render(<PasswordStrengthIndicator password="abc" />);

      // Before debounce completes, should show calculating state
      expect(screen.getByText("計算中...")).toBeInTheDocument();
    });
  });
});
