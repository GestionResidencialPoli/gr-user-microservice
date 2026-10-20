package com.uni.usermicroservice.tenant;

import com.uni.usermicroservice.identity.domain.Tenant;
import com.uni.usermicroservice.identity.domain.TipoResidente;
import com.uni.usermicroservice.identity.domain.User;

import java.time.LocalDate;

public record ArrendatarioResponse(
        Long arrendatarioId,
        Long apartamentoId,
        Long userId,
        String firstName,
        String lastName,
        String documentNumber,
        String email,
        String phone,
        TipoResidente tipoResidente,
        LocalDate vinculadoDesde
) {

    public static ArrendatarioResponse from(Tenant tenant) {
        User user = tenant.getUser();
        return new ArrendatarioResponse(
                tenant.getId(),
                tenant.getApartment().getId(),
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getDocumentNumber(),
                user.getEmail(),
                user.getPhone(),
                TipoResidente.ARRENDATARIO,
                tenant.getStartDate()
        );
    }
}
