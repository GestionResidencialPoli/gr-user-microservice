package com.uni.usermicroservice.security;

import com.uni.usermicroservice.support.TestPasswords;
import com.uni.usermicroservice.UserMicroserviceApplication;
import com.uni.usermicroservice.apartment.ApartmentRequest;
import com.uni.usermicroservice.apartment.PropietarioRequest;
import com.uni.usermicroservice.tenant.ArrendatarioRequest;
import com.uni.usermicroservice.tenant.ArrendatarioResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

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
class AuthorizationMatrixIT {

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    private RestTestClient client;
    private Long apartmentId;
    private Long arrendatarioId;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        apartmentId = insertApartment();
        arrendatarioId = createArrendatarioFixture(apartmentId);
    }

    private static Stream<Arguments> rolesExpecting(HttpStatus administracionStatus) {
        return Stream.of(
                Arguments.of((String) null, HttpStatus.UNAUTHORIZED),
                Arguments.of("RESIDENTE", HttpStatus.FORBIDDEN),
                Arguments.of("VIGILANTE", HttpStatus.FORBIDDEN),
                Arguments.of("ADMINISTRACION", administracionStatus)
        );
    }

    private static Stream<Arguments> createApartmentRoles() {
        return rolesExpecting(HttpStatus.CREATED);
    }

    private static Stream<Arguments> listApartmentsRoles() {
        return rolesExpecting(HttpStatus.OK);
    }

    private static Stream<Arguments> updateApartmentRoles() {
        return rolesExpecting(HttpStatus.OK);
    }

    private static Stream<Arguments> deactivateApartmentRoles() {
        return rolesExpecting(HttpStatus.NO_CONTENT);
    }

    private static Stream<Arguments> linkArrendatarioRoles() {
        return rolesExpecting(HttpStatus.CREATED);
    }

    private static Stream<Arguments> listArrendatariosRoles() {
        return rolesExpecting(HttpStatus.OK);
    }

    private static Stream<Arguments> unlinkArrendatarioRoles() {
        return rolesExpecting(HttpStatus.NO_CONTENT);
    }

    @ParameterizedTest(name = "POST /api/v1/apartamentos, rol {0} -> {1}")
    @MethodSource("createApartmentRoles")
    void createApartment(String role, HttpStatus expectedStatus) {
        String accessCookie = accessCookieForOrNull(role);
        String csrf = fetchCsrfToken();

        var response = client.post().uri("/api/v1/apartamentos")
                .cookie(ACCESS_COOKIE, accessCookie == null ? "" : accessCookie)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anApartmentRequest())
                .exchange();

        assertOutcome(response, expectedStatus);
    }

    @ParameterizedTest(name = "GET /api/v1/apartamentos, rol {0} -> {1}")
    @MethodSource("listApartmentsRoles")
    void listApartments(String role, HttpStatus expectedStatus) {
        String accessCookie = accessCookieForOrNull(role);

        var response = client.get().uri("/api/v1/apartamentos")
                .cookie(ACCESS_COOKIE, accessCookie == null ? "" : accessCookie)
                .exchange();

        assertOutcome(response, expectedStatus);
    }

    @ParameterizedTest(name = "PUT apartamento, rol {0} -> {1}")
    @MethodSource("updateApartmentRoles")
    void updateApartment(String role, HttpStatus expectedStatus) {
        String accessCookie = accessCookieForOrNull(role);
        String csrf = fetchCsrfToken();

        var response = client.put().uri("/api/v1/apartamentos/" + apartmentId)
                .cookie(ACCESS_COOKIE, accessCookie == null ? "" : accessCookie)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anApartmentRequest())
                .exchange();

        assertOutcome(response, expectedStatus);
    }

    @ParameterizedTest(name = "DELETE apartamento, rol {0} -> {1}")
    @MethodSource("deactivateApartmentRoles")
    void deactivateApartment(String role, HttpStatus expectedStatus) {
        String accessCookie = accessCookieForOrNull(role);
        String csrf = fetchCsrfToken();

        var response = client.delete().uri("/api/v1/apartamentos/" + apartmentId)
                .cookie(ACCESS_COOKIE, accessCookie == null ? "" : accessCookie)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange();

        assertOutcome(response, expectedStatus);
    }

    @ParameterizedTest(name = "POST arrendatarios, rol {0} -> {1}")
    @MethodSource("linkArrendatarioRoles")
    void linkArrendatario(String role, HttpStatus expectedStatus) {
        String accessCookie = accessCookieForOrNull(role);
        String csrf = fetchCsrfToken();

        var response = client.post().uri("/api/v1/apartamentos/" + apartmentId + "/arrendatarios")
                .cookie(ACCESS_COOKIE, accessCookie == null ? "" : accessCookie)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anArrendatarioRequest("extra-" + uniqueId()))
                .exchange();

        assertOutcome(response, expectedStatus);
    }

    @ParameterizedTest(name = "GET arrendatarios, rol {0} -> {1}")
    @MethodSource("listArrendatariosRoles")
    void listArrendatarios(String role, HttpStatus expectedStatus) {
        String accessCookie = accessCookieForOrNull(role);

        var response = client.get().uri("/api/v1/apartamentos/" + apartmentId + "/arrendatarios")
                .cookie(ACCESS_COOKIE, accessCookie == null ? "" : accessCookie)
                .exchange();

        assertOutcome(response, expectedStatus);
    }

    @ParameterizedTest(name = "DELETE arrendatarios, rol {0} -> {1}")
    @MethodSource("unlinkArrendatarioRoles")
    void unlinkArrendatario(String role, HttpStatus expectedStatus) {
        String accessCookie = accessCookieForOrNull(role);
        String csrf = fetchCsrfToken();

        var response = client.delete().uri("/api/v1/apartamentos/" + apartmentId + "/arrendatarios/" + arrendatarioId)
                .cookie(ACCESS_COOKIE, accessCookie == null ? "" : accessCookie)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .exchange();

        assertOutcome(response, expectedStatus);
    }

    @Test
    void administracionCanViewAnyApartment() {
        String admin = accessCookieFor("ADMINISTRACION");

        client.get().uri("/api/v1/apartamentos/" + apartmentId)
                .cookie(ACCESS_COOKIE, admin)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void theOwningResidenteCanViewTheirOwnApartment() {
        Long ownerId = createUserWithRole("owner-" + uniqueId() + "@example.com", "RESIDENTE");
        jdbcTemplate.update(
                "INSERT INTO owners (user_id, apartment_id, principal) VALUES (?, ?, true)", ownerId, apartmentId);
        String ownerCookie = accessCookieForExistingUser(ownerId, "RESIDENTE");

        client.get().uri("/api/v1/apartamentos/" + apartmentId)
                .cookie(ACCESS_COOKIE, ownerCookie)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void theActiveTenantCanViewTheApartmentTheyRentIn() {
        Long tenantId = createUserWithRole("current-tenant-" + uniqueId() + "@example.com", "RESIDENTE");
        jdbcTemplate.update(
                "INSERT INTO tenants (user_id, apartment_id, start_date) VALUES (?, ?, CURRENT_DATE)",
                tenantId, apartmentId);
        String tenantCookie = accessCookieForExistingUser(tenantId, "RESIDENTE");

        client.get().uri("/api/v1/apartamentos/" + apartmentId)
                .cookie(ACCESS_COOKIE, tenantCookie)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void aResidenteWithNoLinkToTheApartmentGets403() {
        String unrelatedResidente = accessCookieFor("RESIDENTE");

        var response = client.get().uri("/api/v1/apartamentos/" + apartmentId)
                .cookie(ACCESS_COOKIE, unrelatedResidente)
                .exchange();

        assertOutcome(response, HttpStatus.FORBIDDEN);
    }

    @Test
    void aVigilanteCannotViewApartmentDetail() {
        String vigilante = accessCookieFor("VIGILANTE");

        var response = client.get().uri("/api/v1/apartamentos/" + apartmentId)
                .cookie(ACCESS_COOKIE, vigilante)
                .exchange();

        assertOutcome(response, HttpStatus.FORBIDDEN);
    }

    @Test
    void anUnauthenticatedRequestToApartmentDetailGets401() {
        var response = client.get().uri("/api/v1/apartamentos/" + apartmentId).exchange();

        assertOutcome(response, HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest(name = "login como {0} emite un token con ese rol")
    @MethodSource("everyRole")
    void everyRoleCanLogInAndReceivesATokenCarryingItsRole(String role) {
        String email = "login-matrix-" + role.toLowerCase() + "-" + uniqueId() + "@example.com";
        String rawPassword = "Str0ngPass!";
        createLoginableUser(email, role, rawPassword);
        String csrf = fetchCsrfToken();

        var result = client.post().uri("/api/v1/auth/login")
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(new LoginRequest(email, rawPassword))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Void.class);

        String accessToken = result.getResponseCookies().getFirst(ACCESS_COOKIE).getValue();
        var claims = jwtTokenProvider.parseClaims(accessToken).orElseThrow();
        assertThat(jwtTokenProvider.rolesOf(claims)).containsExactly(role);
    }

    private static Stream<String> everyRole() {
        return Stream.of("RESIDENTE", "VIGILANTE", "ADMINISTRACION");
    }

    private static String uniqueId() {
        return Long.toString(System.nanoTime(), 36);
    }

    private void assertOutcome(RestTestClient.ResponseSpec response, HttpStatus expectedStatus) {
        response.expectStatus().isEqualTo(expectedStatus);
        if (expectedStatus == HttpStatus.UNAUTHORIZED || expectedStatus == HttpStatus.FORBIDDEN) {
            response.expectBody(ApiError.class).value(body -> {
                assertThat(body.status()).isEqualTo(expectedStatus.value());
                assertThat(body.message()).isNotBlank();
            });
        }
    }

    private String accessCookieForOrNull(String role) {
        return role == null ? null : accessCookieFor(role);
    }

    private String accessCookieFor(String role) {
        String email = role.toLowerCase() + "-" + uniqueId() + "@example.com";
        Long userId = createUserWithRole(email, role);
        return accessCookieForExistingUser(userId, role);
    }

    private String accessCookieForExistingUser(Long userId, String role) {
        String email = emailOf(userId);
        return authTokenService.issueTokens(userId, email, List.of(role)).accessCookie().getValue();
    }

    private Long createUserWithRole(String email, String role) {
        jdbcTemplate.update(
                "INSERT INTO users (first_name, last_name, document_number, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                "Test", "User", "DOC-" + uniqueId(), email, TestPasswords.UNUSABLE_BCRYPT_HASH);
        return linkRole(email, role);
    }

    private Long createLoginableUser(String email, String role, String rawPassword) {
        jdbcTemplate.update(
                "INSERT INTO users (first_name, last_name, document_number, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                "Test", "User", "DOC-" + uniqueId(), email, passwordEncoder.encode(rawPassword));
        return linkRole(email, role);
    }

    private Long linkRole(String email, String role) {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = ?", Long.class, role);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    private String emailOf(Long userId) {
        return jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
    }

    private Long insertApartment() {
        String numero = "M" + uniqueId();
        jdbcTemplate.update(
                "INSERT INTO apartments (torre, numero, piso, activo) VALUES (?, ?, ?, ?)", "M", numero, 1, true);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM apartments WHERE torre = ? AND numero = ?", Long.class, "M", numero);
    }

    private Long createArrendatarioFixture(Long apartmentId) {
        String admin = accessCookieFor("ADMINISTRACION");
        String csrf = fetchCsrfToken();

        ArrendatarioResponse created = client.post().uri("/api/v1/apartamentos/" + apartmentId + "/arrendatarios")
                .cookie(ACCESS_COOKIE, admin)
                .cookie(CSRF_COOKIE, csrf)
                .header(CSRF_HEADER, csrf)
                .body(anArrendatarioRequest("fixture-" + uniqueId()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ArrendatarioResponse.class)
                .returnResult()
                .getResponseBody();

        return created.arrendatarioId();
    }

    private String fetchCsrfToken() {
        var result = client.get().uri("/api/v1/apartamentos").exchange().returnResult(Void.class);
        return result.getResponseCookies().getFirst(CSRF_COOKIE).getValue();
    }

    private ApartmentRequest anApartmentRequest() {
        String suffix = uniqueId();
        return new ApartmentRequest(
                "Z",
                suffix,
                1,
                new BigDecimal("0.0100"),
                new BigDecimal("50.00"),
                new PropietarioRequest("Ana", "Perez", "DOC-" + suffix, "owner-" + suffix + "@example.com", "3000000000"));
    }

    private ArrendatarioRequest anArrendatarioRequest(String suffix) {
        return new ArrendatarioRequest("Luis", "Marin", "DOC-ARR-" + suffix, "arr-" + suffix + "@example.com", "3009999999");
    }
}
