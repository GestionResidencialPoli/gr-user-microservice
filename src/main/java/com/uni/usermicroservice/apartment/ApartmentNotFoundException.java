package com.uni.usermicroservice.apartment;

public class ApartmentNotFoundException extends RuntimeException {

    public ApartmentNotFoundException(Long id) {
        super("No existe un apartamento con id " + id);
    }
}
