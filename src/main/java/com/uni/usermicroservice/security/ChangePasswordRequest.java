package com.uni.usermicroservice.security;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(@NotBlank String currentPassword, @PasswordPolicy String newPassword) {
}
