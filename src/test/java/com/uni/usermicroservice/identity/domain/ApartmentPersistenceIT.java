package com.uni.usermicroservice.identity.domain;

import com.uni.usermicroservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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
    void rejectsDuplicateDocumentNumber() {
        User original = IdentityTestDataFactory.aUser();
        entityManager.persist(original);
        entityManager.flush();

        User duplicate = IdentityTestDataFactory.aUser();
        duplicate.setDocumentNumber(original.getDocumentNumber());

        assertThatThrownBy(() -> {
            entityManager.persist(duplicate);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    void rejectsBlankDocumentNumber() {
        User user = IdentityTestDataFactory.aUser();
        user.setDocumentNumber("   ");

        assertThatThrownBy(() -> {
            entityManager.persist(user);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1.5"})
    void rejectsCoeficienteCopropiedadOutOfRange(String coeficiente) {
        Apartment apartment = IdentityTestDataFactory.anApartment();
        apartment.setCoeficienteCopropiedad(new BigDecimal(coeficiente));

        assertThatThrownBy(() -> {
            entityManager.persist(apartment);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1.5"})
    void rejectsNonPositiveArea(String area) {
        Apartment apartment = IdentityTestDataFactory.anApartment();
        apartment.setArea(new BigDecimal(area));

        assertThatThrownBy(() -> {
            entityManager.persist(apartment);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    void acceptsCoeficienteCopropiedadAndAreaWithinRange() {
        Apartment apartment = IdentityTestDataFactory.anApartment();
        apartment.setCoeficienteCopropiedad(new BigDecimal("0.0123"));
        apartment.setArea(new BigDecimal("65.50"));

        entityManager.persist(apartment);
        entityManager.flush();
        entityManager.clear();

        Apartment found = entityManager.find(Apartment.class, apartment.getId());

        assertThat(found.getCoeficienteCopropiedad()).isEqualByComparingTo("0.0123");
        assertThat(found.getArea()).isEqualByComparingTo("65.50");
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
