package com.uni.usermicroservice.tenant;

import com.uni.usermicroservice.apartment.ApartmentNotFoundException;
import com.uni.usermicroservice.identity.domain.Apartment;
import com.uni.usermicroservice.identity.domain.ApartmentRepository;
import com.uni.usermicroservice.identity.domain.ResidentUserService;
import com.uni.usermicroservice.identity.domain.Tenant;
import com.uni.usermicroservice.identity.domain.TenantRepository;
import com.uni.usermicroservice.identity.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TenantService {

    private final ApartmentRepository apartmentRepository;
    private final TenantRepository tenantRepository;
    private final ResidentUserService residentUserService;

    public TenantService(
            ApartmentRepository apartmentRepository,
            TenantRepository tenantRepository,
            ResidentUserService residentUserService
    ) {
        this.apartmentRepository = apartmentRepository;
        this.tenantRepository = tenantRepository;
        this.residentUserService = residentUserService;
    }

    @Transactional
    public ArrendatarioResponse link(Long apartmentId, ArrendatarioRequest request) {
        Apartment apartment = apartmentRepository.findById(apartmentId)
                .orElseThrow(() -> new ApartmentNotFoundException(apartmentId));

        if (!apartment.isActivo()) {
            throw new ApartmentInactiveException(apartmentId);
        }

        User user = residentUserService.resolveByDocument(detailsOf(request));

        if (tenantRepository.existsByUserIdAndEndDateIsNull(user.getId())) {
            throw new TenantAlreadyLinkedException();
        }

        Tenant tenant = new Tenant(user, apartment, LocalDate.now());
        tenantRepository.save(tenant);

        return ArrendatarioResponse.from(tenant);
    }

    @Transactional(readOnly = true)
    public List<ArrendatarioResponse> activeTenantsOf(Long apartmentId) {
        if (!apartmentRepository.existsById(apartmentId)) {
            throw new ApartmentNotFoundException(apartmentId);
        }

        return tenantRepository.findActiveWithUserByApartmentId(apartmentId).stream()
                .map(ArrendatarioResponse::from)
                .toList();
    }

    @Transactional
    public void unlink(Long apartmentId, Long tenantId) {
        Tenant tenant = tenantRepository.findByIdAndApartmentIdAndEndDateIsNull(tenantId, apartmentId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId, apartmentId));

        tenant.setEndDate(LocalDate.now());
    }

    private static ResidentUserService.ResidentDetails detailsOf(ArrendatarioRequest request) {
        return ResidentUserService.ResidentDetails.normalized(
                request.firstName(),
                request.lastName(),
                request.documentNumber(),
                request.email(),
                request.phone()
        );
    }
}
