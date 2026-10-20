package com.uni.usermicroservice.guard;

public class VigilanteNotFoundException extends RuntimeException {

    public VigilanteNotFoundException(Long userId) {
        super("No existe un vigilante con id " + userId + ".");
    }
}
