package com.uni.usermicroservice.identity.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ResidencyTypeService {

    private final OwnerRepository ownerRepository;
    private final TenantRepository tenantRepository;

    public ResidencyTypeService(OwnerRepository ownerRepository, TenantRepository tenantRepository) {
        this.ownerRepository = ownerRepository;
        this.tenantRepository = tenantRepository;
    }

    public record Residency(Apartment apartment, TipoResidente tipoResidente) {
    }

    @Transactional(readOnly = true)
    public Optional<TipoResidente> tipoResidenteOf(Long userId) {
        return residencyOf(userId).map(Residency::tipoResidente);
    }

    @Transactional(readOnly = true)
    public Optional<ApartmentSummary> apartmentOf(Long userId) {
        return residencyOf(userId).map(ResidencyTypeService::summaryOf);
    }

    @Transactional(readOnly = true)
    public Optional<Residency> residencyOf(Long userId) {
        return ownerRepository.findByUserId(userId).stream()
                .findFirst()
                .map(owner -> new Residency(owner.getApartment(), TipoResidente.PROPIETARIO))
                .or(() -> tenantRepository.findActiveByUserId(userId)
                        .map(tenant -> new Residency(tenant.getApartment(), TipoResidente.ARRENDATARIO)));
    }

    private static ApartmentSummary summaryOf(Residency residency) {
        Apartment apartment = residency.apartment();
        return new ApartmentSummary(apartment.getTorre(), apartment.getNumero(), residency.tipoResidente());
    }
}
