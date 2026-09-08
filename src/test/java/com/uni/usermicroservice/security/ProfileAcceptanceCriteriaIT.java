package com.uni.usermicroservice.security;

import com.uni.usermicroservice.UserMicroserviceApplication;
import com.uni.usermicroservice.identity.domain.TipoResidente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class ProfileAcceptanceCriteriaIT {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String CURRENT_PASSWORD = "Str0ngPass!";

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private Long createUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (first_name, last_name, document_number, email, password_hash, phone) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "Camila", "Restrepo", "DOC-" + uniqueId(), email, passwordEncoder.encode(CURRENT_PASSWORD), "3000000000");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = ?", Long.class, "RESIDENTE");
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    private String accessCookieFor(Long userId, String email) {
        return authTokenService.issueTokens(userId, email, List.of("RESIDENTE")).accessCookie().getValue();
    }

    private String fetchCsrfToken() {
        var result = client.get().uri("/api/v1/auth/me").exchange().returnResult(Void.class);
        return result.getResponseCookies().getFirst(CSRF_COOKIE).getValue();
    }

    private static String uniqueId() {
        return Long.toString(System.nanoTime(), 36);
    }

    @Test
    void ca1_seeingMyProfileShowsNameEmailRoleAndApartment() {
        String email = "profile-ca1-" + uniqueId() + "@example.com";
        Long userId = createUser(email);
        String numero = "P" + uniqueId();
        jdbcTemplate.update(
                "INSERT INTO apartments (torre, numero, piso, activo) VALUES (?, ?, ?, ?)", "P", numero, 1, true);
        Long apartmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM apartments WHERE torre = ? AND numero = ?", Long.class, "P", numero);
        jdbcTemplate.update(
                "INSERT INTO owners (user_id, apartment_id, principal) VALUES (?, ?, true)", userId, apartmentId);
        String accessCookie = accessCookieFor(userId, email);

        var body = client.get().uri("/api/v1/auth/me")
                .cookie(ACCESS_COOKIE, accessCookie)
                .exchange()
                .expectStatus().isOk()
                .expectBody(MeResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.firstName()).isEqualTo("Camila");
        assertThat(body.lastName()).isEqualTo("Restrepo");
        assertThat(body.email()).isEqualTo(email);
        assertThat(body.roles()).containsExactly("RESIDENTE");
        assertThat(body.apartment()).isNotNull();
        assertThat(body.apartment().torre()).isEqualTo("P");
        assertThat(body.apartment().numero()).isEqualTo(numero);
        assertThat(body.apartment().tipoResidente()).isEqualTo(TipoResidente.PROPIETARIO);
    }

    @Test
    void ca2_changingPasswordWithTheWrongCurrentPasswordIsRejected() {
        String email = "profile-ca2-" + uniqueId() + "@example.com";
        Long userId = createUser(email);
        String accessCookie = accessCookieFor(userId, email);
        String csrf = fetchCsrfToken();

        var response = client.post().uri("/api/v1/auth/me/password")
                .cookie(ACCESS_COOKIE, accessCookie)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new ChangePasswordRequest("wrong-password", "NewStr0ngPass!"))
                .exchange();

        response.expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        response.expectBody(ApiError.class).value(body -> assertThat(body.status()).isEqualTo(401));

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, userId);
        assertThat(passwordEncoder.matches(CURRENT_PASSWORD, storedHash)).isTrue();
    }

    @Test
    void ca3_aNewPasswordThatDoesNotMeetThePolicyIsRejectedWithTheMissingRequirement() {
        String email = "profile-ca3-" + uniqueId() + "@example.com";
        Long userId = createUser(email);
        String accessCookie = accessCookieFor(userId, email);
        String csrf = fetchCsrfToken();

        var response = client.post().uri("/api/v1/auth/me/password")
                .cookie(ACCESS_COOKIE, accessCookie)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new ChangePasswordRequest(CURRENT_PASSWORD, "todaminuscula"))
                .exchange();

        response.expectStatus().isEqualTo(HttpStatus.BAD_REQUEST);
        response.expectBody(ApiError.class).value(body ->
                assertThat(body.message()).contains("mayuscula"));
    }

    @Test
    void ca4_updatingMyPhoneSurvivesAReload() {
        String email = "profile-ca4-" + uniqueId() + "@example.com";
        Long userId = createUser(email);
        String accessCookie = accessCookieFor(userId, email);
        String csrf = fetchCsrfToken();

        client.patch().uri("/api/v1/auth/me")
                .cookie(ACCESS_COOKIE, accessCookie)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new UpdateProfileRequest("3011234567"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(MeResponse.class)
                .value(body -> assertThat(body.phone()).isEqualTo("3011234567"));

        client.get().uri("/api/v1/auth/me")
                .cookie(ACCESS_COOKIE, accessCookie)
                .exchange()
                .expectStatus().isOk()
                .expectBody(MeResponse.class)
                .value(body -> assertThat(body.phone()).isEqualTo("3011234567"));
    }

    @Test
    void ca5_aRoleFieldSentInTheProfileUpdateRequestIsIgnored() {
        String email = "profile-ca5-" + uniqueId() + "@example.com";
        Long userId = createUser(email);
        String accessCookie = accessCookieFor(userId, email);
        String csrf = fetchCsrfToken();

        client.patch().uri("/api/v1/auth/me")
                .cookie(ACCESS_COOKIE, accessCookie)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"phone\":\"3011234567\",\"role\":\"ADMINISTRACION\",\"roles\":[\"ADMINISTRACION\"]}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(MeResponse.class)
                .value(body -> assertThat(body.roles()).containsExactly("RESIDENTE"));

        Long roleCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_roles ur JOIN roles r ON r.id = ur.role_id "
                        + "WHERE ur.user_id = ? AND r.name = ?",
                Long.class, userId, "ADMINISTRACION");
        assertThat(roleCount).isZero();
    }
}
