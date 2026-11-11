package com.uni.usermicroservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "a-secret-of-at-least-32-characters-long";
    private static final String ENCRYPTION_KEY = "Y2xhdmUtZGUtcHJ1ZWJhcy1kZS0zMi1ieXRlcy1vayE";

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(new JwtProperties(SECRET, ENCRYPTION_KEY, 15, 7));

    @Test
    void generatesAndParsesAValidToken() {
        String token = jwtTokenProvider.generateAccessToken(42L, "resident@example.com", List.of("RESIDENTE"));

        Optional<Claims> claims = jwtTokenProvider.parseClaims(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("resident@example.com");
        assertThat(jwtTokenProvider.userIdOf(claims.get())).isEqualTo(42L);
        assertThat(jwtTokenProvider.rolesOf(claims.get())).containsExactly("RESIDENTE");
    }

    @Test
    void aValidTokenNeverContainsThePasswordOrItsHash() {
        String token = jwtTokenProvider.generateAccessToken(42L, "resident@example.com", List.of("RESIDENTE"));

        assertThat(jwtTokenProvider.parseClaims(token).get().keySet())
                .doesNotContain("password", "passwordHash", "password_hash");
    }

    @Test
    void rejectsATamperedToken() {
        String token = jwtTokenProvider.generateAccessToken(42L, "resident@example.com", List.of("RESIDENTE"));
        int middle = token.length() / 2;
        char flipped = token.charAt(middle) == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, middle) + flipped + token.substring(middle + 1);

        assertThat(jwtTokenProvider.parseClaims(tampered)).isEmpty();
    }

    @Test
    void rejectsATokenSignedWithADifferentKey() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                new JwtProperties("a-completely-different-secret-key-value", ENCRYPTION_KEY, 15, 7)
        );
        String token = otherProvider.generateAccessToken(42L, "resident@example.com", List.of("RESIDENTE"));

        assertThat(jwtTokenProvider.parseClaims(token)).isEmpty();
    }

    @Test
    void rejectsAnExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(3600);
        String expiredToken = Jwts.builder()
                .subject("resident@example.com")
                .claim("roles", List.of("RESIDENTE"))
                .issuedAt(Date.from(past))
                .expiration(Date.from(past.plusSeconds(60)))
                .signWith(key)
                .compact();

        assertThat(jwtTokenProvider.parseClaims(encrypt(expiredToken))).isEmpty();
    }

    @Test
    void theTokenIsAnEncryptedJweWhoseClaimsAreNotReadable() {
        String token = jwtTokenProvider.generateAccessToken(42L, "resident@example.com", List.of("RESIDENTE"));

        String[] parts = token.split("\\.", -1);
        String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);

        assertThat(parts).hasSize(5);
        assertThat(header).contains("\"alg\":\"dir\"", "\"enc\":\"A256GCM\"", "\"cty\":\"JWT\"");
        assertThat(new String(Base64.getUrlDecoder().decode(parts[3]), StandardCharsets.ISO_8859_1))
                .doesNotContain("resident@example.com", "RESIDENTE");
    }

    @Test
    void rejectsAPlainSignedTokenThatWasNotEncrypted() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String plainToken = Jwts.builder()
                .subject("resident@example.com")
                .claim("uid", 42L)
                .claim("roles", List.of("ADMINISTRACION"))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThat(jwtTokenProvider.parseClaims(plainToken)).isEmpty();
    }

    @Test
    void rejectsATokenEncryptedWithADifferentKey() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                new JwtProperties(SECRET, "b3RyYS1jbGF2ZS1kZS0zMi1ieXRlcy1wYXJhLXRlc3Q", 15, 7)
        );
        String token = otherProvider.generateAccessToken(42L, "resident@example.com", List.of("RESIDENTE"));

        assertThat(jwtTokenProvider.parseClaims(token)).isEmpty();
    }

    private static String encrypt(String signedToken) {
        SecretKey encryptionKey = new SecretKeySpec(Base64.getUrlDecoder().decode(ENCRYPTION_KEY), "AES");
        return Jwts.builder()
                .content(signedToken.getBytes(StandardCharsets.UTF_8), "JWT")
                .encryptWith(encryptionKey, Jwts.ENC.A256GCM)
                .compact();
    }
}
