package com.uni.usermicroservice.apartment;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApartmentRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    private PropietarioRequest aValidPropietario() {
        return new PropietarioRequest("Ana", "Perez", "123456", "ana@example.com", "3000000000");
    }

    private ApartmentRequest aValidApartment(PropietarioRequest propietario) {
        return new ApartmentRequest("A", "101", 3, new BigDecimal("0.01"), new BigDecimal("60"), propietario);
    }

    @Test
    void aFullyPopulatedRequestHasNoViolations() {
        Set<ConstraintViolation<ApartmentRequest>> violations = validator.validate(aValidApartment(aValidPropietario()));

        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsABlankTorre() {
        ApartmentRequest request = new ApartmentRequest(
                " ", "101", 3, null, null, aValidPropietario()
        );

        Set<ConstraintViolation<ApartmentRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("torre"));
    }

    @Test
    void rejectsAnInvalidOwnerEmail() {
        PropietarioRequest propietario = new PropietarioRequest("Ana", "Perez", "123456", "no-es-un-correo", "3000000000");

        Set<ConstraintViolation<ApartmentRequest>> violations = validator.validate(aValidApartment(propietario));

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("propietario.email"));
    }

    @Test
    void rejectsABlankOwnerDocumentNumber() {
        PropietarioRequest propietario = new PropietarioRequest("Ana", "Perez", " ", "ana@example.com", "3000000000");

        Set<ConstraintViolation<ApartmentRequest>> violations = validator.validate(aValidApartment(propietario));

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("propietario.documentNumber"));
    }

    @Test
    void rejectsACoeficienteCopropiedadOutOfRange() {
        ApartmentRequest request = new ApartmentRequest(
                "A", "101", 3, new BigDecimal("1.5"), null, aValidPropietario()
        );

        Set<ConstraintViolation<ApartmentRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("coeficienteCopropiedad"));
    }

    @Test
    void rejectsANonPositiveArea() {
        ApartmentRequest request = new ApartmentRequest(
                "A", "101", 3, null, new BigDecimal("0"), aValidPropietario()
        );

        Set<ConstraintViolation<ApartmentRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("area"));
    }
}
