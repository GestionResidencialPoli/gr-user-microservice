package com.uni.usermicroservice.guard;

import com.uni.usermicroservice.identity.domain.Role;
import com.uni.usermicroservice.identity.domain.RoleRepository;
import com.uni.usermicroservice.identity.domain.User;
import com.uni.usermicroservice.identity.domain.UserRepository;
import com.uni.usermicroservice.identity.domain.UserStatus;
import com.uni.usermicroservice.security.RefreshTokenRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class VigilanteService {

    static final String VIGILANTE_ROLE = "VIGILANTE";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public VigilanteService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public VigilanteResponse create(VigilanteRequest request) {
        String documentNumber = request.documentNumber().trim();

        if (userRepository.findByDocumentNumber(documentNumber).isPresent()) {
            throw new VigilanteAlreadyExistsException(documentNumber);
        }

        Role vigilanteRole = roleRepository.findByName(VIGILANTE_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "El rol " + VIGILANTE_ROLE + " no existe en el catalogo de roles."));

        User vigilante = new User(
                request.firstName().trim(),
                request.lastName().trim(),
                documentNumber,
                request.email().trim().toLowerCase(),
                passwordEncoder.encode(request.initialPassword()),
                request.phone()
        );
        vigilante.getRoles().add(vigilanteRole);
        userRepository.save(vigilante);

        return VigilanteResponse.from(vigilante);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public List<VigilanteResponse> findAll() {
        return userRepository.findByRoleName(VIGILANTE_ROLE).stream()
                .map(VigilanteResponse::from)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public void deactivate(Long userId) {
        User vigilante = userRepository.findByIdAndRoleName(userId, VIGILANTE_ROLE)
                .orElseThrow(() -> new VigilanteNotFoundException(userId));

        vigilante.setStatus(UserStatus.INACTIVE);

        Instant now = Instant.now();
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId)
                .forEach(refreshToken -> refreshToken.setRevokedAt(now));
    }
}
