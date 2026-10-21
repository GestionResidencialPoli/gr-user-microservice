package com.uni.usermicroservice.tenant;

public class ApartmentInactiveException extends RuntimeException {

    public ApartmentInactiveException(Long apartmentId) {
        super("El apartamento " + apartmentId + " esta inactivo y no admite vincular arrendatarios.");
    }
}
