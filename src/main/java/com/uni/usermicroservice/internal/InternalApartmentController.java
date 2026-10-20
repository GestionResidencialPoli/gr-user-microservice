package com.uni.usermicroservice.internal;

import com.uni.usermicroservice.apartment.PageResponse;
import com.uni.usermicroservice.identity.domain.ApartmentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/apartments")
public class InternalApartmentController {

    private static final int MAX_PAGE_SIZE = 500;
    private static final Sort BILLING_ORDER = Sort.by("torre", "numero", "id");

    private final ApartmentRepository apartmentRepository;

    public InternalApartmentController(ApartmentRepository apartmentRepository) {
        this.apartmentRepository = apartmentRepository;
    }

    @GetMapping
    public ResponseEntity<InternalApartmentResponse> findByTorreAndNumero(
            @RequestParam String torre,
            @RequestParam String numero
    ) {
        return apartmentRepository.findFirstByTorreIgnoreCaseAndNumeroIgnoreCaseOrderByIdAsc(torre.strip(), numero.strip())
                .map(InternalApartmentResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/facturables")
    public ResponseEntity<PageResponse<InternalBillableApartmentResponse>> listBillable(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), BILLING_ORDER);
        var apartments = apartmentRepository.findAll(pageable).map(InternalBillableApartmentResponse::from);
        return ResponseEntity.ok(PageResponse.from(apartments));
    }
}
