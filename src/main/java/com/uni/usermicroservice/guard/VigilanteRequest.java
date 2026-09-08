package com.uni.usermicroservice.guard;

import com.uni.usermicroservice.security.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VigilanteRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 30) String documentNumber,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 30) String phone,
        @PasswordPolicy String initialPassword
) {
}
