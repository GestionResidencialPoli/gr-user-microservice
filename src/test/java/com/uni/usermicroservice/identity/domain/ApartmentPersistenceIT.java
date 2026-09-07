package com.uni.usermicroservice.identity.domain;

import com.uni.usermicroservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cada prueba corre en su propia transaccion, que se revierte al terminar,
 * por lo que ninguna depende del orden de ejecucion ni del estado dejado
 * por otra. Extiende {@link AbstractIntegrationTest} y por lo tanto reutiliza
 * el mismo contenedor de PostgreSQL que las demas pruebas de integracion.
 */
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

        // La entidad usa GenerationType.IDENTITY: Hibernate ejecuta el INSERT
        // de inmediato en persist(), no al hacer flush().
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
