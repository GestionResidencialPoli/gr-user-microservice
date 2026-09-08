package com.uni.usermicroservice.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResidentDetailsNormalizationTest {

    private ResidentUserService.ResidentDetails normalized(String firstName, String email, String document) {
        return ResidentUserService.ResidentDetails.normalized(firstName, "Perez", document, email, " 300 ");
    }

    @Test
    void theEmailIsLowercasedSoItSatisfiesTheDatabaseCheck() {
        assertThat(normalized("Ana", "  ANA.Perez@Example.COM  ", "123").email())
                .isEqualTo("ana.perez@example.com");
    }

    @Test
    void namesAndDocumentAreTrimmedSoPaddedValuesDoNotCreateDuplicates() {
        var details = normalized("  Ana  ", "ana@example.com", "  123  ");

        assertThat(details.firstName()).isEqualTo("Ana");
        assertThat(details.documentNumber()).isEqualTo("123");
    }

    @Test
    void thePhoneIsKeptAsProvidedBecauseItIsNotAnIdentityField() {
        assertThat(normalized("Ana", "ana@example.com", "123").phone()).isEqualTo(" 300 ");
    }

    @Test
    void nullValuesSurviveNormalizationWithoutFailing() {
        var details = ResidentUserService.ResidentDetails.normalized(null, null, null, null, null);

        assertThat(details.firstName()).isNull();
        assertThat(details.email()).isNull();
        assertThat(details.documentNumber()).isNull();
    }

    @Test
    void anEmailThatIsAlreadyNormalizedIsLeftUnchanged() {
        assertThat(normalized("Ana", "ana@example.com", "123").email()).isEqualTo("ana@example.com");
    }
}
