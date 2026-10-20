package com.uni.usermicroservice.guard;

import com.uni.usermicroservice.identity.domain.User;
import com.uni.usermicroservice.identity.domain.UserStatus;

import java.time.Instant;

public record VigilanteResponse(
        Long userId,
        String firstName,
        String lastName,
        String documentNumber,
        String email,
        String phone,
        UserStatus estado,
        Instant createdAt
) {

    public static VigilanteResponse from(User user) {
        return new VigilanteResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getDocumentNumber(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
