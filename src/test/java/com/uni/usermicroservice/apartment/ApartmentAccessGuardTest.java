package com.uni.usermicroservice.apartment;

import com.uni.usermicroservice.identity.domain.OwnerRepository;
import com.uni.usermicroservice.identity.domain.TenantRepository;
import com.uni.usermicroservice.identity.domain.User;
import com.uni.usermicroservice.identity.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ApartmentAccessGuardTest {

    private static final long APARTMENT_ID = 10L;
    private static final long USER_ID = 5L;
    private static final String EMAIL = "residente@example.com";

    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private final OwnerRepository ownerRepository = Mockito.mock(OwnerRepository.class);
    private final TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
    private final ApartmentAccessGuard guard =
            new ApartmentAccessGuard(userRepository, ownerRepository, tenantRepository);

    private Authentication authenticatedAs(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of());
    }

    private void givenUserExists() {
        User user = Mockito.mock(User.class);
        Mockito.when(user.getId()).thenReturn(USER_ID);
        Mockito.when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private void givenLinks(boolean owns, boolean rents) {
        Mockito.when(ownerRepository.existsByUserIdAndApartmentId(USER_ID, APARTMENT_ID)).thenReturn(owns);
        Mockito.when(tenantRepository.existsByUserIdAndApartmentIdAndEndDateIsNull(USER_ID, APARTMENT_ID))
                .thenReturn(rents);
    }

    @Test
    void theOwnerOfTheApartmentCanViewIt() {
        givenUserExists();
        givenLinks(true, false);

        assertThat(guard.canView(APARTMENT_ID, authenticatedAs(EMAIL))).isTrue();
    }

    @Test
    void theActiveTenantOfTheApartmentCanViewIt() {
        givenUserExists();
        givenLinks(false, true);

        assertThat(guard.canView(APARTMENT_ID, authenticatedAs(EMAIL))).isTrue();
    }

    @Test
    void someoneWithNoLinkToTheApartmentCannotViewIt() {
        givenUserExists();
        givenLinks(false, false);

        assertThat(guard.canView(APARTMENT_ID, authenticatedAs(EMAIL))).isFalse();
    }

    @Test
    void anUnauthenticatedCallerCannotViewAnything() {
        assertThat(guard.canView(APARTMENT_ID, null)).isFalse();
    }

    @Test
    void aTokenWhoseSubjectNoLongerExistsCannotViewAnything() {
        Mockito.when(userRepository.findByEmail("borrado@example.com")).thenReturn(Optional.empty());

        assertThat(guard.canView(APARTMENT_ID, authenticatedAs("borrado@example.com"))).isFalse();
    }

    @Test
    void aFormerTenantWhoseTenancyAlreadyEndedCannotViewTheApartment() {
        givenUserExists();
        givenLinks(false, false);

        assertThat(guard.canView(APARTMENT_ID, authenticatedAs(EMAIL))).isFalse();
        Mockito.verify(tenantRepository).existsByUserIdAndApartmentIdAndEndDateIsNull(USER_ID, APARTMENT_ID);
    }
}
