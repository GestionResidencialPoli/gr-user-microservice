package com.uni.usermicroservice.identity.domain;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class ResidentUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public ResidentUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User resolveByDocument(ResidentDetails details) {
        return userRepository.findByDocumentNumber(details.documentNumber())
                .map(existing -> {
                    apply(existing, details);
                    return existing;
                })
                .orElseGet(() -> userRepository.save(new User(
                        details.firstName(),
                        details.lastName(),
                        details.documentNumber(),
                        details.email(),
                        unusablePasswordHash(),
                        details.phone()
                )));
    }

    public void apply(User user, ResidentDetails details) {
        user.setFirstName(details.firstName());
        user.setLastName(details.lastName());
        user.setDocumentNumber(details.documentNumber());
        user.setEmail(details.email());
        user.setPhone(details.phone());
    }

    private String unusablePasswordHash() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String unguessableSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return passwordEncoder.encode(unguessableSecret);
    }

    public record ResidentDetails(
            String firstName,
            String lastName,
            String documentNumber,
            String email,
            String phone
    ) {

        public static ResidentDetails normalized(
                String firstName,
                String lastName,
                String documentNumber,
                String email,
                String phone
        ) {
            return new ResidentDetails(
                    trimmed(firstName),
                    trimmed(lastName),
                    trimmed(documentNumber),
                    email == null ? null : email.trim().toLowerCase(),
                    phone
            );
        }

        private static String trimmed(String value) {
            return value == null ? null : value.trim();
        }
    }
}
