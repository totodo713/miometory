package com.worklog.application.password;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Command object for requesting a password reset.
 */
public class PasswordResetRequestCommand {
    @NotBlank
    @Email
    private String email;

    public PasswordResetRequestCommand() {}

    public PasswordResetRequestCommand(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
