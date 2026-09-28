package com.uni.usermicroservice.guard;

import com.uni.usermicroservice.security.DocumentNumberFormat;
import com.uni.usermicroservice.security.EmailFormat;
import com.uni.usermicroservice.security.PasswordPolicy;
import com.uni.usermicroservice.security.PersonName;
import com.uni.usermicroservice.security.PhoneFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VigilanteRequest(
        @NotBlank @Size(max = 100) @PersonName String firstName,
        @NotBlank @Size(max = 100) @PersonName String lastName,
        @NotBlank @Size(max = 30) @DocumentNumberFormat String documentNumber,
        @NotBlank @Email @EmailFormat @Size(max = 254) String email,
        @Size(max = 30) @PhoneFormat String phone,
        @PasswordPolicy String initialPassword
) {
}
