package com.uni.usermicroservice.apartment;

import com.uni.usermicroservice.security.UnitCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ApartmentRequest(
        @NotBlank @Size(max = 20) @UnitCode String torre,
        @NotBlank @Size(max = 20) @UnitCode String numero,
        @PositiveOrZero @Max(200) Integer piso,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") @Digits(integer = 1, fraction = 4)
        BigDecimal coeficienteCopropiedad,
        @Positive @Digits(integer = 6, fraction = 2) BigDecimal area,
        @NotNull @Valid PropietarioRequest propietario
) {
}
