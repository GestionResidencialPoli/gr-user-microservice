package com.uni.usermicroservice.identity.domain;

import com.uni.usermicroservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class TenantPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ResidencyTypeService residencyTypeService;

    private User persistedUser() {
        User user = IdentityTestDataFactory.aUser();
        entityManager.persist(user);
        return user;
    }

    private Apartment persistedApartment() {
        Apartment apartment = IdentityTestDataFactory.anApartment();
        entityManager.persist(apartment);
        return apartment;
    }

    @Test
    void rejectsASecondActiveTenancyForTheSameUser() {
        User user = persistedUser();
        entityManager.persist(new Tenant(user, persistedApartment(), LocalDate.now().minusMonths(6)));
        entityManager.flush();

        Tenant second = new Tenant(user, persistedApartment(), LocalDate.now());

        assertThatThrownBy(() -> {
            entityManager.persist(second);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    void allowsANewTenancyOnceThePreviousOneEnded() {
        User user = persistedUser();
        Tenant previous = new Tenant(user, persistedApartment(), LocalDate.now().minusMonths(6));
        entityManager.persist(previous);
        entityManager.flush();

        previous.setEndDate(LocalDate.now().minusDays(1));
        entityManager.flush();

        Tenant current = new Tenant(user, persistedApartment(), LocalDate.now());
        entityManager.persist(current);
        entityManager.flush();

        assertThat(current.getId()).isNotNull();
    }

    @Test
    void rejectsAnEndDateBeforeTheStartDate() {
        Tenant tenant = new Tenant(persistedUser(), persistedApartment(), LocalDate.now());
        tenant.setEndDate(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> {
            entityManager.persist(tenant);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    void resolvesArrendatarioForAUserWithAnActiveTenancy() {
        User user = persistedUser();
        entityManager.persist(new Tenant(user, persistedApartment(), LocalDate.now()));
        entityManager.flush();

        assertThat(residencyTypeService.tipoResidenteOf(user.getId()))
                .contains(TipoResidente.ARRENDATARIO);
    }

    @Test
    void resolvesPropietarioForAUserThatOwnsAnApartment() {
        User user = persistedUser();
        entityManager.persist(IdentityTestDataFactory.anOwner(user, persistedApartment()));
        entityManager.flush();

        assertThat(residencyTypeService.tipoResidenteOf(user.getId()))
                .contains(TipoResidente.PROPIETARIO);
    }

    @Test
    void resolvesNothingForAUserWithNoResidentialLink() {
        User user = persistedUser();
        entityManager.flush();

        assertThat(residencyTypeService.tipoResidenteOf(user.getId())).isEmpty();
    }

    @Test
    void stopsBeingArrendatarioOnceTheTenancyEnds() {
        User user = persistedUser();
        Tenant tenant = new Tenant(user, persistedApartment(), LocalDate.now().minusMonths(2));
        entityManager.persist(tenant);
        entityManager.flush();

        tenant.setEndDate(LocalDate.now());
        entityManager.flush();

        assertThat(residencyTypeService.tipoResidenteOf(user.getId())).isEmpty();
    }
}
