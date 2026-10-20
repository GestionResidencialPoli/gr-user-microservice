package com.uni.usermicroservice.apartment;

import com.uni.usermicroservice.identity.domain.Apartment;

import java.math.BigDecimal;
import java.time.Instant;

public record ApartmentResponse(
        Long id,
        String torre,
        String numero,
        Integer piso,
        BigDecimal coeficienteCopropiedad,
        BigDecimal area,
        boolean activo,
        PropietarioResponse propietario,
        Instant createdAt
) {

    public static ApartmentResponse from(Apartment apartment, PropietarioResponse propietario) {
        return new ApartmentResponse(
                apartment.getId(),
                apartment.getTorre(),
                apartment.getNumero(),
                apartment.getPiso(),
                apartment.getCoeficienteCopropiedad(),
                apartment.getArea(),
                apartment.isActivo(),
                propietario,
                apartment.getCreatedAt()
        );
    }
}
