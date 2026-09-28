package com.uni.usermicroservice.security;

import com.uni.usermicroservice.apartment.ApartmentRequest;
import com.uni.usermicroservice.apartment.PropietarioRequest;
import com.uni.usermicroservice.guard.VigilanteRequest;
import com.uni.usermicroservice.tenant.ArrendatarioRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FormatValidationTest {

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

    private static PropietarioRequest propietario(String firstName, String documentNumber, String email) {
        return new PropietarioRequest(firstName, "Gomez", documentNumber, email, "3001234567");
    }

    private static boolean violates(Object request, String property) {
        return validator.validate(request).stream().anyMatch(v -> v.getPropertyPath().toString().equals(property));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ana", "María José", "O'Neil", "Ana-Lucía", "Ñusta", "Jr. Pérez"})
    void acceptsRealPersonNames(String name) {
        assertThat(violates(propietario(name, "CC-1000", "ana@example.com"), "firstName")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ana2", "123", "Ana@", "-Ana", "Ana_Maria", "<script>"})
    void rejectsNamesWithDigitsOrSymbols(String name) {
        assertThat(violates(propietario(name, "CC-1000", "ana@example.com"), "firstName")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1017123456", "CC-1000000001", "AB1234", "PA-X9"})
    void acceptsDocumentNumbers(String document) {
        assertThat(violates(propietario("Ana", document, "ana@example.com"), "documentNumber")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "10.171.234", "CC 1000", "doc#1"})
    void rejectsMalformedDocumentNumbers(String document) {
        assertThat(violates(propietario("Ana", document, "ana@example.com"), "documentNumber")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana@dominio", "ana@@dominio.com", "ana dominio@x.co", "ana@dominio.c"})
    void rejectsEmailsWithoutAValidDomain(String email) {
        assertThat(violates(propietario("Ana", "CC-1000", email), "email")).isTrue();
    }

    @Test
    void appliesTheSameRulesToTenantsAndGuards() {
        var arrendatario = new ArrendatarioRequest("Luis2", "Marin", "12", "luis@x", null);
        var vigilante = new VigilanteRequest("Luis", "M4rin", "CC 1", "luis@example.com", null, "Str0ngPass");

        assertThat(violates(arrendatario, "firstName")).isTrue();
        assertThat(violates(arrendatario, "documentNumber")).isTrue();
        assertThat(violates(arrendatario, "email")).isTrue();
        assertThat(violates(vigilante, "lastName")).isTrue();
        assertThat(violates(vigilante, "documentNumber")).isTrue();
    }

    @Test
    void validatesTowerNumberAndFloorOfAnApartment() {
        var valido = new ApartmentRequest("B", "301-A", 3, null, null, propietario("Ana", "CC-1000", "ana@example.com"));
        var invalido = new ApartmentRequest("Torre 1", "301#", 250, BigDecimal.ONE, null, propietario("Ana", "CC-1000", "ana@example.com"));

        assertThat(validator.validate(valido)).isEmpty();
        assertThat(violates(invalido, "torre")).isTrue();
        assertThat(violates(invalido, "numero")).isTrue();
        assertThat(violates(invalido, "piso")).isTrue();
    }

    @Test
    void limitsLoginPasswordToTheBcryptMaximum() {
        assertThat(violates(new LoginRequest("ana@example.com", "a".repeat(73)), "password")).isTrue();
        assertThat(violates(new LoginRequest("ana@example.com", "a".repeat(72)), "password")).isFalse();
    }
}
