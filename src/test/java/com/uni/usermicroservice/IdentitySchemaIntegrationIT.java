package com.uni.usermicroservice;

import org.flywaydb.core.Flyway;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdentitySchemaIntegrationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("gr_user_test")
            .withUsername("gr_user")
            .withPassword("gr_user");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Environment environment;

    @Test
    @Order(1)
    void flywayCreatesTheCompleteIdentitySchemaFromAnEmptyDatabase() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public'",
                String.class
        );

        assertThat(tables).contains(
                "flyway_schema_history",
                "roles",
                "users",
                "user_roles",
                "apartments",
                "owners",
                "tenants"
        );
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("1");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM roles", Integer.class))
                .isEqualTo(3);
    }

    @Test
    @Order(2)
    void databaseEnforcesApartmentTowerAndNumberUniqueness() {
        Integer constraintCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_constraint "
                        + "WHERE conname = 'uk_apartments_torre_numero' AND contype = 'u'",
                Integer.class
        );
        String definition = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE conname = 'uk_apartments_torre_numero'",
                String.class
        );

        assertThat(constraintCount).isEqualTo(1);
        assertThat(definition).isEqualTo("UNIQUE (torre, numero)");

        jdbcTemplate.update(
                "INSERT INTO apartments (torre, numero, piso) VALUES (?, ?, ?)",
                "A", "101", 1
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO apartments (torre, numero, piso) VALUES (?, ?, ?)",
                "A", "101", 1
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Order(3)
    void hibernateIsConfiguredToValidateOnly() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("validate");
        assertThat(environment.getProperty("spring.sql.init.mode"))
                .isEqualTo("never");
    }

    @Test
    @Order(4)
    void schemaMismatchPreventsApplicationStartup() {
        jdbcTemplate.execute("ALTER TABLE apartments DROP COLUMN piso");

        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(
                    UserMicroserviceApplication.class
            )
                    .web(WebApplicationType.NONE)
                    .run(
                            "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                            "--spring.datasource.username=" + POSTGRES.getUsername(),
                            "--spring.datasource.password=" + POSTGRES.getPassword(),
                            "--spring.flyway.enabled=false",
                            "--spring.jpa.hibernate.ddl-auto=validate",
                            "--spring.main.banner-mode=off"
                    )) {
                // El contexto no debe llegar a iniciar.
            }
        })
                .hasRootCauseInstanceOf(SchemaManagementException.class)
                .hasStackTraceContaining("missing column [piso] in table [apartments]");
    }
}
