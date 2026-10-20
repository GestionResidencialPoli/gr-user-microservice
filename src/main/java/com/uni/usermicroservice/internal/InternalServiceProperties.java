package com.uni.usermicroservice.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "internal.service")
public record InternalServiceProperties(
        @NotBlank(message = "internal.service.token es obligatorio: definelo en la variable de entorno INTERNAL_SERVICE_TOKEN")
        @Size(min = 32, message = "internal.service.token debe tener al menos 32 caracteres")
        String token
) {
}
