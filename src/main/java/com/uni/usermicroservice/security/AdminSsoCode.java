package com.uni.usermicroservice.security;

import com.uni.usermicroservice.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "admin_sso_codes", uniqueConstraints = @UniqueConstraint(name = "uk_admin_sso_codes_code_hash", columnNames = "code_hash"))
public class AdminSsoCode {

    public static final String ADMIN_AUDIENCE = "admin";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false, length = 30)
    private String audience;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AdminSsoCode(User user, String codeHash, Instant expiresAt) {
        this.user = user;
        this.codeHash = codeHash;
        this.audience = ADMIN_AUDIENCE;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return ADMIN_AUDIENCE.equals(audience) && usedAt == null && expiresAt.isAfter(now);
    }
}
