package com.uni.usermicroservice.security;

import com.uni.usermicroservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordStorageIT extends AbstractIntegrationTest {

    private static final String SHARED_PASSWORD = "MismaClave1";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private void insertUser(String email, String passwordHash) {
        jdbcTemplate.update(
                "INSERT INTO users (first_name, last_name, document_number, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                "Test", "User", "DOC-" + System.nanoTime(), email, passwordHash);
    }

    @Test
    void everyStoredPasswordHashUsesTheBcryptFormat() {
        List<String> hashes = jdbcTemplate.queryForList("SELECT password_hash FROM users", String.class);

        assertThat(hashes).isNotEmpty();
        assertThat(hashes).allSatisfy(hash ->
                assertThat(hash).matches("^\\$2[aby]\\$[0-9]{2}\\$[A-Za-z0-9./]{53}$"));
    }

    @Test
    void noStoredPasswordHashLooksLikeAPlainTextValue() {
        List<String> hashes = jdbcTemplate.queryForList("SELECT password_hash FROM users", String.class);

        assertThat(hashes).noneMatch(hash -> hash.equalsIgnoreCase("PENDING_ACTIVATION"));
        assertThat(hashes).allSatisfy(hash -> assertThat(hash).hasSize(60));
    }

    @Test
    void twoUsersWithTheSamePasswordGetDifferentHashesBecauseOfTheSalt() {
        String firstHash = passwordEncoder.encode(SHARED_PASSWORD);
        String secondHash = passwordEncoder.encode(SHARED_PASSWORD);

        insertUser("salt-a-" + System.nanoTime() + "@example.com", firstHash);
        insertUser("salt-b-" + System.nanoTime() + "@example.com", secondHash);

        assertThat(firstHash).isNotEqualTo(secondHash);
        assertThat(passwordEncoder.matches(SHARED_PASSWORD, firstHash)).isTrue();
        assertThat(passwordEncoder.matches(SHARED_PASSWORD, secondHash)).isTrue();
    }

    @Test
    void theDatabaseRejectsAPasswordHashThatIsNotBcrypt() {
        assertThatThrownBy(() -> insertUser("plain-" + System.nanoTime() + "@example.com", "ClaveEnTextoPlano1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsTheOldPendingActivationSentinel() {
        assertThatThrownBy(() -> insertUser("sentinel-" + System.nanoTime() + "@example.com", "PENDING_ACTIVATION"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aResidentCreatedWithoutCredentialsStillGetsABcryptHash() {
        Integer withoutBcrypt = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE password_hash !~ '^\\$2[aby]\\$[0-9]{2}\\$[A-Za-z0-9./]{53}$'",
                Integer.class);

        assertThat(withoutBcrypt).isZero();
    }
}
