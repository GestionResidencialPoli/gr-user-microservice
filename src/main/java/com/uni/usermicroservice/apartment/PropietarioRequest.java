package com.uni.usermicroservice.apartment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PropietarioRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String documentNumber,
        @NotBlank @Email String email,
        String phone
) {
}
