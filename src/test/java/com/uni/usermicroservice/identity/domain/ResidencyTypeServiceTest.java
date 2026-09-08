package com.uni.usermicroservice.identity.domain;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class ResidencyTypeServiceTest {

    private static final long USER_ID = 42L;

    private final OwnerRepository ownerRepository = Mockito.mock(OwnerRepository.class);
    private final TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
    private final ResidencyTypeService service = new ResidencyTypeService(ownerRepository, tenantRepository);

    private void given(boolean owns, boolean rents) {
        Mockito.when(ownerRepository.existsByUserId(USER_ID)).thenReturn(owns);
        Mockito.when(tenantRepository.existsByUserIdAndEndDateIsNull(USER_ID)).thenReturn(rents);
    }

    @Test
    void someoneWhoOwnsAnApartmentIsAnOwner() {
        given(true, false);

        assertThat(service.tipoResidenteOf(USER_ID)).contains(TipoResidente.PROPIETARIO);
    }

    @Test
    void someoneWithAnActiveTenancyIsATenant() {
        given(false, true);

        assertThat(service.tipoResidenteOf(USER_ID)).contains(TipoResidente.ARRENDATARIO);
    }

    @Test
    void owningTakesPrecedenceOverRentingWhenSomeoneIsBoth() {
        given(true, true);

        assertThat(service.tipoResidenteOf(USER_ID)).contains(TipoResidente.PROPIETARIO);
    }

    @Test
    void someoneWithNoResidentialLinkHasNoType() {
        given(false, false);

        assertThat(service.tipoResidenteOf(USER_ID)).isEmpty();
    }

    @Test
    void theTenantRepositoryIsNotQueriedWhenThePersonAlreadyOwnsSomething() {
        given(true, false);

        service.tipoResidenteOf(USER_ID);

        Mockito.verify(tenantRepository, Mockito.never()).existsByUserIdAndEndDateIsNull(Mockito.anyLong());
    }
}
