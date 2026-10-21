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

@Pattern(regexp = "^\\s*\\p{L}[\\p{L}\\p{M}' .-]*$", message = "solo admite letras, espacios, apostrofo, punto y guion, y debe empezar por una letra")
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PersonName {

    String message() default "solo admite letras, espacios, apostrofo, punto y guion, y debe empezar por una letra";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
