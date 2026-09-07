package com.uni.usermicroservice.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OwnerRepository extends JpaRepository<Owner, Long> {

    Optional<Owner> findByApartmentIdAndPrincipalTrue(Long apartmentId);

    /**
     * Carga en una sola consulta los propietarios principales de varios apartamentos, con su usuario
     * ya resuelto, para que un listado paginado no dispare una consulta por fila.
     */
    @Query("""
            select o from Owner o
            join fetch o.user
            where o.principal = true
            and o.apartment.id in :apartmentIds
            """)
    List<Owner> findPrincipalsWithUserByApartmentIdIn(@Param("apartmentIds") Collection<Long> apartmentIds);
}
