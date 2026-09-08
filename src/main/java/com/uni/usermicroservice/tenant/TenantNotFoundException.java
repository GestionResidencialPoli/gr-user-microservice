package com.uni.usermicroservice.tenant;

public class TenantNotFoundException extends RuntimeException {

    public TenantNotFoundException(Long tenantId, Long apartmentId) {
        super("No existe un arrendatario vigente con id " + tenantId + " en el apartamento " + apartmentId + ".");
    }
}
