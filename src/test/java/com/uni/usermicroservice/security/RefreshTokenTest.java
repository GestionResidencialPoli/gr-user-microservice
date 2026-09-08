package com.uni.usermicroservice.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    private RefreshToken tokenExpiringAt(Instant expiresAt) {
        return new RefreshToken(null, "hash", expiresAt);
    }

    @Test
    void aFreshTokenIsActive() {
        assertThat(tokenExpiringAt(Instant.now().plus(7, ChronoUnit.DAYS)).isActive()).isTrue();
    }

    @Test
    void aTokenPastItsExpirationIsNotActive() {
        assertThat(tokenExpiringAt(Instant.now().minusSeconds(1)).isActive()).isFalse();
    }

    @Test
    void aRevokedTokenIsNotActiveEvenBeforeExpiring() {
        RefreshToken token = tokenExpiringAt(Instant.now().plus(7, ChronoUnit.DAYS));
        token.setRevokedAt(Instant.now());

        assertThat(token.isActive()).isFalse();
    }

    @Test
    void aTokenThatIsBothRevokedAndExpiredIsNotActive() {
        RefreshToken token = tokenExpiringAt(Instant.now().minus(1, ChronoUnit.DAYS));
        token.setRevokedAt(Instant.now().minus(2, ChronoUnit.DAYS));

        assertThat(token.isActive()).isFalse();
    }
}
