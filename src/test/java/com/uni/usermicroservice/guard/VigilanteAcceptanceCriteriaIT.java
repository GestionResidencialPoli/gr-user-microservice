package com.uni.usermicroservice.guard;

import com.uni.usermicroservice.UserMicroserviceApplication;
import com.uni.usermicroservice.identity.domain.UserStatus;
import com.uni.usermicroservice.security.AuthTokenService;
import com.uni.usermicroservice.security.LoginRequest;
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
class VigilanteAcceptanceCriteriaIT {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String VIGILANTES = "/api/v1/vigilantes";
    private static final String PROTECTED_ENDPOINT = "/api/v1/apartamentos";
    private static final String INITIAL_PASSWORD = "Vigilante1";
    private static final String UNUSABLE_BCRYPT_HASH =
            "$2a$10$kZG4rLxEAXVVXndsY7SIsOe/Cn95u6.hFoEqcym267fn0ktD9dqv2";

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

    private long uniqueId() {
        return System.nanoTime();
    }

    private String accessCookieFor(String email, String role) {
        jdbcTemplate.update(
                "INSERT INTO users (first_name, last_name, document_number, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                "Test", "User", "DOC-" + uniqueId(), email, UNUSABLE_BCRYPT_HASH);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = ?", Long.class, role);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return authTokenService.issueTokens(userId, email, List.of(role)).accessCookie().getValue();
    }

    private String adminCookie() {
        return accessCookieFor("admin-vg-" + uniqueId() + "@example.com", "ADMINISTRACION");
    }

    private String fetchCsrfToken(String accessCookie) {
        var result = client.get().uri(VIGILANTES).cookie(ACCESS_COOKIE, accessCookie).exchange().returnResult(Void.class);
        return result.getResponseCookies().getFirst(CSRF_COOKIE).getValue();
    }

    private VigilanteRequest aVigilante(String suffix) {
        return new VigilanteRequest(
                "Julian", "Ospina", "DOC-VG-" + suffix, "vigilante." + suffix + "@example.com",
                "3001112233", INITIAL_PASSWORD);
    }

    private VigilanteResponse createVigilante(String admin, String csrf, String suffix) {
        return client.post().uri(VIGILANTES)
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(aVigilante(suffix))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody(VigilanteResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private String loginAndGetAccessCookie(String email, String csrf) {
        var result = client.post().uri("/api/v1/auth/login")
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new LoginRequest(email, INITIAL_PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Void.class);
        return result.getResponseCookies().getFirst(ACCESS_COOKIE).getValue();
    }

    private void deactivate(String admin, String csrf, Long userId) {
        client.delete().uri(VIGILANTES + "/" + userId)
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void createsAnActiveVigilanteThatCanLogIn() {
        String admin = adminCookie();
        String csrf = fetchCsrfToken(admin);
        String suffix = String.valueOf(uniqueId());

        var created = createVigilante(admin, csrf, suffix);

        assertThat(created).isNotNull();
        assertThat(created.estado()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.documentNumber()).isEqualTo("DOC-VG-" + suffix);

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, created.userId());
        assertThat(storedHash).startsWith("$2").isNotEqualTo(INITIAL_PASSWORD);

        assertThat(loginAndGetAccessCookie("vigilante." + suffix + "@example.com", csrf)).isNotBlank();
    }

    @Test
    void theCreatedVigilanteAppearsInTheListingWithItsStatus() {
        String admin = adminCookie();
        String csrf = fetchCsrfToken(admin);
        String suffix = String.valueOf(uniqueId());
        var created = createVigilante(admin, csrf, suffix);

        client.get().uri(VIGILANTES)
                .cookie(ACCESS_COOKIE, admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("DOC-VG-" + suffix).contains("ACTIVE"));

        deactivate(admin, csrf, created.userId());

        client.get().uri(VIGILANTES)
                .cookie(ACCESS_COOKIE, admin)
                .exchange()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("DOC-VG-" + suffix).contains("INACTIVE"));
    }

    @Test
    void aDeactivatedVigilanteCannotLogInAnymore() {
        String admin = adminCookie();
        String csrf = fetchCsrfToken(admin);
        String suffix = String.valueOf(uniqueId());
        var created = createVigilante(admin, csrf, suffix);

        deactivate(admin, csrf, created.userId());

        client.post().uri("/api/v1/auth/login")
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new LoginRequest("vigilante." + suffix + "@example.com", INITIAL_PASSWORD))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anAlreadySignedInVigilanteIsRejectedOnTheVeryNextRequestAfterBeingDeactivated() {
        String admin = adminCookie();
        String csrf = fetchCsrfToken(admin);
        String suffix = String.valueOf(uniqueId());
        var created = createVigilante(admin, csrf, suffix);

        String vigilanteCookie = loginAndGetAccessCookie("vigilante." + suffix + "@example.com", csrf);

        client.get().uri(PROTECTED_ENDPOINT)
                .cookie(ACCESS_COOKIE, vigilanteCookie)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);

        deactivate(admin, csrf, created.userId());

        client.get().uri(PROTECTED_ENDPOINT)
                .cookie(ACCESS_COOKIE, vigilanteCookie)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deactivatingAVigilanteAlsoRevokesItsRefreshTokens() {
        String admin = adminCookie();
        String csrf = fetchCsrfToken(admin);
        String suffix = String.valueOf(uniqueId());
        var created = createVigilante(admin, csrf, suffix);
        loginAndGetAccessCookie("vigilante." + suffix + "@example.com", csrf);

        Integer activeBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, created.userId());
        assertThat(activeBefore).isPositive();

        deactivate(admin, csrf, created.userId());

        Integer activeAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, created.userId());
        assertThat(activeAfter).isZero();
    }

    @Test
    void aVigilanteCannotCreateAnotherVigilante() {
        String admin = adminCookie();
        String csrf = fetchCsrfToken(admin);
        String suffix = String.valueOf(uniqueId());
        createVigilante(admin, csrf, suffix);
        String vigilanteCookie = loginAndGetAccessCookie("vigilante." + suffix + "@example.com", csrf);

        client.post().uri(VIGILANTES)
                .cookie(ACCESS_COOKIE, vigilanteCookie)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(aVigilante(String.valueOf(uniqueId())))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aResidenteCannotListVigilantes() {
        String residente = accessCookieFor("res-vg-" + uniqueId() + "@example.com", "RESIDENTE");

        client.get().uri(VIGILANTES)
                .cookie(ACCESS_COOKIE, residente)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsADuplicateDocumentNumberWith409() {
        String admin = adminCookie();
        String csrf = fetchCsrfToken(admin);
        String suffix = String.valueOf(uniqueId());
        createVigilante(admin, csrf, suffix);

        client.post().uri(VIGILANTES)
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new VigilanteRequest(
                        "Otro", "Nombre", "DOC-VG-" + suffix, "otro." + uniqueId() + "@example.com",
                        null, INITIAL_PASSWORD))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsAnInitialPasswordThatBreaksThePolicy() {
        String admin = adminCookie();
        String csrf = fetchCsrfToken(admin);

        client.post().uri(VIGILANTES)
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new VigilanteRequest(
                        "Julian", "Ospina", "DOC-VG-" + uniqueId(), "weak." + uniqueId() + "@example.com",
                        null, "corta1"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void returns404WhenDeactivatingSomeoneThatIsNotAVigilante() {
        String admin = adminCookie();
        String csrf = fetchCsrfToken(admin);
        String email = "residente-no-vg-" + uniqueId() + "@example.com";
        accessCookieFor(email, "RESIDENTE");
        Long residenteId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);

        client.delete().uri(VIGILANTES + "/" + residenteId)
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange()
                .expectStatus().isNotFound();
    }
}
