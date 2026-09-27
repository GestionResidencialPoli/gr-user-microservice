package com.uni.usermicroservice.internal;

import com.uni.usermicroservice.UserMicroserviceApplication;
import com.uni.usermicroservice.identity.domain.Apartment;
import com.uni.usermicroservice.identity.domain.ApartmentRepository;
import com.uni.usermicroservice.identity.domain.IdentityTestDataFactory;
import com.uni.usermicroservice.identity.domain.OwnerRepository;
import com.uni.usermicroservice.identity.domain.TenantRepository;
import com.uni.usermicroservice.identity.domain.TipoResidente;
import com.uni.usermicroservice.identity.domain.User;
import com.uni.usermicroservice.identity.domain.UserRepository;
import com.uni.usermicroservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = UserMicroserviceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class InternalApiIT extends AbstractIntegrationTest {

    private static final String TOKEN_HEADER = "X-Internal-Token";
    private static final String INTERNAL_TOKEN = "a-secret-of-at-least-32-characters-long";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApartmentRepository apartmentRepository;

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void userOfAnOwnerIncludesTheOwnedApartment() {
        User owner = userRepository.save(IdentityTestDataFactory.aUser());
        Apartment apartment = apartmentRepository.save(IdentityTestDataFactory.anApartment());
        ownerRepository.save(IdentityTestDataFactory.anOwner(owner, apartment));

        InternalUserResponse response = getUser(owner.getId());

        assertThat(response.firstName()).isEqualTo(owner.getFirstName());
        assertThat(response.apartment().id()).isEqualTo(apartment.getId());
        assertThat(response.apartment().torre()).isEqualTo(apartment.getTorre());
        assertThat(response.apartment().activo()).isTrue();
        assertThat(response.apartment().tipoResidente()).isEqualTo(TipoResidente.PROPIETARIO);
    }

    @Test
    void userOfAnActiveTenantIncludesTheRentedApartment() {
        User tenant = userRepository.save(IdentityTestDataFactory.aUser());
        Apartment apartment = apartmentRepository.save(IdentityTestDataFactory.anApartment());
        tenantRepository.save(IdentityTestDataFactory.aTenant(tenant, apartment, LocalDate.now().minusMonths(1)));

        InternalUserResponse response = getUser(tenant.getId());

        assertThat(response.apartment().id()).isEqualTo(apartment.getId());
        assertThat(response.apartment().tipoResidente()).isEqualTo(TipoResidente.ARRENDATARIO);
    }

    @Test
    void userWithoutResidencyKeepsTheExistingContractAndHasNoApartment() {
        User guard = userRepository.save(IdentityTestDataFactory.aUser());

        InternalUserResponse response = getUser(guard.getId());

        assertThat(response.id()).isEqualTo(guard.getId());
        assertThat(response.lastName()).isEqualTo(guard.getLastName());
        assertThat(response.apartment()).isNull();
    }

    @Test
    void apartmentIsFoundByTorreAndNumeroIgnoringCaseAndSurroundingSpaces() {
        Apartment apartment = apartmentRepository.save(IdentityTestDataFactory.anApartment());

        InternalApartmentResponse response = client.get()
                .uri("/api/v1/internal/apartments?torre={torre}&numero={numero}",
                        " " + apartment.getTorre().toLowerCase() + " ", apartment.getNumero())
                .header(TOKEN_HEADER, INTERNAL_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody(InternalApartmentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isEqualTo(InternalApartmentResponse.from(apartment));
    }

    @Test
    void unknownApartmentIsNotFound() {
        client.get()
                .uri("/api/v1/internal/apartments?torre=ZZ&numero=9999")
                .header(TOKEN_HEADER, INTERNAL_TOKEN)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void bothEndpointsRequireTheInternalToken() {
        client.get().uri("/api/v1/internal/users/1").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/internal/apartments?torre=A&numero=101").exchange().expectStatus().isUnauthorized();
    }

    private InternalUserResponse getUser(Long id) {
        return client.get()
                .uri("/api/v1/internal/users/{id}", id)
                .header(TOKEN_HEADER, INTERNAL_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody(InternalUserResponse.class)
                .returnResult()
                .getResponseBody();
    }
}
