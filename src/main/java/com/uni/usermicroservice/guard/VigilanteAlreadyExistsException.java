package com.uni.usermicroservice.guard;

public class VigilanteAlreadyExistsException extends RuntimeException {

    public VigilanteAlreadyExistsException(String documentNumber) {
        super("Ya existe una persona registrada con el documento " + documentNumber + ".");
    }
}
