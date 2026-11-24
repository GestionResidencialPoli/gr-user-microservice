package com.uni.usermicroservice.communications;

import com.uni.usermicroservice.internal.InternalDirectoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Optional;

@Component
public class CommunicationsRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommunicationsRequestHandler.class);

    private final InternalDirectoryService directoryService;
    private final ObjectMapper objectMapper;

    public CommunicationsRequestHandler(InternalDirectoryService directoryService, ObjectMapper objectMapper) {
        this.directoryService = directoryService;
        this.objectMapper = objectMapper;
    }

    public Optional<String> handle(String message) {
        JsonNode frame = objectMapper.readTree(message);
        if (!"request".equals(frame.path("type").asString(""))) {
            return Optional.empty();
        }

        String id = frame.path("id").asString("");
        try {
            return Optional.of(respond(id, frame.path("action").asString(""), frame.path("payload")));
        } catch (RuntimeException exception) {
            LOGGER.error("No se pudo atender la peticion interna", exception);
            return Optional.of(failure(id, "ERROR_INTERNO", "No fue posible atender la peticion."));
        }
    }

    private String respond(String id, String action, JsonNode payload) {
        return switch (action) {
            case "users.get" -> findUser(id, payload);
            case "apartments.find" -> findApartment(id, payload);
            case "apartments.billable" -> success(
                    id,
                    directoryService.listBillable(payload.path("page").asInt(0), payload.path("size").asInt(100))
            );
            default -> failure(id, "ACCION_DESCONOCIDA", "La accion no existe.");
        };
    }

    private String findUser(String id, JsonNode payload) {
        if (!payload.path("id").isIntegralNumber()) {
            return failure(id, "PETICION_INVALIDA", "El id del usuario es obligatorio.");
        }
        return directoryService.findUser(payload.path("id").asLong())
                .map(user -> success(id, user))
                .orElseGet(() -> notFound(id));
    }

    private String findApartment(String id, JsonNode payload) {
        String torre = payload.path("torre").asString("");
        String numero = payload.path("numero").asString("");
        if (torre.isBlank() || numero.isBlank()) {
            return failure(id, "PETICION_INVALIDA", "La torre y el numero son obligatorios.");
        }
        return directoryService.findApartment(torre, numero)
                .map(apartment -> success(id, apartment))
                .orElseGet(() -> notFound(id));
    }

    private String notFound(String id) {
        return failure(id, "NO_ENCONTRADO", "El recurso no existe.");
    }

    private String success(String id, Object payload) {
        ObjectNode frame = response(id, true);
        frame.set("payload", objectMapper.valueToTree(payload));
        return objectMapper.writeValueAsString(frame);
    }

    private String failure(String id, String code, String message) {
        ObjectNode frame = response(id, false);
        frame.set("error", objectMapper.createObjectNode().put("code", code).put("message", message));
        return objectMapper.writeValueAsString(frame);
    }

    private ObjectNode response(String id, boolean ok) {
        return objectMapper.createObjectNode().put("type", "response").put("id", id).put("ok", ok);
    }
}
