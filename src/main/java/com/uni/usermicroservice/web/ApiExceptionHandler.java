package com.uni.usermicroservice.web;

import com.uni.usermicroservice.apartment.ApartmentAlreadyExistsException;
import com.uni.usermicroservice.apartment.ApartmentNotFoundException;
import com.uni.usermicroservice.apartment.OwnerTransferNotSupportedException;
import com.uni.usermicroservice.guard.VigilanteAlreadyExistsException;
import com.uni.usermicroservice.guard.VigilanteNotFoundException;
import com.uni.usermicroservice.security.ApiError;
import com.uni.usermicroservice.security.IncorrectCurrentPasswordException;
import com.uni.usermicroservice.security.PasswordResetTokenInvalidException;
import com.uni.usermicroservice.tenant.ApartmentInactiveException;
import com.uni.usermicroservice.tenant.TenantAlreadyLinkedException;
import com.uni.usermicroservice.tenant.TenantNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                message,
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(PasswordResetTokenInvalidException.class)
    public ResponseEntity<ApiError> handlePasswordResetToken(
            PasswordResetTokenInvalidException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                exception.getMessage(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(IncorrectCurrentPasswordException.class)
    public ResponseEntity<ApiError> handleIncorrectCurrentPassword(
            IncorrectCurrentPasswordException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                exception.getMessage(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler({
            ApartmentNotFoundException.class,
            TenantNotFoundException.class,
            VigilanteNotFoundException.class
    })
    public ResponseEntity<ApiError> handleNotFound(RuntimeException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                exception.getMessage(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler({
            ApartmentAlreadyExistsException.class,
            OwnerTransferNotSupportedException.class,
            ApartmentInactiveException.class,
            TenantAlreadyLinkedException.class,
            VigilanteAlreadyExistsException.class
    })
    public ResponseEntity<ApiError> handleConflict(RuntimeException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                exception.getMessage(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        log.warn("Violacion de integridad de datos en {} {}", request.getMethod(), request.getRequestURI(), exception);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                "La operacion no se pudo completar porque viola una restriccion de integridad de los datos.",
                request.getRequestURI()
        ));
    }
}
