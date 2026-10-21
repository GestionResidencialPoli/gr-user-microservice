package com.uni.usermicroservice.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmation(
        @NotBlank @Size(max = 200) String token,
        @PasswordPolicy String newPassword
) {
}
