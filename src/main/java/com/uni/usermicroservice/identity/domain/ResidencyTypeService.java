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
        if (ownerRepository.existsByUserId(userId)) {
            return Optional.of(TipoResidente.PROPIETARIO);
        }
        if (tenantRepository.existsByUserIdAndEndDateIsNull(userId)) {
            return Optional.of(TipoResidente.ARRENDATARIO);
        }
        return Optional.empty();
    }
}
