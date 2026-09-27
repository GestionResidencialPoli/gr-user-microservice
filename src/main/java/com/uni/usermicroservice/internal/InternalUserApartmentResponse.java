package com.uni.usermicroservice.internal;

import com.uni.usermicroservice.identity.domain.Apartment;
import com.uni.usermicroservice.identity.domain.ResidencyTypeService.Residency;
import com.uni.usermicroservice.identity.domain.TipoResidente;

public record InternalUserApartmentResponse(
        Long id,
        String torre,
        String numero,
        boolean activo,
        TipoResidente tipoResidente
) {

    public static InternalUserApartmentResponse from(Residency residency) {
        Apartment apartment = residency.apartment();
        return new InternalUserApartmentResponse(
                apartment.getId(),
                apartment.getTorre(),
                apartment.getNumero(),
                apartment.isActivo(),
                residency.tipoResidente()
        );
    }
}
