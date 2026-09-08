package com.uni.usermicroservice.security;

import com.uni.usermicroservice.identity.domain.Role;
import com.uni.usermicroservice.identity.domain.User;
import com.uni.usermicroservice.identity.domain.UserRepository;
import com.uni.usermicroservice.identity.domain.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
    }

    @Transactional
    public Optional<AuthTokenService.IssuedTokens> login(String email, String rawPassword) {
        String normalizedEmail = email.strip().toLowerCase();

        return userRepository.findByEmail(normalizedEmail)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()))
                .map(this::issueTokensFor);
    }

    private AuthTokenService.IssuedTokens issueTokensFor(User user) {
        var roles = user.getRoles().stream().map(Role::getName).toList();
        return authTokenService.issueTokens(user.getId(), user.getEmail(), roles);
    }
}
