package com.uni.usermicroservice.internal;

import com.uni.usermicroservice.identity.domain.ApartmentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/apartments")
public class InternalApartmentController {

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
}
