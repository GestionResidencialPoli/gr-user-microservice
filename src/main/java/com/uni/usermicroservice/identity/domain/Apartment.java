package com.uni.usermicroservice.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "apartments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_apartments_torre_numero",
                columnNames = {"torre", "numero"}
        )
)
public class Apartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String torre;

    @Column(nullable = false, length = 20)
    private String numero;

    @Column
    private Integer piso;

    @Column(nullable = false)
    private boolean activo = true;

    @OneToMany(mappedBy = "apartment", fetch = FetchType.LAZY)
    private Set<Owner> owners = new HashSet<>();

    @OneToMany(mappedBy = "apartment", fetch = FetchType.LAZY)
    private Set<Tenant> tenants = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
