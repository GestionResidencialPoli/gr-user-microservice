package com.uni.usermicroservice.apartment;

import com.uni.usermicroservice.identity.domain.User;

public record PropietarioResponse(
        Long userId,
        String firstName,
        String lastName,
        String documentNumber,
        String email,
        String phone
) {

    public static PropietarioResponse from(User user) {
        return new PropietarioResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getDocumentNumber(),
                user.getEmail(),
                user.getPhone()
        );
    }
}
