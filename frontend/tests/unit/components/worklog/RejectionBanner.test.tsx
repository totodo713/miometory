import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { RejectionBanner } from "@/components/worklog/RejectionBanner";
import { IntlWrapper } from "../../../helpers/intl";

describe("RejectionBanner", () => {
  test("should render monthly rejection banner with reason", () => {
    render(
      <IntlWrapper>
        <RejectionBanner rejectionReason="Hours don't match project plan" rejectionSource="monthly" />
      </IntlWrapper>,
    );

    expect(screen.getByText("差戻")).toBeInTheDocument();
    expect(screen.getByText("Hours don't match project plan")).toBeInTheDocument();
  });

  test("should render daily rejection banner with reason", () => {
    render(
      <IntlWrapper>
        <RejectionBanner rejectionReason="Wrong project assigned" rejectionSource="daily" />
      </IntlWrapper>,
    );

    expect(screen.getByText("差戻")).toBeInTheDocument();
    expect(screen.getByText("Wrong project assigned")).toBeInTheDocument();
  });

  test("should display rejected by name when provided", () => {
    render(
      <IntlWrapper>
        <RejectionBanner rejectionReason="Fix hours" rejectionSource="monthly" rejectedByName="Manager Name" />
      </IntlWrapper>,
    );

    expect(screen.getByText("差戻者: Manager Name")).toBeInTheDocument();
  });

  test("should display rejected at date when provided", () => {
    render(
      <IntlWrapper>
        <RejectionBanner rejectionReason="Fix hours" rejectionSource="monthly" rejectedAt="2026-01-15T10:00:00Z" />
      </IntlWrapper>,
    );

    // The component formats the date using useFormatter().dateTime with month: "short", day: "numeric", hour/minute (ja locale)
    const dateElement = screen.getByText((content) => content.includes("1月") && content.includes("15"));
    expect(dateElement).toBeInTheDocument();
  });

  test("should have alert role for accessibility", () => {
    render(
      <IntlWrapper>
        <RejectionBanner rejectionReason="Fix hours" rejectionSource="monthly" />
      </IntlWrapper>,
    );

    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  test("should not display rejected by when not provided", () => {
    render(
      <IntlWrapper>
        <RejectionBanner rejectionReason="Fix hours" rejectionSource="monthly" />
      </IntlWrapper>,
    );

    expect(screen.queryByText(/差戻者/)).not.toBeInTheDocument();
  });
});
