package com.uni.usermicroservice.security;

import com.uni.usermicroservice.UserMicroserviceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = UserMicroserviceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=a-secret-of-at-least-32-characters-long",
                "jwt.access-token-expiration-minutes=15",
                "jwt.refresh-token-expiration-days=7",
                "app.cors.allowed-origins=http://localhost:3000"
        }
)
class PasswordResetAcceptanceCriteriaIT {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String REQUEST_ENDPOINT = "/api/v1/auth/password-reset";
    private static final String CONFIRM_ENDPOINT = "/api/v1/auth/password-reset/confirm";
    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";
    private static final String VALID_NEW_PASSWORD = "NuevaClave1";
    private static final String ORIGINAL_PASSWORD = "ClaveOriginal1";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("gr_user_test")
            .withUsername("gr_user")
            .withPassword("gr_user");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private Long createUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (first_name, last_name, document_number, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                "Test", "User", "DOC-" + System.nanoTime(), email, passwordEncoder.encode(ORIGINAL_PASSWORD));
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private String fetchCsrfToken() {
        var result = client.get().uri("/api/v1/apartamentos").exchange().returnResult(Void.class);
        return result.getResponseCookies().getFirst(CSRF_COOKIE).getValue();
    }

    private void requestReset(String email, String csrf) {
        client.post().uri(REQUEST_ENDPOINT)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new PasswordResetRequest(email))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.ACCEPTED);
    }

    private String pendingTokenHashOf(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT token_hash FROM password_reset_tokens WHERE user_id = ? AND used_at IS NULL",
                String.class, userId);
    }

    private String rawTokenFor(Long userId) {
        String rawToken = "raw-token-" + System.nanoTime();
        jdbcTemplate.update(
                "UPDATE password_reset_tokens SET token_hash = ? WHERE user_id = ? AND used_at IS NULL",
                AuthTokenService.hash(rawToken), userId);
        return rawToken;
    }

    private String passwordHashOf(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, userId);
    }

    @Test
    void issuesASingleUseTokenValidFor30MinutesForARegisteredEmail() {
        String email = "reset-ok-" + System.nanoTime() + "@example.com";
        Long userId = createUser(email);
        String csrf = fetchCsrfToken();

        requestReset(email, csrf);

        Integer tokens = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM password_reset_tokens WHERE user_id = ?", Integer.class, userId);
        assertThat(tokens).isEqualTo(1);

        assertThat(pendingTokenHashOf(userId)).isNotBlank().hasSize(64);

        Instant expiresAt = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM password_reset_tokens WHERE user_id = ?", Instant.class, userId);
        long minutes = ChronoUnit.MINUTES.between(Instant.now(), expiresAt);
        assertThat(minutes).isBetween(28L, 30L);
    }

    @Test
    void storesTheHashOfTheTokenAndNeverTheRawValue() {
        String email = "reset-hash-" + System.nanoTime() + "@example.com";
        Long userId = createUser(email);
        String csrf = fetchCsrfToken();

        requestReset(email, csrf);

        String storedHash = pendingTokenHashOf(userId);
        assertThat(storedHash).matches("^[0-9a-f]{64}$");
    }

    @Test
    void answersTheSameForAnUnregisteredEmailSoItDoesNotRevealWhoExists() {
        String registered = "reset-exists-" + System.nanoTime() + "@example.com";
        createUser(registered);
        String csrf = fetchCsrfToken();

        var registeredResult = client.post().uri(REQUEST_ENDPOINT)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new PasswordResetRequest(registered))
                .exchange()
                .returnResult(String.class);

        var unknownResult = client.post().uri(REQUEST_ENDPOINT)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new PasswordResetRequest("no-existe-" + System.nanoTime() + "@example.com"))
                .exchange()
                .returnResult(String.class);

        assertThat(unknownResult.getStatus()).isEqualTo(registeredResult.getStatus());
        assertThat(unknownResult.getResponseBody()).isEqualTo(registeredResult.getResponseBody());

        Integer tokensForUnknown = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM password_reset_tokens t JOIN users u ON u.id = t.user_id WHERE u.email LIKE 'no-existe-%'",
                Integer.class);
        assertThat(tokensForUnknown).isZero();
    }

    @Test
    void consumesTheTokenAndUpdatesThePasswordWhenItMeetsThePolicy() {
        String email = "reset-confirm-" + System.nanoTime() + "@example.com";
        Long userId = createUser(email);
        String csrf = fetchCsrfToken();
        requestReset(email, csrf);
        String rawToken = rawTokenFor(userId);
        String previousHash = passwordHashOf(userId);

        client.post().uri(CONFIRM_ENDPOINT)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new PasswordResetConfirmation(rawToken, VALID_NEW_PASSWORD))
                .exchange()
                .expectStatus().isNoContent();

        String newHash = passwordHashOf(userId);
        assertThat(newHash).isNotEqualTo(previousHash).startsWith("$2");
        assertThat(passwordEncoder.matches(VALID_NEW_PASSWORD, newHash)).isTrue();

        Boolean consumed = jdbcTemplate.queryForObject(
                "SELECT used_at IS NOT NULL FROM password_reset_tokens WHERE user_id = ?", Boolean.class, userId);
        assertThat(consumed).isTrue();

        client.post().uri(LOGIN_ENDPOINT)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new LoginRequest(email, VALID_NEW_PASSWORD))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void rejectsAnAlreadyUsedTokenAndLeavesThePasswordUnchanged() {
        String email = "reset-reuse-" + System.nanoTime() + "@example.com";
        Long userId = createUser(email);
        String csrf = fetchCsrfToken();
        requestReset(email, csrf);
        String rawToken = rawTokenFor(userId);

        client.post().uri(CONFIRM_ENDPOINT)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new PasswordResetConfirmation(rawToken, VALID_NEW_PASSWORD))
                .exchange()
                .expectStatus().isNoContent();

        String hashAfterFirstUse = passwordHashOf(userId);

        client.post().uri(CONFIRM_ENDPOINT)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new PasswordResetConfirmation(rawToken, "OtraClave2"))
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(passwordHashOf(userId)).isEqualTo(hashAfterFirstUse);
    }

    @Test
    void rejectsAnExpiredTokenSayingItExpired() {
        String email = "reset-expired-" + System.nanoTime() + "@example.com";
        Long userId = createUser(email);
        String csrf = fetchCsrfToken();
        requestReset(email, csrf);
        String rawToken = rawTokenFor(userId);
        String previousHash = passwordHashOf(userId);

        jdbcTemplate.update(
                "UPDATE password_reset_tokens "
                        + "SET created_at = now() - interval '2 hours', expires_at = now() - interval '90 minutes' "
                        + "WHERE user_id = ?",
                userId);

        client.post().uri(CONFIRM_ENDPOINT)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new PasswordResetConfirmation(rawToken, VALID_NEW_PASSWORD))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("expiro"));

        assertThat(passwordHashOf(userId)).isEqualTo(previousHash);
    }

    @Test
    void rejectsAnUnknownTokenWithoutRevealingAnything() {
        String csrf = fetchCsrfToken();

        client.post().uri(CONFIRM_ENDPOINT)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new PasswordResetConfirmation("token-que-no-existe", VALID_NEW_PASSWORD))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @ParameterizedTest
    @ValueSource(strings = {"corta1A", "sinmayuscula1", "SINMINUSCULA1", "SinDigitos"})
    void rejectsANewPasswordThatBreaksThePolicyWith400(String weakPassword) {
        String email = "reset-policy-" + System.nanoTime() + "@example.com";
        Long userId = createUser(email);
        String csrf = fetchCsrfToken();
        requestReset(email, csrf);
        String rawToken = rawTokenFor(userId);
        String previousHash = passwordHashOf(userId);

        client.post().uri(CONFIRM_ENDPOINT)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new PasswordResetConfirmation(rawToken, weakPassword))
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(passwordHashOf(userId)).isEqualTo(previousHash);
        Boolean stillPending = jdbcTemplate.queryForObject(
                "SELECT used_at IS NULL FROM password_reset_tokens WHERE user_id = ?", Boolean.class, userId);
        assertThat(stillPending).isTrue();
    }

    @Test
    void aNewRequestInvalidatesThePendingTokenOfTheSameUser() {
        String email = "reset-supersede-" + System.nanoTime() + "@example.com";
        Long userId = createUser(email);
        String csrf = fetchCsrfToken();

        requestReset(email, csrf);
        String firstRawToken = rawTokenFor(userId);

        requestReset(email, csrf);

        client.post().uri(CONFIRM_ENDPOINT)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new PasswordResetConfirmation(firstRawToken, VALID_NEW_PASSWORD))
                .exchange()
                .expectStatus().isBadRequest();

        Integer pending = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM password_reset_tokens WHERE user_id = ? AND used_at IS NULL",
                Integer.class, userId);
        assertThat(pending).isEqualTo(1);
    }

    @Test
    void resettingThePasswordRevokesTheActiveSessionsOfThatUser() {
        String email = "reset-sessions-" + System.nanoTime() + "@example.com";
        Long userId = createUser(email);
        String csrf = fetchCsrfToken();

        client.post().uri(LOGIN_ENDPOINT)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new LoginRequest(email, ORIGINAL_PASSWORD))
                .exchange()
                .expectStatus().isOk();

        Integer activeBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, userId);
        assertThat(activeBefore).isPositive();

        requestReset(email, csrf);
        String rawToken = rawTokenFor(userId);

        client.post().uri(CONFIRM_ENDPOINT)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new PasswordResetConfirmation(rawToken, VALID_NEW_PASSWORD))
                .exchange()
                .expectStatus().isNoContent();

        Integer activeAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, userId);
        assertThat(activeAfter).isZero();
    }
}
