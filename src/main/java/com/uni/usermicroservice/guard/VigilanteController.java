package com.uni.usermicroservice.guard;

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
@RequestMapping("/api/v1/vigilantes")
public class VigilanteController {

    private final VigilanteService vigilanteService;

    public VigilanteController(VigilanteService vigilanteService) {
        this.vigilanteService = vigilanteService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<VigilanteResponse> create(
            @Valid @RequestBody VigilanteRequest request,
            UriComponentsBuilder uriComponentsBuilder
    ) {
        VigilanteResponse response = vigilanteService.create(request);
        return ResponseEntity
                .created(uriComponentsBuilder.path("/api/v1/vigilantes/{userId}").build(response.userId()))
                .body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<List<VigilanteResponse>> list() {
        return ResponseEntity.ok(vigilanteService.findAll());
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<Void> deactivate(@PathVariable Long userId) {
        vigilanteService.deactivate(userId);
        return ResponseEntity.noContent().build();
    }
}
