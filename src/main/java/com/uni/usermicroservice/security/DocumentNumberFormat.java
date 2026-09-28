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

@Pattern(regexp = "^\\s*[A-Za-z0-9-]{4,30}\\s*$", message = "el documento solo admite letras, digitos y guion, entre 4 y 30 caracteres")
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DocumentNumberFormat {

    String message() default "el documento solo admite letras, digitos y guion, entre 4 y 30 caracteres";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
