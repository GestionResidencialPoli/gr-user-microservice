package com.uni.usermicroservice.security;

import com.uni.usermicroservice.UserMicroserviceApplication;
import com.uni.usermicroservice.support.AbstractIntegrationTest;
import com.uni.usermicroservice.support.TestPasswords;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = UserMicroserviceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.access-token-expiration-minutes=15",
                "jwt.refresh-token-expiration-days=7",
                "app.cors.allowed-origins=http://localhost:3000"
        }
)
class AdminSsoAcceptanceCriteriaIT extends AbstractIntegrationTest {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

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

    @Test
    void validCodeCreatesTheExpectedCookiesAndCannotBeExchangedTwice() {
        var tokens = tokensFor("sso-admin@example.com", "ADMINISTRACION");
        String csrf = fetchCsrfToken();
        String code = issueCode(tokens.accessCookie().getValue(), csrf);

        var result = exchange(code, csrf)
                .expectStatus().isOk()
                .returnResult(Void.class);

        assertThat(result.getResponseCookies().getFirst(ACCESS_COOKIE)).isNotNull();
        assertThat(result.getResponseCookies().getFirst("refresh_token")).isNotNull();
        assertThat(result.getResponseCookies().getFirst(ACCESS_COOKIE).isHttpOnly()).isTrue();

        exchange(code, csrf).expectStatus().isUnauthorized();
    }

    @Test
    void expiredOrWrongAudienceCodeIsRejected() {
        Long userId = insertUser("sso-expired@example.com", "ADMINISTRACION");
        String csrf = fetchCsrfToken();
        insertCode("expired-code", userId, "admin", Instant.now().minusSeconds(1));
        insertCode("other-audience-code", userId, "resident", Instant.now().plusSeconds(60));

        exchange("expired-code", csrf).expectStatus().isUnauthorized();
        exchange("other-audience-code", csrf).expectStatus().isUnauthorized();
    }

    @Test
    void codeForAUserWithoutAdministrationRoleIsForbidden() {
        Long userId = insertUser("sso-resident@example.com", "RESIDENTE");
        String csrf = fetchCsrfToken();
        insertCode("resident-code", userId, "admin", Instant.now().plusSeconds(60));

        exchange("resident-code", csrf).expectStatus().isForbidden();
    }

    @Test
    void codeIssuanceRequiresAdministrationAndExchangeRequiresCsrf() {
        var resident = tokensFor("sso-issuer-resident@example.com", "RESIDENTE");
        String csrf = fetchCsrfToken();
        client.post().uri("/api/v1/auth/admin-sso/code")
                .cookie(ACCESS_COOKIE, resident.accessCookie().getValue())
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange()
                .expectStatus().isForbidden();

        Long userId = insertUser("sso-csrf@example.com", "ADMINISTRACION");
        insertCode("csrf-code", userId, "admin", Instant.now().plusSeconds(60));
        client.post().uri("/api/v1/auth/admin-sso/exchange")
                .body(new AdminSsoExchangeRequest("csrf-code"))
                .exchange()
                .expectStatus().isForbidden();
    }

    private String issueCode(String accessToken, String csrf) {
        return client.post().uri("/api/v1/auth/admin-sso/code")
                .cookie(ACCESS_COOKIE, accessToken)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AdminSsoCodeResponse.class)
                .returnResult().getResponseBody().code();
    }

    private RestTestClient.ResponseSpec exchange(String code, String csrf) {
        return client.post().uri("/api/v1/auth/admin-sso/exchange")
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new AdminSsoExchangeRequest(code))
                .exchange();
    }

    private String fetchCsrfToken() {
        var result = client.get().uri("/api/v1/auth/csrf").exchange().returnResult(Void.class);
        return result.getResponseCookies().getFirst(CSRF_COOKIE).getValue();
    }

    private AuthTokenService.IssuedTokens tokensFor(String email, String role) {
        Long userId = insertUser(email, role);
        return authTokenService.issueTokens(userId, email, List.of(role));
    }

    private Long insertUser(String email, String role) {
        jdbcTemplate.update(
                "INSERT INTO users (first_name, last_name, document_number, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                "Test", "User", "DOC-" + email, email, TestPasswords.UNUSABLE_BCRYPT_HASH);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = ?", Long.class, role);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    private void insertCode(String rawCode, Long userId, String audience, Instant expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO admin_sso_codes (user_id, code_hash, audience, expires_at, created_at) VALUES (?, ?, ?, ?, ?)",
                userId, AuthTokenService.hash(rawCode), audience, expiresAt, expiresAt.minusSeconds(60));
    }
}
