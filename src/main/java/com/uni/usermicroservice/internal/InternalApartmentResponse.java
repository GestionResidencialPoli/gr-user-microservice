package com.uni.usermicroservice.internal;

import com.uni.usermicroservice.identity.domain.Apartment;

public record InternalApartmentResponse(Long id, String torre, String numero, boolean activo) {

    public static InternalApartmentResponse from(Apartment apartment) {
        return new InternalApartmentResponse(
                apartment.getId(),
                apartment.getTorre(),
                apartment.getNumero(),
                apartment.isActivo()
        );
    }
}
