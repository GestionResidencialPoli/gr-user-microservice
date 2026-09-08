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

    @Transactional(readOnly = true)
    public Optional<TipoResidente> tipoResidenteOf(Long userId) {
        return apartmentOf(userId).map(ApartmentSummary::tipoResidente);
    }

    @Transactional(readOnly = true)
    public Optional<ApartmentSummary> apartmentOf(Long userId) {
        return ownerRepository.findByUserId(userId).stream()
                .findFirst()
                .map(owner -> summaryOf(owner.getApartment(), TipoResidente.PROPIETARIO))
                .or(() -> tenantRepository.findActiveByUserId(userId)
                        .map(tenant -> summaryOf(tenant.getApartment(), TipoResidente.ARRENDATARIO)));
    }

    private ApartmentSummary summaryOf(Apartment apartment, TipoResidente tipoResidente) {
        return new ApartmentSummary(apartment.getTorre(), apartment.getNumero(), tipoResidente);
    }
}
