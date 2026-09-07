package com.uni.usermicroservice.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OwnerRepository extends JpaRepository<Owner, Long> {

    Optional<Owner> findByApartmentIdAndPrincipalTrue(Long apartmentId);
}
