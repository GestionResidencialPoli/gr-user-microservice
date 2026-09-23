package com.uni.usermicroservice.security;

import com.uni.usermicroservice.apartment.PropietarioRequest;
import com.uni.usermicroservice.guard.VigilanteRequest;
import com.uni.usermicroservice.tenant.ArrendatarioRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneFormatValidationTest {

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

    @ParameterizedTest
    @ValueSource(strings = {"3011234567", "", "0000000000"})
    void acceptsAnEmptyOrExactlyTenDigitPhone(String phone) {
        assertThat(validator.validate(new UpdateProfileRequest(phone))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"301123456", "30112345678", "301-123-4567", "301abcd567", "+573011234567"})
    void rejectsAPhoneThatIsNotExactlyTenDigits(String phone) {
        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(new UpdateProfileRequest(phone));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
    }

    @org.junit.jupiter.api.Test
    void alsoValidatesThePhoneOnArrendatarioVigilanteAndPropietarioRequests() {
        var arrendatario = new ArrendatarioRequest("Ana", "Ruiz", "CC-1", "ana@example.com", "invalido");
        var vigilante = new VigilanteRequest("Ana", "Ruiz", "CC-1", "ana@example.com", "invalido", "Str0ngPass!");
        var propietario = new PropietarioRequest("Ana", "Ruiz", "CC-1", "ana@example.com", "invalido");

        assertThat(validator.validate(arrendatario)).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
        assertThat(validator.validate(vigilante)).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
        assertThat(validator.validate(propietario)).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
    }
}
