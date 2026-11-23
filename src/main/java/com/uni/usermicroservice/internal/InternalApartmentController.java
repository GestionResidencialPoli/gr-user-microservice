package com.uni.usermicroservice.internal;

import com.uni.usermicroservice.apartment.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/apartments")
public class InternalApartmentController {

    private final InternalDirectoryService directoryService;

    public InternalApartmentController(InternalDirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @GetMapping
    public ResponseEntity<InternalApartmentResponse> findByTorreAndNumero(
            @RequestParam String torre,
            @RequestParam String numero
    ) {
        return ResponseEntity.of(directoryService.findApartment(torre, numero));
    }

    @GetMapping("/facturables")
    public ResponseEntity<PageResponse<InternalBillableApartmentResponse>> listBillable(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        return ResponseEntity.ok(directoryService.listBillable(page, size));
    }
}
