package com.uni.usermicroservice.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank(message = "jwt.secret es obligatorio: definelo en la variable de entorno JWT_SECRET")
        @Size(min = 32, message = "jwt.secret debe tener al menos 32 caracteres para firmar con HMAC-SHA256")
        String secret,

        @Positive(message = "jwt.access-token-expiration-minutes debe ser mayor que cero")
        long accessTokenExpirationMinutes,

        @Positive(message = "jwt.refresh-token-expiration-days debe ser mayor que cero")
        long refreshTokenExpirationDays
) {
}
