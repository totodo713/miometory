import { fireEvent, render, screen } from "@testing-library/react";
import SignupPage from "@/(auth)/signup/page";

describe("Signup page", () => {
  test("shows validation error for empty fields", () => {
    render(<SignupPage />);
    fireEvent.click(screen.getByText(/sign up/i));
    expect(screen.getByRole("alert")).toHaveTextContent(/all fields are required/i);
  });

  test("shows password validation error", () => {
    render(<SignupPage />);
    fireEvent.change(screen.getByLabelText("email"), {
      target: { value: "a@b.com" },
    });
    fireEvent.change(screen.getByLabelText("name"), {
      target: { value: "User" },
    });
    fireEvent.change(screen.getByLabelText("password"), {
      target: { value: "weakpass" },
    });
    fireEvent.click(screen.getByText(/sign up/i));
    expect(screen.getByRole("alert")).toHaveTextContent(/password must be/i);
  });
});
