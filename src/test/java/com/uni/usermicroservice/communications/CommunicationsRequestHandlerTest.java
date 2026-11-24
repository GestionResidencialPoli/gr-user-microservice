package com.uni.usermicroservice.communications;

import com.uni.usermicroservice.internal.InternalApartmentResponse;
import com.uni.usermicroservice.internal.InternalDirectoryService;
import com.uni.usermicroservice.internal.InternalUserResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunicationsRequestHandlerTest {

    private final InternalDirectoryService directoryService = mock(InternalDirectoryService.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final CommunicationsRequestHandler handler = new CommunicationsRequestHandler(directoryService, objectMapper);

    @Test
    void respondsWithTheRequestedUser() {
        when(directoryService.findUser(7L)).thenReturn(Optional.of(new InternalUserResponse(7L, "Ana", "Ruiz", null)));

        JsonNode response = request("users.get", "{\"id\":7}");

        assertThat(response.path("id").asString()).isEqualTo("req-1");
        assertThat(response.path("ok").asBoolean()).isTrue();
        assertThat(response.path("payload").path("firstName").asString()).isEqualTo("Ana");
    }

    @Test
    void respondsWithTheRequestedApartment() {
        when(directoryService.findApartment("A", "101"))
                .thenReturn(Optional.of(new InternalApartmentResponse(3L, "A", "101", true)));

        JsonNode response = request("apartments.find", "{\"torre\":\"A\",\"numero\":\"101\"}");

        assertThat(response.path("payload").path("id").asLong()).isEqualTo(3L);
    }

    @Test
    void reportsMissingResources() {
        when(directoryService.findUser(9L)).thenReturn(Optional.empty());
        assertThat(errorOf(request("users.get", "{\"id\":9}"))).isEqualTo("NO_ENCONTRADO");
    }

    @Test
    void rejectsInvalidPayloadsAndUnknownActions() {
        assertThat(errorOf(request("users.get", "{\"id\":\"x\"}"))).isEqualTo("PETICION_INVALIDA");
        assertThat(errorOf(request("apartments.find", "{}"))).isEqualTo("PETICION_INVALIDA");
        assertThat(errorOf(request("otra", "{}"))).isEqualTo("ACCION_DESCONOCIDA");
    }

    @Test
    void hidesInternalFailures() {
        when(directoryService.findUser(1L)).thenThrow(new IllegalStateException("detalle interno"));

        JsonNode response = request("users.get", "{\"id\":1}");

        assertThat(errorOf(response)).isEqualTo("ERROR_INTERNO");
        assertThat(response.toString()).doesNotContain("detalle interno");
    }

    @Test
    void ignoresFramesThatAreNotRequests() {
        assertThat(handler.handle("{\"type\":\"error\"}")).isEmpty();
    }

    private JsonNode request(String action, String payload) {
        String frame = "{\"type\":\"request\",\"id\":\"req-1\",\"action\":\"%s\",\"payload\":%s}".formatted(action, payload);
        return objectMapper.readTree(handler.handle(frame).orElseThrow());
    }

    private String errorOf(JsonNode response) {
        return response.path("error").path("code").asString();
    }
}
