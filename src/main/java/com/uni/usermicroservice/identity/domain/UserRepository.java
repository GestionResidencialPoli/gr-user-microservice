package com.uni.usermicroservice.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByDocumentNumber(String documentNumber);

    Optional<User> findByEmail(String email);
}
