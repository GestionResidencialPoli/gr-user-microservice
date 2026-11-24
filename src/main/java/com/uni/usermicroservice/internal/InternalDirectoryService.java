package com.uni.usermicroservice.internal;

import com.uni.usermicroservice.apartment.PageResponse;
import com.uni.usermicroservice.identity.domain.ApartmentRepository;
import com.uni.usermicroservice.identity.domain.ResidencyTypeService;
import com.uni.usermicroservice.identity.domain.User;
import com.uni.usermicroservice.identity.domain.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InternalDirectoryService {

    private static final int MAX_PAGE_SIZE = 500;
    private static final Sort BILLING_ORDER = Sort.by("torre", "numero", "id");

    private final UserRepository userRepository;
    private final ApartmentRepository apartmentRepository;
    private final ResidencyTypeService residencyTypeService;

    public InternalDirectoryService(
            UserRepository userRepository,
            ApartmentRepository apartmentRepository,
            ResidencyTypeService residencyTypeService
    ) {
        this.userRepository = userRepository;
        this.apartmentRepository = apartmentRepository;
        this.residencyTypeService = residencyTypeService;
    }

    public Optional<InternalUserResponse> findUser(Long id) {
        return userRepository.findById(id).map(this::toResponse);
    }

    public Optional<InternalApartmentResponse> findApartment(String torre, String numero) {
        return apartmentRepository
                .findFirstByTorreIgnoreCaseAndNumeroIgnoreCaseOrderByIdAsc(torre.strip(), numero.strip())
                .map(InternalApartmentResponse::from);
    }

    public PageResponse<InternalBillableApartmentResponse> listBillable(int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), BILLING_ORDER);
        return PageResponse.from(apartmentRepository.findAll(pageable).map(InternalBillableApartmentResponse::from));
    }

    private InternalUserResponse toResponse(User user) {
        InternalUserApartmentResponse apartment = residencyTypeService.residencyOf(user.getId())
                .map(InternalUserApartmentResponse::from)
                .orElse(null);
        return new InternalUserResponse(user.getId(), user.getFirstName(), user.getLastName(), apartment);
    }
}
