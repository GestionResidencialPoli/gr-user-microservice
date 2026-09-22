package com.uni.usermicroservice.security;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminSsoCodeRepository extends JpaRepository<AdminSsoCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select code from AdminSsoCode code join fetch code.user where code.codeHash = :codeHash")
    Optional<AdminSsoCode> findByCodeHashForUpdate(@Param("codeHash") String codeHash);
}
