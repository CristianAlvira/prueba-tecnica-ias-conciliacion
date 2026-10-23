package com.pagoclaro.reconciliation.infrastructure.exception;

import com.pagoclaro.reconciliation.domain.exception.PaymentLockedException;
import com.pagoclaro.reconciliation.domain.exception.PaymentNotFoundException;
import com.pagoclaro.reconciliation.infrastructure.dto.ErrorResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_REFERENCE = "La referencia externa no es válida";
    private static final String UNEXPECTED_ERROR = "No fue posible conciliar el pago; revise los logs del servicio";

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotFound(PaymentNotFoundException error) {
        log.warn(error.getMessage());
        return response(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(PaymentLockedException.class)
    public ResponseEntity<ErrorResponseDTO> handleLocked(PaymentLockedException error) {
        log.warn(error.getMessage());
        return response(HttpStatus.CONFLICT, error.getMessage());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidReference(HandlerMethodValidationException error) {
        return response(HttpStatus.BAD_REQUEST, INVALID_REFERENCE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception error) {
        log.error("Error inesperado al conciliar un pago", error);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR);
    }

    private ResponseEntity<ErrorResponseDTO> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponseDTO(message));
    }
}
