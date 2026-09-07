package com.uni.usermicroservice.security;

import com.uni.usermicroservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenSchemaIntegrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (first_name, last_name, email, password_hash) VALUES (?, ?, ?, ?)",
                "Test", "User", email, "irrelevant-hash-for-this-test");
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
