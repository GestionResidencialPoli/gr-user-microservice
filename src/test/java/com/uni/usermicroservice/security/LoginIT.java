package com.uni.usermicroservice.security;

import com.uni.usermicroservice.UserMicroserviceApplication;
import com.uni.usermicroservice.support.AbstractIntegrationTest;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {UserMicroserviceApplication.class, LoginIT.ProtectedTestController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class LoginIT extends AbstractIntegrationTest {

    private static final String TEST_SECRET = "a-secret-of-at-least-32-characters-long";
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private RestTestClient client;

    @RestController
    static class ProtectedTestController {

        @GetMapping("/api/v1/test/authenticated-only")
        @PreAuthorize("isAuthenticated()")
        String authenticatedOnly() {
            return "ok";
        }
    }

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String fetchCsrfToken() {
        var result = client.get().uri("/api/v1/test/authenticated-only").exchange().returnResult(Void.class);
        return result.getResponseCookies().getFirst("XSRF-TOKEN").getValue();
    }

    private RestTestClient.ResponseSpec login(LoginRequest loginRequest) {
        String csrf = fetchCsrfToken();
        return client.post().uri("/api/v1/auth/login")
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(loginRequest)
                .exchange();
    }

    private Long insertUser(String email, String rawPassword, String role, boolean active) {
        String documentNumber = "DOC-" + email.substring(0, email.indexOf('@'));
        jdbcTemplate.update(
                "INSERT INTO users (first_name, last_name, document_number, email, password_hash, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "Test", "User", documentNumber, email, PASSWORD_ENCODER.encode(rawPassword),
                active ? "ACTIVE" : "INACTIVE"
        );
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = ?", Long.class, role);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    @Test
    void ca1_correctCredentialsReturnOkWithASignedToken() {
        insertUser("login-ok@example.com", "Str0ngPass!", "RESIDENTE", true);

        var result = login(new LoginRequest("login-ok@example.com", "Str0ngPass!"))
                .expectStatus().isOk()
                .returnResult(Void.class);

        assertThat(result.getResponseCookies().getFirst("access_token")).isNotNull();
        assertThat(result.getResponseCookies().getFirst("refresh_token")).isNotNull();
    }

    @Test
    void ca2_wrongPasswordAndUnknownEmailReturnTheSameGenericMessage() {
        insertUser("login-wrongpass@example.com", "Str0ngPass!", "RESIDENTE", true);

        String wrongPasswordBody = login(new LoginRequest("login-wrongpass@example.com", "not-the-password"))
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody(ApiError.class)
                .returnResult()
                .getResponseBody()
                .message();

        String unknownEmailBody = login(new LoginRequest("this-email-does-not-exist@example.com", "whatever"))
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody(ApiError.class)
                .returnResult()
                .getResponseBody()
                .message();

        assertThat(wrongPasswordBody).isEqualTo(unknownEmailBody);
    }

    @Test
    void ca3_inactiveUserWithCorrectPasswordIsRejected() {
        insertUser("login-inactive@example.com", "Str0ngPass!", "RESIDENTE", false);

        login(new LoginRequest("login-inactive@example.com", "Str0ngPass!"))
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void ca4_theTokenContainsUserIdRoleAndExpirationButNeverThePasswordOrItsHash() {
        Long userId = insertUser("login-claims@example.com", "Str0ngPass!", "ADMINISTRACION", true);

        var result = login(new LoginRequest("login-claims@example.com", "Str0ngPass!"))
                .expectStatus().isOk()
                .returnResult(Void.class);

        String accessToken = result.getResponseCookies().getFirst("access_token").getValue();
        Claims claims = jwtTokenProvider.parseClaims(accessToken).orElseThrow();

        assertThat(jwtTokenProvider.userIdOf(claims)).isEqualTo(userId);
        assertThat(jwtTokenProvider.rolesOf(claims)).containsExactly("ADMINISTRACION");
        assertThat(claims.getExpiration()).isAfter(new Date());
        assertThat(claims.keySet()).doesNotContain("password", "passwordHash", "password_hash");
    }

    @Test
    void ca5_anExpiredTokenOnAProtectedEndpointIsRejected() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(3600);
        String expiredToken = Jwts.builder()
                .subject("expired@example.com")
                .claim("uid", 1L)
                .claim("roles", List.of("RESIDENTE"))
                .issuedAt(Date.from(past))
                .expiration(Date.from(past.plusSeconds(60)))
                .signWith(key)
                .compact();

        client.get().uri("/api/v1/test/authenticated-only")
                .cookie("access_token", expiredToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void ca7_aTamperedTokenSignatureIsRejected() {
        insertUser("login-tamper@example.com", "Str0ngPass!", "RESIDENTE", true);
        var result = login(new LoginRequest("login-tamper@example.com", "Str0ngPass!"))
                .expectStatus().isOk()
                .returnResult(Void.class);
        String token = result.getResponseCookies().getFirst("access_token").getValue();
        int middle = token.length() / 2;
        char flipped = token.charAt(middle) == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, middle) + flipped + token.substring(middle + 1);

        client.get().uri("/api/v1/test/authenticated-only")
                .cookie("access_token", tampered)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
