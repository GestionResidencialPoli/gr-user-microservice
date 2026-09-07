package com.uni.usermicroservice.apartment;

/**
 * Se lanza cuando una edicion intenta cambiar la persona titular de un apartamento.
 *
 * <p>Este endpoint solo actualiza los datos del propietario principal ya registrado. Cambiar la
 * titularidad implica historico de cambios de propietario, que GR-38 excluye explicitamente de su
 * alcance. Sin esta guarda, el documento recibido sobrescribiria la identidad del usuario existente.
 */
public class OwnerTransferNotSupportedException extends RuntimeException {

    public OwnerTransferNotSupportedException(Long apartmentId) {
        super("El apartamento " + apartmentId + " ya tiene un propietario principal registrado y este"
                + " endpoint no permite cambiar la titularidad. Envie el mismo numero de documento para"
                + " actualizar los datos del propietario actual.");
    }
}
