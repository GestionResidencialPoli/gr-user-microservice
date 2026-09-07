package com.uni.usermicroservice.security;

import com.uni.usermicroservice.UserMicroserviceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = {UserMicroserviceApplication.class, SecurityAcceptanceCriteriaTest.ProtectedTestController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=a-secret-of-at-least-32-characters-long",
                "jwt.access-token-expiration-minutes=15",
                "jwt.refresh-token-expiration-days=7",
                "app.cors.allowed-origins=http://localhost:3000"
        }
)
class SecurityAcceptanceCriteriaTest {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

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
    private AuthTokenService authTokenService;

    private RestTestClient client;

    @RestController
    static class ProtectedTestController {

        @GetMapping("/api/v1/test/admin-only")
        @PreAuthorize("hasRole('ADMINISTRACION')")
        String adminOnly() {
            return "ok";
        }
    }

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private Long insertUser(String email, String role) {
        jdbcTemplate.update(
                "INSERT INTO users (first_name, last_name, email, password_hash) VALUES (?, ?, ?, ?)",
                "Test", "User", email, "irrelevant-hash-for-this-test");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = ?", Long.class, role);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    private AuthTokenService.IssuedTokens createUserAndIssueTokens(String email, String role) {
        Long userId = insertUser(email, role);
        return authTokenService.issueTokens(userId, email, List.of(role));
    }

    private String fetchCsrfToken() {
        var result = client.get().uri("/api/v1/test/admin-only").exchange().returnResult(Void.class);
        return result.getResponseCookies().getFirst(CSRF_COOKIE).getValue();
    }

    @Test
    void rejectsRequestsWithoutACookieWith401() {
        client.get().uri("/api/v1/test/admin-only")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsAResidentAccessCookieOnAnAdminOnlyEndpointWith403() {
        var tokens = createUserAndIssueTokens("resident@example.com", "RESIDENTE");

        client.get().uri("/api/v1/test/admin-only")
                .cookie(ACCESS_COOKIE, tokens.accessCookie().getValue())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void acceptsAnAdministracionAccessCookieOnAnAdminOnlyEndpoint() {
        var tokens = createUserAndIssueTokens("admin@example.com", "ADMINISTRACION");

        client.get().uri("/api/v1/test/admin-only")
                .cookie(ACCESS_COOKIE, tokens.accessCookie().getValue())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ok");
    }

    @Test
    void anAuthenticatedRequestToAnUnmappedPathReturnsNotFoundNotUnauthorized() {
        var tokens = createUserAndIssueTokens("notfound@example.com", "ADMINISTRACION");

        client.get().uri("/api/v1/does-not-exist")
                .cookie(ACCESS_COOKIE, tokens.accessCookie().getValue())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void issuesHttpOnlySecureSameSiteStrictCookies() {
        var tokens = createUserAndIssueTokens("cookieattrs@example.com", "RESIDENTE");

        assertThat(tokens.accessCookie().isHttpOnly()).isTrue();
        assertThat(tokens.accessCookie().isSecure()).isTrue();
        assertThat(tokens.accessCookie().getSameSite()).isEqualTo("Strict");

        assertThat(tokens.refreshCookie().isHttpOnly()).isTrue();
        assertThat(tokens.refreshCookie().isSecure()).isTrue();
        assertThat(tokens.refreshCookie().getSameSite()).isEqualTo("Strict");
        assertThat(tokens.refreshCookie().getPath()).isEqualTo("/api/v1/auth");
    }

    @Test
    void doesNotCreateAnHttpSession() {
        var tokens = createUserAndIssueTokens("nosession@example.com", "ADMINISTRACION");

        var result = client.get().uri("/api/v1/test/admin-only")
                .cookie(ACCESS_COOKIE, tokens.accessCookie().getValue())
                .exchange()
                .returnResult(String.class);

        assertThat(result.getResponseCookies().get("JSESSIONID")).isNull();
    }

    @Test
    void includesTheAllowedOriginInCorsResponses() {
        var tokens = createUserAndIssueTokens("cors-ok@example.com", "ADMINISTRACION");

        client.get().uri("/api/v1/test/admin-only")
                .cookie(ACCESS_COOKIE, tokens.accessCookie().getValue())
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .exchange()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");
    }

    @Test
    void rejectsAnOriginThatIsNotAllowed() {
        var tokens = createUserAndIssueTokens("cors-bad@example.com", "ADMINISTRACION");

        client.get().uri("/api/v1/test/admin-only")
                .cookie(ACCESS_COOKIE, tokens.accessCookie().getValue())
                .header(HttpHeaders.ORIGIN, "http://malicious.example.com")
                .exchange()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void refreshWithoutTheCsrfHeaderIsRejected() {
        var tokens = createUserAndIssueTokens("csrf-refresh@example.com", "RESIDENTE");
        String csrf = fetchCsrfToken();

        client.post().uri("/api/v1/auth/refresh")
                .cookie(REFRESH_COOKIE, tokens.refreshCookie().getValue())
                .cookie(CSRF_COOKIE, csrf)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void refreshRotatesTheRefreshTokenAndRevokesThePrevious() {
        var tokens = createUserAndIssueTokens("rotate@example.com", "RESIDENTE");
        String csrf = fetchCsrfToken();
        String oldRawRefreshToken = tokens.refreshCookie().getValue();

        var result = client.post().uri("/api/v1/auth/refresh")
                .cookie(REFRESH_COOKIE, oldRawRefreshToken)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange()
                .expectStatus().isOk()
                .returnResult(Void.class);

        String newRawRefreshToken = result.getResponseCookies().getFirst(REFRESH_COOKIE).getValue();
        assertThat(newRawRefreshToken).isNotEqualTo(oldRawRefreshToken);
        assertThat(result.getResponseCookies().getFirst(ACCESS_COOKIE)).isNotNull();

        Boolean oldIsRevoked = jdbcTemplate.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM refresh_tokens WHERE token_hash = ?",
                Boolean.class,
                AuthTokenService.hash(oldRawRefreshToken)
        );
        assertThat(oldIsRevoked).isTrue();

        client.post().uri("/api/v1/auth/refresh")
                .cookie(REFRESH_COOKIE, newRawRefreshToken)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void refreshWithARevokedTokenIsRejected() {
        var tokens = createUserAndIssueTokens("revoked@example.com", "RESIDENTE");
        String csrf = fetchCsrfToken();
        String rawRefreshToken = tokens.refreshCookie().getValue();
        authTokenService.revoke(rawRefreshToken);

        client.post().uri("/api/v1/auth/refresh")
                .cookie(REFRESH_COOKIE, rawRefreshToken)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void refreshWithoutACookieIsRejected() {
        String csrf = fetchCsrfToken();

        client.post().uri("/api/v1/auth/refresh")
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void logoutRevokesTheRefreshTokenAndClearsBothCookies() {
        var tokens = createUserAndIssueTokens("logout@example.com", "RESIDENTE");
        String csrf = fetchCsrfToken();
        String rawRefreshToken = tokens.refreshCookie().getValue();

        var result = client.post().uri("/api/v1/auth/logout")
                .cookie(REFRESH_COOKIE, rawRefreshToken)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange()
                .expectStatus().isNoContent()
                .returnResult(Void.class);

        assertThat(result.getResponseCookies().getFirst(ACCESS_COOKIE).getMaxAge()).isZero();
        assertThat(result.getResponseCookies().getFirst(REFRESH_COOKIE).getMaxAge()).isZero();

        Boolean isRevoked = jdbcTemplate.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM refresh_tokens WHERE token_hash = ?",
                Boolean.class,
                AuthTokenService.hash(rawRefreshToken)
        );
        assertThat(isRevoked).isTrue();
    }
}
