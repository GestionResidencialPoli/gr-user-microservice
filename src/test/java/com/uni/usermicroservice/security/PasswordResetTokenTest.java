package com.uni.usermicroservice.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private PasswordResetToken tokenExpiringAt(Instant expiresAt) {
        return new PasswordResetToken(null, "hash", expiresAt);
    }

    @Test
    void aNewlyIssuedTokenIsNeitherUsedNorExpired() {
        PasswordResetToken token = tokenExpiringAt(NOW.plus(30, ChronoUnit.MINUTES));

        assertThat(token.isUsed()).isFalse();
        assertThat(token.isExpired(NOW)).isFalse();
    }

    @Test
    void aTokenBecomesUsedOnceItsConsumptionIsRecorded() {
        PasswordResetToken token = tokenExpiringAt(NOW.plus(30, ChronoUnit.MINUTES));
        token.setUsedAt(NOW);

        assertThat(token.isUsed()).isTrue();
    }

    @Test
    void aTokenIsStillValidOneSecondBeforeItsExpiration() {
        PasswordResetToken token = tokenExpiringAt(NOW.plusSeconds(1));

        assertThat(token.isExpired(NOW)).isFalse();
    }

    @Test
    void aTokenIsExpiredOneSecondAfterItsExpiration() {
        PasswordResetToken token = tokenExpiringAt(NOW.minusSeconds(1));

        assertThat(token.isExpired(NOW)).isTrue();
    }

    @Test
    void aTokenIsNotYetExpiredExactlyAtItsExpirationInstant() {
        PasswordResetToken token = tokenExpiringAt(NOW);

        assertThat(token.isExpired(NOW)).isFalse();
    }

    @Test
    void expirationAndConsumptionAreIndependentConditions() {
        PasswordResetToken token = tokenExpiringAt(NOW.minus(1, ChronoUnit.HOURS));
        token.setUsedAt(NOW.minus(2, ChronoUnit.HOURS));

        assertThat(token.isUsed()).isTrue();
        assertThat(token.isExpired(NOW)).isTrue();
    }
}
