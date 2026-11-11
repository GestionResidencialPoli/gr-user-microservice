package com.uni.usermicroservice.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

        @NotBlank(message = "jwt.encryption-key es obligatorio: definelo en la variable de entorno JWT_ENCRYPTION_KEY")
        @Pattern(
                regexp = "^[A-Za-z0-9_-]{43}$",
                message = "jwt.encryption-key debe ser una clave de 32 bytes en base64url sin relleno (43 caracteres) para cifrar con AES-256-GCM"
        )
        String encryptionKey,

        @Positive(message = "jwt.access-token-expiration-minutes debe ser mayor que cero")
        long accessTokenExpirationMinutes,

        @Positive(message = "jwt.refresh-token-expiration-days debe ser mayor que cero")
        long refreshTokenExpirationDays
) {
}
