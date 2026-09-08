package com.uni.usermicroservice.security;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;

@NotBlank
@Size(min = 8, max = 72)
@Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).+$",
        message = "debe incluir al menos una minuscula, una mayuscula y un digito"
)
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PasswordPolicy {

    String message() default "no cumple la politica minima de contrasena";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
