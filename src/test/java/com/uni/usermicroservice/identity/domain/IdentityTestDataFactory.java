package com.uni.usermicroservice.identity.domain;

import java.util.concurrent.atomic.AtomicLong;

public final class IdentityTestDataFactory {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private IdentityTestDataFactory() {
    }

    public static User aUser() {
        long n = SEQUENCE.incrementAndGet();
        User user = new User();
        user.setFirstName("Nombre" + n);
        user.setLastName("Apellido" + n);
        user.setEmail("usuario" + n + "@example.com");
        user.setPasswordHash("hash-" + n);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    public static Role aRole(String name) {
        Role role = new Role();
        role.setName(name);
        role.setDescription("Rol de prueba " + name);
        return role;
    }

    public static Apartment anApartment() {
        long n = SEQUENCE.incrementAndGet();
        Apartment apartment = new Apartment();
        apartment.setTorre("T" + n);
        apartment.setNumero(String.valueOf(100 + n));
        apartment.setPiso(1);
        apartment.setActivo(true);
        return apartment;
    }

    public static Owner anOwner(User user, Apartment apartment) {
        Owner owner = new Owner();
        owner.setUser(user);
        owner.setApartment(apartment);
        owner.setPrincipal(true);
        return owner;
    }

    public static Tenant aTenant(User user, Apartment apartment, java.time.LocalDate startDate) {
        Tenant tenant = new Tenant();
        tenant.setUser(user);
        tenant.setApartment(apartment);
        tenant.setStartDate(startDate);
        return tenant;
    }
}
