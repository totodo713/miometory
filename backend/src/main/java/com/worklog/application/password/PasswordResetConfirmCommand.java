package com.worklog.application.password;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Command object for confirming a password reset.
 */
public class PasswordResetConfirmCommand {
    @NotBlank private String token;

    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") private String newPassword;

    public PasswordResetConfirmCommand() {}

    public PasswordResetConfirmCommand(String token, String newPassword) {
        this.token = token;
        this.newPassword = newPassword;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
