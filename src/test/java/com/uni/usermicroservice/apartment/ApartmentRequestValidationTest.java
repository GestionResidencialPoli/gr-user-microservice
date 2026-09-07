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
    void rejectsANegativePiso() {
        ApartmentRequest request = new ApartmentRequest(
                "A", "101", -1, null, null, aValidPropietario()
        );

        Set<ConstraintViolation<ApartmentRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("piso"));
    }

    @Test
    void rejectsATorreLongerThanTheColumn() {
        ApartmentRequest request = new ApartmentRequest(
                "T".repeat(21), "101", 3, null, null, aValidPropietario()
        );

        Set<ConstraintViolation<ApartmentRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("torre"));
    }

    @Test
    void rejectsANumeroLongerThanTheColumn() {
        ApartmentRequest request = new ApartmentRequest(
                "A", "1".repeat(21), 3, null, null, aValidPropietario()
        );

        Set<ConstraintViolation<ApartmentRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("numero"));
    }

    @Test
    void rejectsAnAreaThatOverflowsTheColumnPrecision() {
        ApartmentRequest request = new ApartmentRequest(
                "A", "101", 3, null, new BigDecimal("1234567.89"), aValidPropietario()
        );

        Set<ConstraintViolation<ApartmentRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("area"));
    }

    @Test
    void rejectsAnOwnerDocumentNumberLongerThanTheColumn() {
        PropietarioRequest propietario = new PropietarioRequest(
                "Ana", "Perez", "9".repeat(31), "ana@example.com", "3000000000"
        );

        Set<ConstraintViolation<ApartmentRequest>> violations = validator.validate(aValidApartment(propietario));

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("propietario.documentNumber"));
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
