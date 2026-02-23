"use client";

/**
 * Password Strength Indicator Component
 *
 * Provides real-time visual feedback about password strength as users type.
 *
 * Features:
 * - zxcvbn-ts integration for strength calculation (score 0-4)
 * - 300ms debounce to avoid excessive calculations
 * - Color-coded visual indicator (red/yellow/green)
 * - Strength label (weak/medium/strong)
 * - Feedback messages for improvement suggestions
 * - Accessibility (WCAG 2.1 AA compliant)
 *
 * User Story 3 (P3): View Password Strength Feedback
 * @see specs/005-password-reset-frontend/spec.md
 */

import { useEffect, useState } from "react";
import type { PasswordStrengthResult } from "@/lib/types/password-reset";
import { analyzePasswordStrength } from "@/lib/validation/password";

export interface PasswordStrengthIndicatorProps {
  /** Password to analyze */
  password: string;
  /** Callback when strength result changes */
  onChange?: (result: PasswordStrengthResult) => void;
  /** Show feedback messages (default: true) */
  showFeedback?: boolean;
  /** Custom CSS class for container */
  className?: string;
}

/**
 * PasswordStrengthIndicator component
 *
 * @example
 * ```tsx
 * <PasswordStrengthIndicator
 *   password={newPassword}
 *   onChange={(result) => setPasswordStrength(result)}
 * />
 * ```
 */
export function PasswordStrengthIndicator({
  password,
  onChange,
  showFeedback = true,
  className = "",
}: PasswordStrengthIndicatorProps) {
  const [result, setResult] = useState<PasswordStrengthResult | null>(null);
  const [isCalculating, setIsCalculating] = useState(false);

  /**
   * Debounced password strength calculation
   * Waits 300ms after last keystroke before analyzing
   */
  useEffect(() => {
    // Don't calculate for empty passwords
    if (!password) {
      setResult(null);
      setIsCalculating(false);
      if (onChange) {
        onChange({
          strength: "weak",
          score: 0,
          feedback: [],
          crackTimeDisplay: "即座",
        });
      }
      return;
    }

    setIsCalculating(true);

    // Debounce: Wait 300ms after last input before calculating
    const timeoutId = setTimeout(() => {
      const strengthResult = analyzePasswordStrength(password);
      setResult(strengthResult);
      setIsCalculating(false);

      // Notify parent component
      if (onChange) {
        onChange(strengthResult);
      }
    }, 300);

    // Cleanup: Cancel previous timeout if user types again
    return () => clearTimeout(timeoutId);
  }, [password, onChange]);

  // Don't render anything for empty passwords
  if (!password) {
    return null;
  }

  // Show loading state during calculation
  if (isCalculating || !result) {
    return (
      <div className={`strength-indicator ${className}`}>
        <div className="strength-bar-container">
          <div className="strength-bar strength-calculating" />
        </div>
        <p className="strength-label calculating" aria-live="polite">
          計算中...
        </p>

        <style jsx>{`
					.strength-indicator {
						margin-top: 0.75rem;
					}

					.strength-bar-container {
						height: 6px;
						background-color: #e0e0e0;
						border-radius: 3px;
						overflow: hidden;
					}

					.strength-bar {
						height: 100%;
						transition: width 0.3s ease, background-color 0.3s ease;
					}

					.strength-calculating {
						width: 33%;
						background-color: #bdbdbd;
						animation: pulse 1.5s ease-in-out infinite;
					}

					@keyframes pulse {
						0%,
						100% {
							opacity: 1;
						}
						50% {
							opacity: 0.5;
						}
					}

					.strength-label {
						margin: 0.5rem 0 0;
						font-size: 0.875rem;
						font-weight: 500;
					}

					.calculating {
						color: #757575;
					}
				`}</style>
      </div>
    );
  }

  // Map strength to visual properties
  const strengthConfig = {
    weak: {
      color: "#d32f2f",
      label: "弱い",
      width: "33%",
      bgColor: "#ffebee",
    },
    medium: {
      color: "#f57c00",
      label: "普通",
      width: "66%",
      bgColor: "#fff3e0",
    },
    strong: {
      color: "#388e3c",
      label: "強い",
      width: "100%",
      bgColor: "#e8f5e9",
    },
  };

  const config = strengthConfig[result.strength];

  return (
    <div className={`strength-indicator ${className}`}>
      {/* Visual strength bar */}
      <div className="strength-bar-container" aria-hidden="true">
        <div className="strength-bar" />
      </div>

      {/* Strength label (screen reader friendly) */}
      <output className="strength-label" aria-live="polite" aria-label={`パスワード強度: ${config.label}`}>
        パスワード強度: <span className="strength-value">{config.label}</span>
      </output>

      {/* Feedback messages */}
      {showFeedback && result.feedback.length > 0 && (
        <div className="feedback-container">
          <ul className="feedback-list">
            {result.feedback.map((message) => (
              <li key={message} className="feedback-item">
                {message}
              </li>
            ))}
          </ul>
        </div>
      )}

      <style jsx>{`
				.strength-indicator {
					margin-top: 0.75rem;
				}

				.strength-bar-container {
					height: 6px;
					background-color: #e0e0e0;
					border-radius: 3px;
					overflow: hidden;
				}

				.strength-bar {
					height: 100%;
					width: ${config.width};
					background-color: ${config.color};
					transition:
						width 0.3s ease,
						background-color 0.3s ease;
				}

				.strength-label {
					display: block;
					margin: 0.5rem 0 0;
					font-size: 0.875rem;
					font-weight: 500;
					color: #333;
				}

				.strength-value {
					color: ${config.color};
					font-weight: 600;
				}

				.feedback-container {
					margin-top: 0.5rem;
					padding: 0.75rem;
					background-color: ${config.bgColor};
					border-left: 3px solid ${config.color};
					border-radius: 4px;
				}

				.feedback-list {
					margin: 0;
					padding-left: 1.25rem;
					list-style-type: disc;
				}

				.feedback-item {
					font-size: 0.8125rem;
					color: #424242;
					line-height: 1.5;
				}

				.feedback-item:not(:last-child) {
					margin-bottom: 0.25rem;
				}

				@media (max-width: 480px) {
					.feedback-item {
						font-size: 0.75rem;
					}
				}
			`}</style>
    </div>
  );
}
