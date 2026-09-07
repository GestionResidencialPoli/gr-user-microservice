package com.uni.usermicroservice.identity.domain;

import com.uni.usermicroservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class ApartmentPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAnApartmentAndReadsItBack() {
        Apartment apartment = IdentityTestDataFactory.anApartment();

        entityManager.persist(apartment);
        entityManager.flush();
        entityManager.clear();

        Apartment found = entityManager.find(Apartment.class, apartment.getId());

        assertThat(found).isNotNull();
        assertThat(found.getTorre()).isEqualTo(apartment.getTorre());
        assertThat(found.isActivo()).isTrue();
    }

    @Test
    void rejectsDuplicateTorreAndNumero() {
        Apartment original = IdentityTestDataFactory.anApartment();
        entityManager.persist(original);
        entityManager.flush();

        Apartment duplicate = IdentityTestDataFactory.anApartment();
        duplicate.setTorre(original.getTorre());
        duplicate.setNumero(original.getNumero());

        assertThatThrownBy(() -> {
            entityManager.persist(duplicate);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    void persistsAnOwnerLinkedToUserAndApartment() {
        User user = IdentityTestDataFactory.aUser();
        Apartment apartment = IdentityTestDataFactory.anApartment();
        entityManager.persist(user);
        entityManager.persist(apartment);

        Owner owner = IdentityTestDataFactory.anOwner(user, apartment);
        entityManager.persist(owner);
        entityManager.flush();
        entityManager.clear();

        Owner found = entityManager.find(Owner.class, owner.getId());

        assertThat(found.getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getApartment().getId()).isEqualTo(apartment.getId());
    }
}
