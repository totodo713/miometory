import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { RejectionBanner } from "@/components/worklog/RejectionBanner";

describe("RejectionBanner", () => {
  test("should render monthly rejection banner with reason", () => {
    render(<RejectionBanner rejectionReason="Hours don't match project plan" rejectionSource="monthly" />);

    expect(screen.getByText("Monthly Rejection")).toBeInTheDocument();
    expect(screen.getByText("Hours don't match project plan")).toBeInTheDocument();
  });

  test("should render daily rejection banner with reason", () => {
    render(<RejectionBanner rejectionReason="Wrong project assigned" rejectionSource="daily" />);

    expect(screen.getByText("Daily Rejection")).toBeInTheDocument();
    expect(screen.getByText("Wrong project assigned")).toBeInTheDocument();
  });

  test("should display rejected by name when provided", () => {
    render(<RejectionBanner rejectionReason="Fix hours" rejectionSource="monthly" rejectedByName="Manager Name" />);

    expect(screen.getByText("by Manager Name")).toBeInTheDocument();
  });

  test("should display rejected at date when provided", () => {
    render(<RejectionBanner rejectionReason="Fix hours" rejectionSource="monthly" rejectedAt="2026-01-15T10:00:00Z" />);

    // The component formats the date using toLocaleDateString with month: "short", day: "numeric", hour/minute
    const dateElement = screen.getByText((content) => content.includes("Jan") && content.includes("15"));
    expect(dateElement).toBeInTheDocument();
  });

  test("should have alert role for accessibility", () => {
    render(<RejectionBanner rejectionReason="Fix hours" rejectionSource="monthly" />);

    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  test("should not display rejected by when not provided", () => {
    render(<RejectionBanner rejectionReason="Fix hours" rejectionSource="monthly" />);

    expect(screen.queryByText(/^by /)).not.toBeInTheDocument();
  });
});
