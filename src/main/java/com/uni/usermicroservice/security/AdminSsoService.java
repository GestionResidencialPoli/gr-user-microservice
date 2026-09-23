package com.uni.usermicroservice.security;

import com.uni.usermicroservice.identity.domain.Role;
import com.uni.usermicroservice.identity.domain.User;
import com.uni.usermicroservice.identity.domain.UserRepository;
import com.uni.usermicroservice.identity.domain.UserStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class AdminSsoService {

    private static final String ADMIN_ROLE = "ADMINISTRACION";
    private static final long CODE_EXPIRATION_SECONDS = 60;

    private final UserRepository userRepository;
    private final AdminSsoCodeRepository adminSsoCodeRepository;
    private final AuthTokenService authTokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminSsoService(UserRepository userRepository, AdminSsoCodeRepository adminSsoCodeRepository, AuthTokenService authTokenService) {
        this.userRepository = userRepository;
        this.adminSsoCodeRepository = adminSsoCodeRepository;
        this.authTokenService = authTokenService;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public AdminSsoCodeResponse issueCode(String authenticatedEmail) {
        User user = userRepository.findByEmail(authenticatedEmail)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(AdminSsoCodeInvalidException::new);
        String rawCode = newRawCode();
        adminSsoCodeRepository.save(new AdminSsoCode(user, AuthTokenService.hash(rawCode), Instant.now().plus(CODE_EXPIRATION_SECONDS, ChronoUnit.SECONDS)));
        return new AdminSsoCodeResponse(rawCode);
    }

    @Transactional
    public AuthTokenService.IssuedTokens exchange(String rawCode) {
        AdminSsoCode code = adminSsoCodeRepository.findByCodeHashForUpdate(AuthTokenService.hash(rawCode))
                .filter(candidate -> candidate.isUsable(Instant.now()))
                .orElseThrow(AdminSsoCodeInvalidException::new);
        User user = code.getUser();
        boolean isAdmin = user.getStatus() == UserStatus.ACTIVE && user.getRoles().stream().map(Role::getName).anyMatch(ADMIN_ROLE::equals);
        if (!isAdmin) {
            throw new AdminSsoForbiddenException();
        }
        code.setUsedAt(Instant.now());
        return authTokenService.issueTokens(user.getId(), user.getEmail(), user.getRoles().stream().map(Role::getName).toList());
    }

    private String newRawCode() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
