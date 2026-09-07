package com.uni.usermicroservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
        "jwt.secret=a-secret-of-at-least-32-characters-long",
        "spring.jpa.show-sql=false"
})
class RefreshTokenSchemaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("gr_user_test")
            .withUsername("gr_user")
            .withPassword("gr_user");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (first_name, last_name, document_number, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                "Test", "User", "DOC-" + email, email, "irrelevant-hash-for-this-test");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    @Test
    void tokenHashMustBeUnique() {
        Long userId = insertUser("unique-hash@example.com");
        jdbcTemplate.update(
                "INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (?, ?, now() + interval '7 days')",
                userId, "same-hash"
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (?, ?, now() + interval '7 days')",
                userId, "same-hash"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAUserCascadesItsRefreshTokens() {
        Long userId = insertUser("cascade@example.com");
        jdbcTemplate.update(
                "INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (?, ?, now() + interval '7 days')",
                userId, "cascade-hash"
        );

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, "cascade-hash"
        );
        assertThat(remaining).isZero();
    }

    @Test
    void expiresAtMustBeAfterCreatedAt() {
        Long userId = insertUser("expiry-check@example.com");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO refresh_tokens (user_id, token_hash, expires_at, created_at) "
                        + "VALUES (?, ?, now() - interval '1 day', now())",
                userId, "expired-before-created"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
