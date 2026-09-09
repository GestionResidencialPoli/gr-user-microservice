package com.uni.usermicroservice.security;

import com.uni.usermicroservice.UserMicroserviceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class JwtSecretRequiredAtStartupIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("gr_user_test")
            .withUsername("gr_user")
            .withPassword("gr_user");

    @Test
    void applicationFailsToStartWithoutAJwtSecret() {
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(
                    UserMicroserviceApplication.class
            )
                    .web(WebApplicationType.NONE)
                    .run(
                            "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                            "--spring.datasource.username=" + POSTGRES.getUsername(),
                            "--spring.datasource.password=" + POSTGRES.getPassword(),
                            "--spring.main.banner-mode=off"
                    )) {
                throw new IllegalStateException("El contexto no debia iniciar sin JWT_SECRET");
            }
        })
                .hasRootCauseInstanceOf(BindValidationException.class)
                .hasStackTraceContaining("jwt.secret debe tener al menos 32 caracteres");
    }
}
