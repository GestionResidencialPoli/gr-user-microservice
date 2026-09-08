package com.uni.usermicroservice.identity.domain;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ResidencyTypeServiceTest {

    private static final long USER_ID = 42L;

    private final OwnerRepository ownerRepository = Mockito.mock(OwnerRepository.class);
    private final TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
    private final ResidencyTypeService service = new ResidencyTypeService(ownerRepository, tenantRepository);

    private Apartment anApartment(String torre, String numero) {
        return new Apartment(torre, numero, 1, null, null);
    }

    private void givenOwns(Apartment apartment) {
        Owner ownership = new Owner(null, apartment, true);
        Mockito.when(ownerRepository.findByUserId(USER_ID)).thenReturn(List.of(ownership));
    }

    private void givenOwnsNothing() {
        Mockito.when(ownerRepository.findByUserId(USER_ID)).thenReturn(List.of());
    }

    private void givenRents(Apartment apartment) {
        Tenant tenancy = new Tenant(null, apartment, LocalDate.now());
        Mockito.when(tenantRepository.findActiveByUserId(USER_ID)).thenReturn(Optional.of(tenancy));
    }

    private void givenRentsNothing() {
        Mockito.when(tenantRepository.findActiveByUserId(USER_ID)).thenReturn(Optional.empty());
    }

    @Test
    void someoneWhoOwnsAnApartmentIsAnOwner() {
        givenOwns(anApartment("A", "101"));

        assertThat(service.tipoResidenteOf(USER_ID)).contains(TipoResidente.PROPIETARIO);
    }

    @Test
    void someoneWithAnActiveTenancyIsATenant() {
        givenOwnsNothing();
        givenRents(anApartment("B", "202"));

        assertThat(service.tipoResidenteOf(USER_ID)).contains(TipoResidente.ARRENDATARIO);
    }

    @Test
    void owningTakesPrecedenceOverRentingWhenSomeoneIsBoth() {
        givenOwns(anApartment("A", "101"));
        givenRents(anApartment("B", "202"));

        assertThat(service.tipoResidenteOf(USER_ID)).contains(TipoResidente.PROPIETARIO);
    }

    @Test
    void someoneWithNoResidentialLinkHasNoType() {
        givenOwnsNothing();
        givenRentsNothing();

        assertThat(service.tipoResidenteOf(USER_ID)).isEmpty();
    }

    @Test
    void theTenancyIsNotLookedUpWhenThePersonAlreadyOwnsSomething() {
        givenOwns(anApartment("A", "101"));

        service.tipoResidenteOf(USER_ID);

        Mockito.verify(tenantRepository, Mockito.never()).findActiveByUserId(Mockito.anyLong());
    }

    @Test
    void theSummaryCarriesTheTowerAndNumberOfTheOwnedApartment() {
        givenOwns(anApartment("A", "101"));

        assertThat(service.apartmentOf(USER_ID))
                .contains(new ApartmentSummary("A", "101", TipoResidente.PROPIETARIO));
    }

    @Test
    void theSummaryCarriesTheTowerAndNumberOfTheRentedApartment() {
        givenOwnsNothing();
        givenRents(anApartment("B", "202"));

        assertThat(service.apartmentOf(USER_ID))
                .contains(new ApartmentSummary("B", "202", TipoResidente.ARRENDATARIO));
    }

    @Test
    void thereIsNoSummaryForSomeoneWithNoResidentialLink() {
        givenOwnsNothing();
        givenRentsNothing();

        assertThat(service.apartmentOf(USER_ID)).isEmpty();
    }
}
