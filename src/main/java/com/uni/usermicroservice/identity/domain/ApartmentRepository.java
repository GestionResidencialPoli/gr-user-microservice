package com.uni.usermicroservice.identity.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApartmentRepository extends JpaRepository<Apartment, Long> {

    boolean existsByTorreAndNumero(String torre, String numero);

    boolean existsByTorreAndNumeroAndIdNot(String torre, String numero, Long id);

    @Query("""
            select a from Apartment a
            where a.activo = true
            and (:torrePattern is null or lower(a.torre) like :torrePattern)
            and (:numeroPattern is null or lower(a.numero) like :numeroPattern)
            """)
    Page<Apartment> search(
            @Param("torrePattern") String torrePattern,
            @Param("numeroPattern") String numeroPattern,
            Pageable pageable
    );
}
