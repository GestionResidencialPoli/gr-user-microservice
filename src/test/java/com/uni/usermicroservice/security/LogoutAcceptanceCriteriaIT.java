package com.uni.usermicroservice.security;

import com.uni.usermicroservice.UserMicroserviceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

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
class LogoutAcceptanceCriteriaIT {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String PROTECTED_ENDPOINT = "/api/v1/apartamentos";
    private static final String LOGOUT_ENDPOINT = "/api/v1/auth/logout";
    private static final String REFRESH_ENDPOINT = "/api/v1/auth/refresh";

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

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private AuthTokenService.IssuedTokens signIn(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (first_name, last_name, document_number, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                "Test", "User", "DOC-" + System.nanoTime(), email, "irrelevant-hash-for-this-test");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = ?", Long.class, "RESIDENTE");
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);

        return authTokenService.issueTokens(userId, email, List.of("RESIDENTE"));
    }

    private String fetchCsrfToken() {
        var result = client.get().uri(PROTECTED_ENDPOINT).exchange().returnResult(Void.class);
        return result.getResponseCookies().getFirst(CSRF_COOKIE).getValue();
    }

    private void logout(String rawRefreshToken, String csrf) {
        client.post().uri(LOGOUT_ENDPOINT)
                .cookie(REFRESH_COOKIE, rawRefreshToken)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void aProtectedEndpointAnswers200BeforeLogoutAnd401AfterTheClientDropsTheCookies() {
        var tokens = signIn("logout-protected@example.com");
        String csrf = fetchCsrfToken();

        client.get().uri(PROTECTED_ENDPOINT)
                .cookie(ACCESS_COOKIE, tokens.accessCookie().getValue())
                .exchange()
                .expectStatus().isOk();

        logout(tokens.refreshCookie().getValue(), csrf);

        client.get().uri(PROTECTED_ENDPOINT)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theRefreshTokenIsUnusableAfterLogout() {
        var tokens = signIn("logout-refresh@example.com");
        String csrf = fetchCsrfToken();
        String rawRefreshToken = tokens.refreshCookie().getValue();

        logout(rawRefreshToken, csrf);

        client.post().uri(REFRESH_ENDPOINT)
                .cookie(REFRESH_COOKIE, rawRefreshToken)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aPreviouslyIssuedAccessTokenStaysValidAfterLogoutUntilItExpires() {
        var tokens = signIn("logout-window@example.com");
        String csrf = fetchCsrfToken();
        String accessToken = tokens.accessCookie().getValue();

        logout(tokens.refreshCookie().getValue(), csrf);

        client.get().uri(PROTECTED_ENDPOINT)
                .cookie(ACCESS_COOKIE, accessToken)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void logoutSucceedsAndClearsCookiesWhenNoRefreshCookieIsSent() {
        String csrf = fetchCsrfToken();

        var result = client.post().uri(LOGOUT_ENDPOINT)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange()
                .expectStatus().isNoContent()
                .returnResult(Void.class);

        assertThat(result.getResponseCookies().getFirst(ACCESS_COOKIE).getMaxAge()).isZero();
        assertThat(result.getResponseCookies().getFirst(REFRESH_COOKIE).getMaxAge()).isZero();
    }

    @Test
    void logoutIsIdempotentWhenCalledTwiceWithTheSameRefreshToken() {
        var tokens = signIn("logout-twice@example.com");
        String csrf = fetchCsrfToken();
        String rawRefreshToken = tokens.refreshCookie().getValue();

        logout(rawRefreshToken, csrf);
        logout(rawRefreshToken, csrf);

        Boolean isRevoked = jdbcTemplate.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM refresh_tokens WHERE token_hash = ?",
                Boolean.class,
                AuthTokenService.hash(rawRefreshToken)
        );
        assertThat(isRevoked).isTrue();
    }

    @Test
    void logoutWithoutTheCsrfHeaderIsRejectedAndKeepsTheSessionAlive() {
        var tokens = signIn("logout-csrf@example.com");
        String csrf = fetchCsrfToken();
        String rawRefreshToken = tokens.refreshCookie().getValue();

        client.post().uri(LOGOUT_ENDPOINT)
                .cookie(REFRESH_COOKIE, rawRefreshToken)
                .cookie(CSRF_COOKIE, csrf)
                .exchange()
                .expectStatus().isForbidden();

        Boolean isRevoked = jdbcTemplate.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM refresh_tokens WHERE token_hash = ?",
                Boolean.class,
                AuthTokenService.hash(rawRefreshToken)
        );
        assertThat(isRevoked).isFalse();
    }

    @Test
    void logoutOnlyRevokesTheSessionThatWasClosed() {
        var firstSession = signIn("logout-one-session@example.com");
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, "logout-one-session@example.com");
        var secondSession = authTokenService.issueTokens(userId, "logout-one-session@example.com", List.of("RESIDENTE"));
        String csrf = fetchCsrfToken();

        logout(firstSession.refreshCookie().getValue(), csrf);

        client.post().uri(REFRESH_ENDPOINT)
                .cookie(REFRESH_COOKIE, secondSession.refreshCookie().getValue())
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange()
                .expectStatus().isOk();
    }
}
