package com.uni.usermicroservice.security;

import jakarta.validation.constraints.NotBlank;

/**
 * Audiencia solicitada por el frontend que emite el codigo: la aplicacion de
 * rol a la que el usuario quiere cruzar (por ejemplo "admin" o "residente").
 * El servicio valida que corresponda al rol real del usuario autenticado;
 * no basta con enviar cualquier valor.
 */
public record SsoCodeRequest(@NotBlank String audience) {
}
