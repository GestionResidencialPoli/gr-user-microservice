package com.uni.usermicroservice.security;

import com.uni.usermicroservice.apartment.ApartmentRequest;
import com.uni.usermicroservice.apartment.ApartmentService;
import com.uni.usermicroservice.apartment.PropietarioRequest;
import com.uni.usermicroservice.support.AbstractIntegrationTest;
import com.uni.usermicroservice.tenant.ArrendatarioRequest;
import com.uni.usermicroservice.tenant.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceLayerAuthorizationIT extends AbstractIntegrationTest {

    @Autowired
    private ApartmentService apartmentService;

    @Autowired
    private TenantService tenantService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        role.toLowerCase() + "@example.com",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void authenticateAsAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken(
                        "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    }

    private ApartmentRequest anApartmentRequest() {
        long n = System.nanoTime();
        return new ApartmentRequest(
                "SL" + n, String.valueOf(n % 1000), 1, null, null,
                new PropietarioRequest("Ana", "Perez", "DOC-SL-" + n, "ana.sl." + n + "@example.com", null));
    }

    private ArrendatarioRequest anArrendatarioRequest() {
        long n = System.nanoTime();
        return new ArrendatarioRequest(
                "Luis", "Marin", "DOC-SLT-" + n, "luis.sl." + n + "@example.com", null);
    }

    @Test
    void aResidenteCannotCreateAnApartmentEvenBypassingTheController() {
        authenticateAs("RESIDENTE");

        assertThatThrownBy(() -> apartmentService.create(anApartmentRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aVigilanteCannotCreateAnApartmentEvenBypassingTheController() {
        authenticateAs("VIGILANTE");

        assertThatThrownBy(() -> apartmentService.create(anApartmentRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anAnonymousCallerCannotCreateAnApartment() {
        authenticateAsAnonymous();

        assertThatThrownBy(() -> apartmentService.create(anApartmentRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aResidenteCannotListApartmentsEvenBypassingTheController() {
        authenticateAs("RESIDENTE");

        assertThatThrownBy(() -> apartmentService.search(null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aResidenteCannotUpdateAnApartmentEvenBypassingTheController() {
        authenticateAs("RESIDENTE");

        assertThatThrownBy(() -> apartmentService.update(1L, anApartmentRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aResidenteCannotDeactivateAnApartmentEvenBypassingTheController() {
        authenticateAs("RESIDENTE");

        assertThatThrownBy(() -> apartmentService.deactivate(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aResidenteWithNoRelationCannotReadAnApartmentDetailFromTheService() {
        authenticateAs("ADMINISTRACION");
        var created = apartmentService.create(anApartmentRequest());
        SecurityContextHolder.clearContext();

        authenticateAs("RESIDENTE");

        assertThatThrownBy(() -> apartmentService.findById(created.id()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aVigilanteCannotLinkATenantEvenBypassingTheController() {
        authenticateAs("VIGILANTE");

        assertThatThrownBy(() -> tenantService.link(1L, anArrendatarioRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aResidenteCannotListTenantsEvenBypassingTheController() {
        authenticateAs("RESIDENTE");

        assertThatThrownBy(() -> tenantService.activeTenantsOf(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aResidenteCannotUnlinkATenantEvenBypassingTheController() {
        authenticateAs("RESIDENTE");

        assertThatThrownBy(() -> tenantService.unlink(1L, 1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void administracionReachesTheServiceOperationsItOwns() {
        authenticateAs("ADMINISTRACION");

        assertThatCode(() -> {
            var created = apartmentService.create(anApartmentRequest());
            apartmentService.findById(created.id());
            apartmentService.search(null, null, PageRequest.of(0, 20));
            tenantService.activeTenantsOf(created.id());
            apartmentService.deactivate(created.id());
        }).doesNotThrowAnyException();
    }
}
