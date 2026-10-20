package com.uni.usermicroservice.tenant;

public class TenantAlreadyLinkedException extends RuntimeException {

    public TenantAlreadyLinkedException() {
        super("La persona ya tiene un arrendamiento vigente; debe desvincularse antes de vincularla a otro apartamento.");
    }
}
