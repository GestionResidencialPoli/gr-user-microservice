package com.uni.usermicroservice.identity.domain;

import com.uni.usermicroservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeedRolesIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void flywaySeedsTheThreeDefaultRoles() {
        TypedQuery<String> query = entityManager.createQuery(
                "select r.name from Role r order by r.name", String.class);

        List<String> roleNames = query.getResultList();

        assertThat(roleNames).containsExactlyInAnyOrder(
                "ADMINISTRACION", "RESIDENTE", "VIGILANTE"
        );
    }
}
