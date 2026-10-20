package com.uni.usermicroservice.internal;

import com.uni.usermicroservice.identity.domain.Apartment;

import java.math.BigDecimal;

public record InternalBillableApartmentResponse(
        Long id,
        String torre,
        String numero,
        boolean activo,
        BigDecimal coeficienteCopropiedad
) {

    public static InternalBillableApartmentResponse from(Apartment apartment) {
        return new InternalBillableApartmentResponse(
                apartment.getId(),
                apartment.getTorre(),
                apartment.getNumero(),
                apartment.isActivo(),
                apartment.getCoeficienteCopropiedad()
        );
    }
}
