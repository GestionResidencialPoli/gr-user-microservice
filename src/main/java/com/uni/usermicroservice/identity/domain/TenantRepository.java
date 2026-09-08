package com.uni.usermicroservice.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    boolean existsByUserIdAndEndDateIsNull(Long userId);

    boolean existsByUserIdAndApartmentIdAndEndDateIsNull(Long userId, Long apartmentId);

    Optional<Tenant> findByIdAndApartmentIdAndEndDateIsNull(Long id, Long apartmentId);

    @Query("""
            select t from Tenant t
            join fetch t.apartment
            where t.user.id = :userId
            and t.endDate is null
            """)
    Optional<Tenant> findActiveByUserId(@Param("userId") Long userId);

    @Query("""
            select t from Tenant t
            join fetch t.user
            where t.apartment.id = :apartmentId
            and t.endDate is null
            order by t.startDate asc, t.id asc
            """)
    List<Tenant> findActiveWithUserByApartmentId(@Param("apartmentId") Long apartmentId);
}
