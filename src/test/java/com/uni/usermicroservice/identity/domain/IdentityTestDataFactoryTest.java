package com.uni.usermicroservice.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityTestDataFactoryTest {

    @Test
    void buildsUsersWithUniqueEmails() {
        User first = IdentityTestDataFactory.aUser();
        User second = IdentityTestDataFactory.aUser();

        assertThat(first.getEmail()).isNotBlank().isNotEqualTo(second.getEmail());
        assertThat(first.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void buildsApartmentsWithUniqueTorreAndNumero() {
        Apartment first = IdentityTestDataFactory.anApartment();
        Apartment second = IdentityTestDataFactory.anApartment();

        assertThat(first.getTorre()).isNotEqualTo(second.getTorre());
        assertThat(first.getNumero()).isNotEqualTo(second.getNumero());
        assertThat(first.isActivo()).isTrue();
    }

    @Test
    void buildsOwnerLinkedToItsUserAndApartment() {
        User user = IdentityTestDataFactory.aUser();
        Apartment apartment = IdentityTestDataFactory.anApartment();

        Owner owner = IdentityTestDataFactory.anOwner(user, apartment);

        assertThat(owner.getUser()).isSameAs(user);
        assertThat(owner.getApartment()).isSameAs(apartment);
        assertThat(owner.isPrincipal()).isTrue();
    }

    @Test
    void buildsTenantWithGivenStartDate() {
        User user = IdentityTestDataFactory.aUser();
        Apartment apartment = IdentityTestDataFactory.anApartment();
        LocalDate startDate = LocalDate.of(2026, 1, 1);

        Tenant tenant = IdentityTestDataFactory.aTenant(user, apartment, startDate);

        assertThat(tenant.getStartDate()).isEqualTo(startDate);
        assertThat(tenant.getEndDate()).isNull();
    }
}
