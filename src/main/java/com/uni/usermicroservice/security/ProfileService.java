package com.uni.usermicroservice.security;

import com.uni.usermicroservice.identity.domain.ResidencyTypeService;
import com.uni.usermicroservice.identity.domain.Role;
import com.uni.usermicroservice.identity.domain.User;
import com.uni.usermicroservice.identity.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final ResidencyTypeService residencyTypeService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    public ProfileService(
            UserRepository userRepository,
            ResidencyTypeService residencyTypeService,
            PasswordEncoder passwordEncoder,
            RefreshTokenRepository refreshTokenRepository
    ) {
        this.userRepository = userRepository;
        this.residencyTypeService = residencyTypeService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(readOnly = true)
    public Optional<MeResponse> me(String email) {
        return userRepository.findByEmail(email).map(this::toMeResponse);
    }

    @Transactional
    public Optional<MeResponse> updatePhone(String email, String phone) {
        return userRepository.findByEmail(email).map(user -> {
            user.setPhone(phone);
            return toMeResponse(user);
        });
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email).orElseThrow();

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IncorrectCurrentPasswordException();
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        revokeActiveSessionsOf(user);
    }

    private void revokeActiveSessionsOf(User user) {
        Instant now = Instant.now();
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId())
                .forEach(refreshToken -> refreshToken.setRevokedAt(now));
    }

    private MeResponse toMeResponse(User user) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getRoles().stream().map(Role::getName).toList(),
                residencyTypeService.apartmentOf(user.getId()).orElse(null)
        );
    }
}
