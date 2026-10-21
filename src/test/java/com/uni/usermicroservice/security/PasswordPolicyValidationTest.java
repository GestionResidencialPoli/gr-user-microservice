package com.uni.usermicroservice.security;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyValidationTest {

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

    private Set<ConstraintViolation<PasswordResetConfirmation>> validate(String password) {
        return validator.validate(new PasswordResetConfirmation("un-token", password));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Contrasena1", "aB3defgh", "Xy9zzzzzzzzz"})
    void acceptsAPasswordThatMeetsThePolicy(String password) {
        assertThat(validate(password)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ab3defg", "corto1A"})
    void rejectsAPasswordShorterThanEightCharacters(String password) {
        assertThat(validate(password)).anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
    }

    @Test
    void rejectsAPasswordWithoutAnUppercaseLetter() {
        assertThat(validate("sinmayuscula1"))
                .anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
    }

    @Test
    void rejectsAPasswordWithoutALowercaseLetter() {
        assertThat(validate("SINMINUSCULA1"))
                .anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
    }

    @Test
    void rejectsAPasswordWithoutADigit() {
        assertThat(validate("SinDigitosAqui"))
                .anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
    }

    @Test
    void rejectsABlankPassword() {
        assertThat(validate("   ")).anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
    }

    @Test
    void rejectsAPasswordLongerThanBcryptCanHash() {
        assertThat(validate("Ab3" + "x".repeat(70)))
                .anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
    }

    @Test
    void rejectsABlankToken() {
        Set<ConstraintViolation<PasswordResetConfirmation>> violations =
                validator.validate(new PasswordResetConfirmation("  ", "Contrasena1"));

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("token"));
    }
}
