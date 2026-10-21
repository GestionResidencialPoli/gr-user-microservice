package com.uni.usermicroservice.apartment;

public class OwnerTransferNotSupportedException extends RuntimeException {

    public OwnerTransferNotSupportedException(Long apartmentId) {
        super("El apartamento " + apartmentId + " ya tiene un propietario principal registrado y este"
                + " endpoint no permite cambiar la titularidad. Envie el mismo numero de documento para"
                + " actualizar los datos del propietario actual.");
    }
}
