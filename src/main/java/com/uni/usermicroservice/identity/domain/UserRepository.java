package com.uni.usermicroservice.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByDocumentNumber(String documentNumber);

    Optional<User> findByEmail(String email);

    @Query("""
            select u from User u
            join u.roles r
            where r.name = :roleName
            order by u.lastName asc, u.firstName asc
            """)
    List<User> findByRoleName(@Param("roleName") String roleName);

    @Query("""
            select u from User u
            join u.roles r
            where u.id = :userId and r.name = :roleName
            """)
    Optional<User> findByIdAndRoleName(@Param("userId") Long userId, @Param("roleName") String roleName);

    @Query("select u.status from User u where u.id = :userId")
    Optional<UserStatus> findStatusById(@Param("userId") Long userId);
}
