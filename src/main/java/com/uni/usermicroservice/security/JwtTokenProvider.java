package com.uni.usermicroservice.security;

import com.uni.usermicroservice.identity.domain.TipoResidente;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Component
public class JwtTokenProvider {

    private static final String ROLES_CLAIM = "roles";
    private static final String USER_ID_CLAIM = "uid";
    private static final String TIPO_RESIDENTE_CLAIM = "tipoResidente";
    private static final String NESTED_JWT_CONTENT_TYPE = "JWT";

    private final SecretKey signingKey;
    private final SecretKey encryptionKey;
    private final long accessTokenExpirationMinutes;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.encryptionKey = new SecretKeySpec(Base64.getUrlDecoder().decode(jwtProperties.encryptionKey()), "AES");
        this.accessTokenExpirationMinutes = jwtProperties.accessTokenExpirationMinutes();
    }

    public String generateAccessToken(Long userId, String subject, Collection<String> roles) {
        return generateAccessToken(userId, subject, roles, null);
    }

    public String generateAccessToken(Long userId, String subject, Collection<String> roles, TipoResidente tipoResidente) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(subject)
                .claim(USER_ID_CLAIM, userId)
                .claim(ROLES_CLAIM, roles);

        if (tipoResidente != null) {
            builder.claim(TIPO_RESIDENTE_CLAIM, tipoResidente.name());
        }

        String signedToken = builder
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpirationMinutes * 60)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return Jwts.builder()
                .content(signedToken.getBytes(StandardCharsets.UTF_8), NESTED_JWT_CONTENT_TYPE)
                .encryptWith(encryptionKey, Jwts.ENC.A256GCM)
                .compact();
    }

    public Optional<Claims> parseClaims(String token) {
        try {
            byte[] signedToken = Jwts.parser()
                    .decryptWith(encryptionKey)
                    .build()
                    .parseEncryptedContent(token)
                    .getPayload();
            return Optional.of(
                    Jwts.parser()
                            .verifyWith(signingKey)
                            .build()
                            .parseSignedClaims(new String(signedToken, StandardCharsets.UTF_8))
                            .getPayload()
            );
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> rolesOf(Claims claims) {
        return (List<String>) claims.get(ROLES_CLAIM, List.class);
    }

    public Long userIdOf(Claims claims) {
        return claims.get(USER_ID_CLAIM, Long.class);
    }

    public Optional<TipoResidente> tipoResidenteOf(Claims claims) {
        String value = claims.get(TIPO_RESIDENTE_CLAIM, String.class);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(TipoResidente.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
