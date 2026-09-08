package com.uni.usermicroservice.security;

import com.uni.usermicroservice.identity.domain.User;
import com.uni.usermicroservice.identity.domain.UserRepository;
import com.uni.usermicroservice.identity.domain.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class PasswordResetService {

    static final long TOKEN_VALIDITY_MINUTES = 30;

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = email.strip().toLowerCase();

        userRepository.findByEmail(normalizedEmail)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(this::issueTokenFor);
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(AuthTokenService.hash(rawToken))
                .orElseThrow(PasswordResetTokenInvalidException::notUsable);

        if (token.isUsed()) {
            throw PasswordResetTokenInvalidException.notUsable();
        }

        Instant now = Instant.now();
        if (token.isExpired(now)) {
            throw PasswordResetTokenInvalidException.expired();
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(now);

        revokeActiveSessionsOf(user, now);
    }

    private void issueTokenFor(User user) {
        Instant now = Instant.now();

        passwordResetTokenRepository.findByUserIdAndUsedAtIsNull(user.getId())
                .forEach(pending -> pending.setUsedAt(now));

        String rawToken = newRawToken();
        Instant expiresAt = now.plus(TOKEN_VALIDITY_MINUTES, ChronoUnit.MINUTES);
        passwordResetTokenRepository.save(
                new PasswordResetToken(user, AuthTokenService.hash(rawToken), expiresAt));

        log.info(
                "Token de restablecimiento emitido para el usuario {}. Vence en {} minutos. Token: {}",
                user.getId(), TOKEN_VALIDITY_MINUTES, rawToken);
    }

    private void revokeActiveSessionsOf(User user, Instant now) {
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId())
                .forEach(refreshToken -> refreshToken.setRevokedAt(now));
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
