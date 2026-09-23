package com.uni.usermicroservice.security;

import com.uni.usermicroservice.identity.domain.Role;
import com.uni.usermicroservice.identity.domain.User;
import com.uni.usermicroservice.identity.domain.UserRepository;
import com.uni.usermicroservice.identity.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

/**
 * Puente SSO entre el login y cada aplicacion de rol. Nacio en GR-30 para
 * administracion unicamente; GR-151 lo generaliza a los tres roles.
 *
 * No hay {@code @PreAuthorize} de rol fijo aqui: el rol que califica depende
 * de la audiencia que pide quien llama, no es estatico por endpoint. La
 * correspondencia audiencia -> rol es la unica fuente de verdad y se usa
 * tanto al emitir como al canjear, para que un cambio de rol entre ambos
 * pasos (o un intento de pedir una audiencia ajena) quede cubierto en los
 * dos lados.
 */
@Service
public class SsoService {

    private static final long CODE_EXPIRATION_SECONDS = 60;

    private static final Map<String, String> AUDIENCE_ROLES = Map.of(
            "admin", "ADMINISTRACION",
            "residente", "RESIDENTE",
            "vigilante", "VIGILANTE"
    );

    private final UserRepository userRepository;
    private final SsoCodeRepository ssoCodeRepository;
    private final AuthTokenService authTokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    public SsoService(UserRepository userRepository, SsoCodeRepository ssoCodeRepository, AuthTokenService authTokenService) {
        this.userRepository = userRepository;
        this.ssoCodeRepository = ssoCodeRepository;
        this.authTokenService = authTokenService;
    }

    @Transactional
    public SsoCodeResponse issueCode(String authenticatedEmail, String audience) {
        String requiredRole = AUDIENCE_ROLES.get(audience);
        if (requiredRole == null) {
            throw new SsoAudienceUnknownException();
        }

        User user = userRepository.findByEmail(authenticatedEmail)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(SsoCodeInvalidException::new);

        if (!hasRole(user, requiredRole)) {
            throw new SsoForbiddenException();
        }

        String rawCode = newRawCode();
        ssoCodeRepository.save(new SsoCode(
                user,
                AuthTokenService.hash(rawCode),
                audience,
                Instant.now().plus(CODE_EXPIRATION_SECONDS, ChronoUnit.SECONDS)
        ));
        return new SsoCodeResponse(rawCode);
    }

    @Transactional
    public AuthTokenService.IssuedTokens exchange(String rawCode) {
        SsoCode code = ssoCodeRepository.findByCodeHashForUpdate(AuthTokenService.hash(rawCode))
                .filter(candidate -> candidate.isUsable(Instant.now()))
                .orElseThrow(SsoCodeInvalidException::new);

        String requiredRole = AUDIENCE_ROLES.get(code.getAudience());
        User user = code.getUser();
        boolean qualifies = requiredRole != null
                && user.getStatus() == UserStatus.ACTIVE
                && hasRole(user, requiredRole);
        if (!qualifies) {
            throw new SsoForbiddenException();
        }

        code.setUsedAt(Instant.now());
        return authTokenService.issueTokens(user.getId(), user.getEmail(), user.getRoles().stream().map(Role::getName).toList());
    }

    private static boolean hasRole(User user, String roleName) {
        return user.getRoles().stream().map(Role::getName).anyMatch(roleName::equals);
    }

    private String newRawCode() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
