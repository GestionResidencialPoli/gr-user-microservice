package com.uni.usermicroservice.apartment;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/apartamentos")
public class ApartmentController {

    private final ApartmentService apartmentService;

    public ApartmentController(ApartmentService apartmentService) {
        this.apartmentService = apartmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<ApartmentResponse> create(
            @Valid @RequestBody ApartmentRequest request,
            UriComponentsBuilder uriComponentsBuilder
    ) {
        ApartmentResponse response = apartmentService.create(request);
        return ResponseEntity
                .created(uriComponentsBuilder.path("/api/v1/apartamentos/{id}").build(response.id()))
                .body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<PageResponse<ApartmentResponse>> search(
            @RequestParam(required = false) String torre,
            @RequestParam(required = false) String numero,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(apartmentService.search(torre, numero, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRACION') or @apartmentAccessGuard.canView(#id, authentication)")
    public ResponseEntity<ApartmentResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(apartmentService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<ApartmentResponse> update(@PathVariable Long id, @Valid @RequestBody ApartmentRequest request) {
        return ResponseEntity.ok(apartmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        apartmentService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
