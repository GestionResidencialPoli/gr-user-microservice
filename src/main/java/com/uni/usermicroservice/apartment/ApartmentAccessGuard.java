package com.uni.usermicroservice.apartment;

import com.uni.usermicroservice.identity.domain.OwnerRepository;
import com.uni.usermicroservice.identity.domain.TenantRepository;
import com.uni.usermicroservice.identity.domain.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("apartmentAccessGuard")
public class ApartmentAccessGuard {

    private final UserRepository userRepository;
    private final OwnerRepository ownerRepository;
    private final TenantRepository tenantRepository;

    public ApartmentAccessGuard(
            UserRepository userRepository,
            OwnerRepository ownerRepository,
            TenantRepository tenantRepository
    ) {
        this.userRepository = userRepository;
        this.ownerRepository = ownerRepository;
        this.tenantRepository = tenantRepository;
    }

    public boolean canView(Long apartmentId, Authentication authentication) {
        if (authentication == null) {
            return false;
        }

        return userRepository.findByEmail(authentication.getName())
                .map(user -> ownerRepository.existsByUserIdAndApartmentId(user.getId(), apartmentId)
                        || tenantRepository.existsByUserIdAndApartmentIdAndEndDateIsNull(user.getId(), apartmentId))
                .orElse(false);
    }
}
