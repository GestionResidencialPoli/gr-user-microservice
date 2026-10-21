package com.uni.usermicroservice.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank @Email @EmailFormat @Size(max = 254) String email
) {
}
