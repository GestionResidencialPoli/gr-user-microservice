package com.uni.usermicroservice.internal;

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
class InternalServiceTokenRequiredAtStartupIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("gr_user_test")
            .withUsername("gr_user")
            .withPassword("gr_user");

    @Test
    void applicationFailsToStartWithoutAnInternalServiceToken() {
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(
                    UserMicroserviceApplication.class
            )
                    .web(WebApplicationType.NONE)
                    .run(
                            "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                            "--spring.datasource.username=" + POSTGRES.getUsername(),
                            "--spring.datasource.password=" + POSTGRES.getPassword(),
                            "--spring.main.banner-mode=off",
                            "--jwt.secret=a-secret-of-at-least-32-characters-long"
                    )) {
                throw new IllegalStateException("El contexto no debia iniciar sin INTERNAL_SERVICE_TOKEN");
            }
        })
                .hasRootCauseInstanceOf(BindValidationException.class)
                .hasStackTraceContaining("internal.service.token debe tener al menos 32 caracteres");
    }
}
