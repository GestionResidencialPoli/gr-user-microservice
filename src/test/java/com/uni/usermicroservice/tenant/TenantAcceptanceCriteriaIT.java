package com.uni.usermicroservice.tenant;

import com.uni.usermicroservice.UserMicroserviceApplication;
import com.uni.usermicroservice.identity.domain.TipoResidente;
import com.uni.usermicroservice.security.AuthTokenService;
import com.uni.usermicroservice.security.JwtTokenProvider;
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
class TenantAcceptanceCriteriaIT {

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

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private Long createUserWithRole(String email, String role) {
        jdbcTemplate.update(
                "INSERT INTO users (first_name, last_name, document_number, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                "Test", "User", "DOC-" + System.nanoTime(), email, "irrelevant-hash-for-this-test");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = ?", Long.class, role);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    private String accessCookieFor(String email, String role) {
        Long userId = createUserWithRole(email, role);
        return authTokenService.issueTokens(userId, email, List.of(role)).accessCookie().getValue();
    }

    private String adminAccessCookie() {
        return accessCookieFor("admin-" + System.nanoTime() + "@example.com", "ADMINISTRACION");
    }

    private String residenteAccessCookie() {
        return accessCookieFor("resident-" + System.nanoTime() + "@example.com", "RESIDENTE");
    }

    private String fetchCsrfToken(String accessCookie, Long apartmentId) {
        var result = client.get().uri("/api/v1/apartamentos/" + apartmentId + "/arrendatarios")
                .cookie(ACCESS_COOKIE, accessCookie)
                .exchange()
                .returnResult(Void.class);
        return result.getResponseCookies().getFirst(CSRF_COOKIE).getValue();
    }

    private Long insertApartment(String torre, String numero, boolean activo) {
        jdbcTemplate.update(
                "INSERT INTO apartments (torre, numero, piso, activo) VALUES (?, ?, ?, ?)",
                torre, numero, 2, activo);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM apartments WHERE torre = ? AND numero = ?", Long.class, torre, numero);
    }

    private ArrendatarioRequest anArrendatario(String suffix) {
        return new ArrendatarioRequest(
                "Luis", "Marin", "DOC-ARR-" + suffix, "luis.marin." + suffix + "@example.com", "3009999999");
    }

    @Test
    void linksATenantToAnActiveApartmentAndItAppearsInTheListing() {
        String admin = adminAccessCookie();
        Long apartmentId = insertApartment("ARR", "101", true);
        String csrf = fetchCsrfToken(admin, apartmentId);

        var created = client.post().uri("/api/v1/apartamentos/" + apartmentId + "/arrendatarios")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anArrendatario("101"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody(ArrendatarioResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.documentNumber()).isEqualTo("DOC-ARR-101");
        assertThat(created.email()).isEqualTo("luis.marin.101@example.com");
        assertThat(created.tipoResidente()).isEqualTo(TipoResidente.ARRENDATARIO);
        assertThat(created.apartamentoId()).isEqualTo(apartmentId);

        client.get().uri("/api/v1/apartamentos/" + apartmentId + "/arrendatarios")
                .cookie(ACCESS_COOKIE, admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("DOC-ARR-101"));
    }

    @Test
    void theTokenOfALinkedTenantCarriesTipoResidenteArrendatario() {
        String admin = adminAccessCookie();
        Long apartmentId = insertApartment("ARR", "102", true);
        String csrf = fetchCsrfToken(admin, apartmentId);

        var created = client.post().uri("/api/v1/apartamentos/" + apartmentId + "/arrendatarios")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anArrendatario("102"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody(ArrendatarioResponse.class)
                .returnResult()
                .getResponseBody();

        String accessToken = authTokenService
                .issueTokens(created.userId(), created.email(), List.of("RESIDENTE"))
                .accessCookie()
                .getValue();

        var claims = jwtTokenProvider.parseClaims(accessToken);
        assertThat(claims).isPresent();
        assertThat(jwtTokenProvider.tipoResidenteOf(claims.get())).contains(TipoResidente.ARRENDATARIO);
        assertThat(jwtTokenProvider.rolesOf(claims.get())).containsExactly("RESIDENTE");
    }

    @Test
    void rejectsLinkingATenantToAnInactiveApartmentWith409() {
        String admin = adminAccessCookie();
        Long apartmentId = insertApartment("ARR", "103", false);
        String csrf = fetchCsrfToken(admin, apartmentId);

        client.post().uri("/api/v1/apartamentos/" + apartmentId + "/arrendatarios")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anArrendatario("103"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);

        Integer linked = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tenants WHERE apartment_id = ?", Integer.class, apartmentId);
        assertThat(linked).isZero();
    }

    @Test
    void unlinkingRemovesTheTenantFromTheListingAndFromTheToken() {
        String admin = adminAccessCookie();
        Long apartmentId = insertApartment("ARR", "104", true);
        String csrf = fetchCsrfToken(admin, apartmentId);

        var created = client.post().uri("/api/v1/apartamentos/" + apartmentId + "/arrendatarios")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anArrendatario("104"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody(ArrendatarioResponse.class)
                .returnResult()
                .getResponseBody();

        client.delete().uri("/api/v1/apartamentos/" + apartmentId + "/arrendatarios/" + created.arrendatarioId())
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange()
                .expectStatus().isNoContent();

        client.get().uri("/api/v1/apartamentos/" + apartmentId + "/arrendatarios")
                .cookie(ACCESS_COOKIE, admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).doesNotContain("DOC-ARR-104"));

        String accessToken = authTokenService
                .issueTokens(created.userId(), created.email(), List.of("RESIDENTE"))
                .accessCookie()
                .getValue();

        var claims = jwtTokenProvider.parseClaims(accessToken);
        assertThat(jwtTokenProvider.tipoResidenteOf(claims.get())).isEmpty();
    }

    @Test
    void rejectsASecondActiveTenancyForTheSamePersonWith409() {
        String admin = adminAccessCookie();
        Long first = insertApartment("ARR", "105", true);
        Long second = insertApartment("ARR", "106", true);
        String csrf = fetchCsrfToken(admin, first);

        client.post().uri("/api/v1/apartamentos/" + first + "/arrendatarios")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anArrendatario("105"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED);

        client.post().uri("/api/v1/apartamentos/" + second + "/arrendatarios")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anArrendatario("105"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aResidenteGets403WhenLinkingOrListingTenants() {
        String admin = adminAccessCookie();
        Long apartmentId = insertApartment("ARR", "107", true);
        String residente = residenteAccessCookie();
        String csrf = fetchCsrfToken(admin, apartmentId);

        client.post().uri("/api/v1/apartamentos/" + apartmentId + "/arrendatarios")
                .cookie(ACCESS_COOKIE, residente)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anArrendatario("107"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);

        client.get().uri("/api/v1/apartamentos/" + apartmentId + "/arrendatarios")
                .cookie(ACCESS_COOKIE, residente)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void returns404WhenTheApartmentDoesNotExist() {
        String admin = adminAccessCookie();
        Long apartmentId = insertApartment("ARR", "108", true);
        String csrf = fetchCsrfToken(admin, apartmentId);

        client.post().uri("/api/v1/apartamentos/999999/arrendatarios")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anArrendatario("108"))
                .exchange()
                .expectStatus().isNotFound();
    }
}
