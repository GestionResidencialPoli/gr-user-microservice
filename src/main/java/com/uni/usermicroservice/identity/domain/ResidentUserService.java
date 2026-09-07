package com.uni.usermicroservice.identity.domain;

import org.springframework.stereotype.Service;

@Service
public class ResidentUserService {

    private static final String PENDING_ACTIVATION_PASSWORD_HASH = "PENDING_ACTIVATION";

    private final UserRepository userRepository;

    public ResidentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
                        PENDING_ACTIVATION_PASSWORD_HASH,
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
