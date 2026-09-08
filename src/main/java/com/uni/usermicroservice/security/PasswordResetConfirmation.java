package com.uni.usermicroservice.security;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirmation(
        @NotBlank String token,
        @PasswordPolicy String newPassword
) {
}
