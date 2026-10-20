package com.uni.usermicroservice.security;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;

@Pattern(regexp = "^$|^(3\\d{9}|60\\d{8})$", message = "el telefono debe tener 10 digitos: celular (3xx) o fijo nacional (60x)")
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PhoneFormat {

    String message() default "el telefono debe tener 10 digitos: celular (3xx) o fijo nacional (60x)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
