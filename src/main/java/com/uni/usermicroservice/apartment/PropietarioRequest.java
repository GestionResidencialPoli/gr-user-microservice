package com.uni.usermicroservice.apartment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PropietarioRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 30) String documentNumber,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 30) String phone
) {
}
