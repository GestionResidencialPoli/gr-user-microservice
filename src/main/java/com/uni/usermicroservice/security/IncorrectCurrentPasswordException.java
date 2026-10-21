package com.uni.usermicroservice.security;

public class IncorrectCurrentPasswordException extends RuntimeException {

    public IncorrectCurrentPasswordException() {
        super("La contrasena actual no es correcta.");
    }
}
