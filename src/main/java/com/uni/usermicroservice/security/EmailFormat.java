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

@Pattern(regexp = "^\\s*[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}\\s*$", message = "el correo debe tener un dominio con extension, por ejemplo nombre@dominio.com")
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EmailFormat {

    String message() default "el correo debe tener un dominio con extension, por ejemplo nombre@dominio.com";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
