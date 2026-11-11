package com.uni.usermicroservice.security;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    private static final String VALID_SECRET = "un-secreto-local-de-mas-de-32-caracteres";
    private static final String UNRESOLVED_PLACEHOLDER = "${JWT_SECRET}";
    private static final String VALID_ENCRYPTION_KEY = "Y2xhdmUtZGUtcHJ1ZWJhcy1kZS0zMi1ieXRlcy1vayE";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private JwtProperties propertiesWithEncryptionKey(String encryptionKey) {
        return new JwtProperties(VALID_SECRET, encryptionKey, 15, 7);
    }

    private JwtProperties propertiesWithSecret(String secret) {
        return new JwtProperties(secret, VALID_ENCRYPTION_KEY, 15, 7);
    }

    @Test
    void aCompleteConfigurationPassesValidation() {
        assertThat(validator.validate(propertiesWithSecret(VALID_SECRET))).isEmpty();
    }

    @Test
    void anUnresolvedPlaceholderIsRejectedInsteadOfBecomingTheSigningKey() {
        assertThat(validator.validate(propertiesWithSecret(UNRESOLVED_PLACEHOLDER)))
                .singleElement()
                .satisfies(violation -> assertThat(violation.getMessage()).contains("al menos 32 caracteres"));
    }

    @Test
    void aSecretOfExactlyThirtyTwoCharactersIsAccepted() {
        assertThat(validator.validate(propertiesWithSecret("a".repeat(32)))).isEmpty();
    }

    @Test
    void aSecretOneCharacterShortIsRejected() {
        assertThat(validator.validate(propertiesWithSecret("a".repeat(31)))).hasSize(1);
    }

    @Test
    void aBlankSecretIsRejected() {
        assertThat(validator.validate(propertiesWithSecret("   ")))
                .anySatisfy(violation -> assertThat(violation.getMessage()).contains("obligatorio"));
    }

    @Test
    void aNullSecretIsRejected() {
        assertThat(validator.validate(propertiesWithSecret(null)))
                .anySatisfy(violation -> assertThat(violation.getMessage()).contains("obligatorio"));
    }

    @Test
    void anExpirationThatIsNotPositiveIsRejected() {
        assertThat(validator.validate(new JwtProperties(VALID_SECRET, VALID_ENCRYPTION_KEY, 0, 7))).hasSize(1);
        assertThat(validator.validate(new JwtProperties(VALID_SECRET, VALID_ENCRYPTION_KEY, 15, -1))).hasSize(1);
    }

    @Test
    void aMissingEncryptionKeyIsRejected() {
        assertThat(validator.validate(propertiesWithEncryptionKey(null)))
                .anySatisfy(violation -> assertThat(violation.getMessage()).contains("JWT_ENCRYPTION_KEY"));
    }

    @Test
    void anEncryptionKeyThatIsNotThirtyTwoBytesInBase64UrlIsRejected() {
        assertThat(validator.validate(propertiesWithEncryptionKey("demasiado-corta"))).hasSize(1);
        assertThat(validator.validate(propertiesWithEncryptionKey("${JWT_ENCRYPTION_KEY}"))).hasSize(1);
        assertThat(validator.validate(propertiesWithEncryptionKey(VALID_ENCRYPTION_KEY + "A"))).hasSize(1);
    }
}
