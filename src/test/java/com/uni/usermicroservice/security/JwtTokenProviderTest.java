package com.uni.usermicroservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "a-secret-of-at-least-32-characters-long";

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(new JwtProperties(SECRET, 15, 7));

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
                new JwtProperties("a-completely-different-secret-key-value", 15, 7)
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

        assertThat(jwtTokenProvider.parseClaims(expiredToken)).isEmpty();
    }
}
