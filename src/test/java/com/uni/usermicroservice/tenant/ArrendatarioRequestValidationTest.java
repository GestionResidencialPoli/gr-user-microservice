package com.uni.usermicroservice.tenant;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArrendatarioRequestValidationTest {

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

    private ArrendatarioRequest aValidRequest() {
        return new ArrendatarioRequest("Luis", "Marin", "555111", "luis@example.com", "3009999999");
    }

    @Test
    void aFullyPopulatedRequestHasNoViolations() {
        assertThat(validator.validate(aValidRequest())).isEmpty();
    }

    @Test
    void rejectsABlankDocumentNumber() {
        ArrendatarioRequest request = new ArrendatarioRequest("Luis", "Marin", " ", "luis@example.com", null);

        Set<ConstraintViolation<ArrendatarioRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("documentNumber"));
    }

    @Test
    void rejectsAnInvalidEmail() {
        ArrendatarioRequest request = new ArrendatarioRequest("Luis", "Marin", "555111", "no-es-un-correo", null);

        Set<ConstraintViolation<ArrendatarioRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void rejectsADocumentNumberLongerThanTheColumn() {
        ArrendatarioRequest request =
                new ArrendatarioRequest("Luis", "Marin", "9".repeat(31), "luis@example.com", null);

        Set<ConstraintViolation<ArrendatarioRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("documentNumber"));
    }

    @Test
    void rejectsABlankFirstName() {
        ArrendatarioRequest request = new ArrendatarioRequest(" ", "Marin", "555111", "luis@example.com", null);

        Set<ConstraintViolation<ArrendatarioRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("firstName"));
    }

    @Test
    void acceptsAnAbsentPhone() {
        ArrendatarioRequest request = new ArrendatarioRequest("Luis", "Marin", "555111", "luis@example.com", null);

        assertThat(validator.validate(request)).isEmpty();
    }
}
