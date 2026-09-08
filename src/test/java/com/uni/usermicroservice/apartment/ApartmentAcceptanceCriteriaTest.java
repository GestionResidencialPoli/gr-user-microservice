package com.uni.usermicroservice.apartment;

import com.uni.usermicroservice.support.TestPasswords;
import com.uni.usermicroservice.UserMicroserviceApplication;
import com.uni.usermicroservice.security.AuthTokenService;
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

import java.math.BigDecimal;
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
class ApartmentAcceptanceCriteriaTest {

    private static final String ACCESS_COOKIE = "access_token";
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

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String adminAccessCookie() {
        return issueAccessCookie("admin-" + System.nanoTime() + "@example.com", "ADMINISTRACION");
    }

    private String residenteAccessCookie() {
        return issueAccessCookie("resident-" + System.nanoTime() + "@example.com", "RESIDENTE");
    }

    private String issueAccessCookie(String email, String role) {
        jdbcTemplate.update(
                "INSERT INTO users (first_name, last_name, document_number, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                "Test", "User", "DOC-" + System.nanoTime(), email, TestPasswords.UNUSABLE_BCRYPT_HASH);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = ?", Long.class, role);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);

        return authTokenService.issueTokens(userId, email, List.of(role)).accessCookie().getValue();
    }

    private String fetchCsrfToken(String accessCookie) {
        var result = client.get().uri("/api/v1/apartamentos")
                .cookie(ACCESS_COOKIE, accessCookie)
                .exchange()
                .returnResult(Void.class);
        return result.getResponseCookies().getFirst(CSRF_COOKIE).getValue();
    }

    private ApartmentRequest anApartmentRequest(String torre, String numero) {
        return new ApartmentRequest(
                torre,
                numero,
                3,
                new BigDecimal("0.0123"),
                new BigDecimal("65.50"),
                new PropietarioRequest(
                        "Ana",
                        "Perez",
                        "DOC-" + torre + numero,
                        "ana.perez." + torre + numero + "@example.com",
                        "3000000000"
                )
        );
    }

    @Test
    void createsAnApartmentAndItAppearsInTheListing() {
        String admin = adminAccessCookie();
        String csrf = fetchCsrfToken(admin);

        var created = client.post().uri("/api/v1/apartamentos")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anApartmentRequest("A", "101"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody(ApartmentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.torre()).isEqualTo("A");
        assertThat(created.numero()).isEqualTo("101");
        assertThat(created.propietario().email()).isEqualTo("ana.perez.a101@example.com");

        client.get().uri("/api/v1/apartamentos/" + created.id())
                .cookie(ACCESS_COOKIE, admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ApartmentResponse.class)
                .value(response -> assertThat(response.torre()).isEqualTo("A"));
    }

    @Test
    void rejectsADuplicateTorreAndNumeroWith409() {
        String admin = adminAccessCookie();
        String csrf = fetchCsrfToken(admin);

        client.post().uri("/api/v1/apartamentos")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anApartmentRequest("B", "202"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED);

        client.post().uri("/api/v1/apartamentos")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anApartmentRequest("B", "202"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsAnInvalidOwnerEmailOnUpdateWith400AndDoesNotSave() {
        String admin = adminAccessCookie();
        String csrf = fetchCsrfToken(admin);

        var created = client.post().uri("/api/v1/apartamentos")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anApartmentRequest("C", "303"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody(ApartmentResponse.class)
                .returnResult()
                .getResponseBody();

        ApartmentRequest invalidUpdate = new ApartmentRequest(
                "C", "303", 3, new BigDecimal("0.0123"), new BigDecimal("65.50"),
                new PropietarioRequest("Ana", "Perez", "DOC-C303", "correo-invalido", "3000000000")
        );

        client.put().uri("/api/v1/apartamentos/" + created.id())
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(invalidUpdate)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST);

        client.get().uri("/api/v1/apartamentos/" + created.id())
                .cookie(ACCESS_COOKIE, admin)
                .exchange()
                .expectBody(ApartmentResponse.class)
                .value(response -> assertThat(response.propietario().email()).isEqualTo("ana.perez.c303@example.com"));
    }

    @Test
    void deactivatingAnApartmentHidesItFromTheOperationalListingButKeepsItQueryableById() {
        String admin = adminAccessCookie();
        String csrf = fetchCsrfToken(admin);

        var created = client.post().uri("/api/v1/apartamentos")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anApartmentRequest("D", "404"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody(ApartmentResponse.class)
                .returnResult()
                .getResponseBody();

        client.delete().uri("/api/v1/apartamentos/" + created.id())
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange()
                .expectStatus().isNoContent();

        client.get().uri("/api/v1/apartamentos/" + created.id())
                .cookie(ACCESS_COOKIE, admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ApartmentResponse.class)
                .value(response -> assertThat(response.activo()).isFalse());

        client.get().uri("/api/v1/apartamentos?torre=D&numero=404")
                .cookie(ACCESS_COOKIE, admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).doesNotContain("\"id\":" + created.id()));
    }

    @Test
    void aResidenteGets403WhenCreatingAnApartment() {
        String residente = residenteAccessCookie();
        String csrf = fetchCsrfToken(residente);

        client.post().uri("/api/v1/apartamentos")
                .cookie(ACCESS_COOKIE, residente)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anApartmentRequest("E", "505"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsChangingTheOwnerDocumentOnUpdateAndLeavesTheCurrentOwnerIntact() {
        String admin = adminAccessCookie();
        String csrf = fetchCsrfToken(admin);

        var created = client.post().uri("/api/v1/apartamentos")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anApartmentRequest("F", "606"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody(ApartmentResponse.class)
                .returnResult()
                .getResponseBody();

        ApartmentRequest transferAttempt = new ApartmentRequest(
                "F", "606", 3, new BigDecimal("0.0123"), new BigDecimal("65.50"),
                new PropietarioRequest("Carlos", "Gomez", "DOC-OTRO-DUENO", "carlos.gomez@example.com", "3111111111")
        );

        client.put().uri("/api/v1/apartamentos/" + created.id())
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(transferAttempt)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);

        client.get().uri("/api/v1/apartamentos/" + created.id())
                .cookie(ACCESS_COOKIE, admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ApartmentResponse.class)
                .value(response -> {
                    assertThat(response.propietario().documentNumber()).isEqualTo("DOC-F606");
                    assertThat(response.propietario().firstName()).isEqualTo("Ana");
                    assertThat(response.propietario().email()).isEqualTo("ana.perez.f606@example.com");
                });

        Integer usersWithRejectedDocument = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE document_number = ?", Integer.class, "DOC-OTRO-DUENO");
        assertThat(usersWithRejectedDocument).isZero();
    }

    @Test
    void trimsTorreAndNumeroSoPaddedValuesDoNotBypassTheUniqueConstraint() {
        String admin = adminAccessCookie();
        String csrf = fetchCsrfToken(admin);

        PropietarioRequest propietario =
                new PropietarioRequest("Ana", "Perez", "DOC-G707", "ana.perez.g707@example.com", "3000000000");

        var created = client.post().uri("/api/v1/apartamentos")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new ApartmentRequest(" G ", " 707 ", 3, null, null, propietario))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody(ApartmentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.torre()).isEqualTo("G");
        assertThat(created.numero()).isEqualTo("707");

        client.post().uri("/api/v1/apartamentos")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new ApartmentRequest("G", "707", 3, null, null, propietario))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void listingIsPaginatedWithTheRealTotalCount() {
        String admin = adminAccessCookie();

        for (int i = 1; i <= 50; i++) {
            jdbcTemplate.update(
                    "INSERT INTO apartments (torre, numero, piso) VALUES (?, ?, ?)",
                    "PAG", String.valueOf(1000 + i), 1
            );
        }

        client.get().uri("/api/v1/apartamentos?torre=PAG&page=1&size=20")
                .cookie(ACCESS_COOKIE, admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody(PageResponse.class)
                .value(page -> {
                    assertThat(page.content()).hasSize(20);
                    assertThat(page.totalElements()).isEqualTo(50);
                    assertThat(page.page()).isEqualTo(1);
                });
    }
}
