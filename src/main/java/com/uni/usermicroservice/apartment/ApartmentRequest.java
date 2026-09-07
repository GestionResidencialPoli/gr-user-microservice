package com.uni.usermicroservice.apartment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ApartmentRequest(
        @NotBlank String torre,
        @NotBlank String numero,
        Integer piso,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") BigDecimal coeficienteCopropiedad,
        @Positive BigDecimal area,
        @NotNull @Valid PropietarioRequest propietario
) {
}
