package com.uni.usermicroservice.tenant;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/v1/apartamentos/{apartamentoId}/arrendatarios")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<ArrendatarioResponse> link(
            @PathVariable Long apartamentoId,
            @Valid @RequestBody ArrendatarioRequest request,
            UriComponentsBuilder uriComponentsBuilder
    ) {
        ArrendatarioResponse response = tenantService.link(apartamentoId, request);
        return ResponseEntity
                .created(uriComponentsBuilder
                        .path("/api/v1/apartamentos/{apartamentoId}/arrendatarios/{arrendatarioId}")
                        .build(apartamentoId, response.arrendatarioId()))
                .body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<List<ArrendatarioResponse>> list(@PathVariable Long apartamentoId) {
        return ResponseEntity.ok(tenantService.activeTenantsOf(apartamentoId));
    }

    @DeleteMapping("/{arrendatarioId}")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<Void> unlink(@PathVariable Long apartamentoId, @PathVariable Long arrendatarioId) {
        tenantService.unlink(apartamentoId, arrendatarioId);
        return ResponseEntity.noContent().build();
    }
}
