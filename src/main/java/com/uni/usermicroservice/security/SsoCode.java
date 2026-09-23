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

/**
 * Codigo de un solo uso para el puente SSO entre aplicaciones de rol. El
 * nombre de la tabla ("admin_sso_codes") es historico: nacio solo para
 * administracion en GR-30 y no se renombro al generalizarse en GR-151 porque
 * no hay ninguna razon de esquema para hacerlo, solo cosmetica.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "admin_sso_codes", uniqueConstraints = @UniqueConstraint(name = "uk_admin_sso_codes_code_hash", columnNames = "code_hash"))
public class SsoCode {

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

    public SsoCode(User user, String codeHash, String audience, Instant expiresAt) {
        this.user = user;
        this.codeHash = codeHash;
        this.audience = audience;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
