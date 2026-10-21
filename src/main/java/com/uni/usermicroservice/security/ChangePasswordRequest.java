package com.uni.usermicroservice.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(@NotBlank @Size(max = 72) String currentPassword, @PasswordPolicy String newPassword) {
}
