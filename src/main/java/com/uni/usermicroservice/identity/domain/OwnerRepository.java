package com.uni.usermicroservice.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OwnerRepository extends JpaRepository<Owner, Long> {

    Optional<Owner> findByApartmentIdAndPrincipalTrue(Long apartmentId);

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndApartmentId(Long userId, Long apartmentId);

    @Query("""
            select o from Owner o
            join fetch o.user
            where o.principal = true
            and o.apartment.id in :apartmentIds
            """)
    List<Owner> findPrincipalsWithUserByApartmentIdIn(@Param("apartmentIds") Collection<Long> apartmentIds);
}
