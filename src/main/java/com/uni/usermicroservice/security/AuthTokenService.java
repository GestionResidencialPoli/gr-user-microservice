package com.uni.usermicroservice.security;

import com.uni.usermicroservice.identity.domain.Role;
import com.uni.usermicroservice.identity.domain.User;
import jakarta.persistence.EntityManager;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AuthTokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final CookieProperties cookieProperties;
    private final EntityManager entityManager;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthTokenService(
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository,
            JwtProperties jwtProperties,
            CookieProperties cookieProperties,
            EntityManager entityManager
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.cookieProperties = cookieProperties;
        this.entityManager = entityManager;
    }

    public record IssuedTokens(ResponseCookie accessCookie, ResponseCookie refreshCookie) {
    }

    @Transactional
    public IssuedTokens issueTokens(Long userId, String email, Collection<String> roles) {
        String accessToken = jwtTokenProvider.generateAccessToken(userId, email, roles);

        String rawRefreshToken = newRawToken();
        Instant expiresAt = Instant.now().plus(jwtProperties.refreshTokenExpirationDays(), ChronoUnit.DAYS);
        User userReference = entityManager.getReference(User.class, userId);
        refreshTokenRepository.save(new RefreshToken(userReference, hash(rawRefreshToken), expiresAt));

        return new IssuedTokens(buildAccessCookie(accessToken), buildRefreshCookie(rawRefreshToken));
    }

    @Transactional
    public Optional<IssuedTokens> rotate(String rawRefreshToken) {
        return findActive(rawRefreshToken).map(existing -> {
            existing.setRevokedAt(Instant.now());
            User user = existing.getUser();
            var roles = user.getRoles().stream().map(Role::getName).toList();
            return issueTokens(user.getId(), user.getEmail(), roles);
        });
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        findActive(rawRefreshToken).ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    public ResponseCookie clearedAccessCookie() {
        return ResponseCookie.from(cookieProperties.accessTokenName(), "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    public ResponseCookie clearedRefreshCookie() {
        return ResponseCookie.from(cookieProperties.refreshTokenName(), "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(cookieProperties.refreshTokenPath())
                .maxAge(Duration.ZERO)
                .build();
    }

    private Optional<RefreshToken> findActive(String rawRefreshToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .filter(RefreshToken::isActive);
    }

    private ResponseCookie buildAccessCookie(String accessToken) {
        return ResponseCookie.from(cookieProperties.accessTokenName(), accessToken)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path("/")
                .maxAge(Duration.ofMinutes(jwtProperties.accessTokenExpirationMinutes()))
                .build();
    }

    private ResponseCookie buildRefreshCookie(String rawRefreshToken) {
        return ResponseCookie.from(cookieProperties.refreshTokenName(), rawRefreshToken)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(cookieProperties.refreshTokenPath())
                .maxAge(Duration.ofDays(jwtProperties.refreshTokenExpirationDays()))
                .build();
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", ex);
        }
    }
}
