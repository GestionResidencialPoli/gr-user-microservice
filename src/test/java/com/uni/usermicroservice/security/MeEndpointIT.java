package com.uni.usermicroservice.security;

import com.uni.usermicroservice.UserMicroserviceApplication;
import com.uni.usermicroservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = UserMicroserviceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class MeEndpointIT extends AbstractIntegrationTest {

    private static final String ACCESS_COOKIE = "access_token";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthTokenService authTokenService;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void returnsTheIdentityAndRolesOfTheAuthenticatedUser() {
        String email = "me-endpoint@example.com";
        jdbcTemplate.update(
                "INSERT INTO users (first_name, last_name, document_number, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                "Camila", "Restrepo", "DOC-ME-1", email, "irrelevant-hash-for-this-test");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = ?", Long.class, "RESIDENTE");
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
        String accessCookie = authTokenService.issueTokens(userId, email, List.of("RESIDENTE")).accessCookie().getValue();

        var body = client.get().uri("/api/v1/auth/me")
                .cookie(ACCESS_COOKIE, accessCookie)
                .exchange()
                .expectStatus().isOk()
                .expectBody(MeResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.id()).isEqualTo(userId);
        assertThat(body.email()).isEqualTo(email);
        assertThat(body.firstName()).isEqualTo("Camila");
        assertThat(body.lastName()).isEqualTo("Restrepo");
        assertThat(body.roles()).containsExactly("RESIDENTE");
    }

    @Test
    void anUnauthenticatedRequestGets401() {
        client.get().uri("/api/v1/auth/me")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
