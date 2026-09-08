package com.uni.usermicroservice.security;

public class PasswordResetTokenInvalidException extends RuntimeException {

    public PasswordResetTokenInvalidException(String message) {
        super(message);
    }

    public static PasswordResetTokenInvalidException notUsable() {
        return new PasswordResetTokenInvalidException(
                "El enlace de restablecimiento no es valido o ya fue utilizado. Solicite uno nuevo.");
    }

    public static PasswordResetTokenInvalidException expired() {
        return new PasswordResetTokenInvalidException(
                "El enlace de restablecimiento expiro. Solicite uno nuevo.");
    }
}
