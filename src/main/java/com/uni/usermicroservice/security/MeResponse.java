package com.uni.usermicroservice.security;

import com.uni.usermicroservice.identity.domain.ApartmentSummary;

import java.util.List;

public record MeResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        List<String> roles,
        ApartmentSummary apartment
) {
}
