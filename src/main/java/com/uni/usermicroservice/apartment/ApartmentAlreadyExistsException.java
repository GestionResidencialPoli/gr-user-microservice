package com.uni.usermicroservice.apartment;

public class ApartmentAlreadyExistsException extends RuntimeException {

    public ApartmentAlreadyExistsException(String torre, String numero) {
        super("Ya existe un apartamento con torre " + torre + " y numero " + numero);
    }
}
