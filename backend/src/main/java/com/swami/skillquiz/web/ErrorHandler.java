package com.swami.skillquiz.web;

import com.swami.skillquiz.service.ApiException;
import com.swami.skillquiz.web.Dtos.ErrorBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * Every error goes to the app as {"error": "English text", "code": "KEY", "params": {...}}.
 * The app translates by code into the user's language and falls back to the English text.
 */
@RestControllerAdvice
public class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(new ErrorBody(e.getMessage(), e.code(), e.params()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException e) {
        FieldError f = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        if (f != null && "ID_CHARS".equals(f.getDefaultMessage())) {
            return ResponseEntity.badRequest().body(new ErrorBody(
                    "The ID can only have letters, numbers and - _ . /", "ID_CHARS", Map.of()));
        }
        String field = f == null ? "" : f.getField();
        return ResponseEntity.badRequest().body(new ErrorBody(
                "Please check this field: " + field, "FIELD_INVALID", Map.of("field", field)));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorBody> badInput(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorBody(
                "The request was not in the right format.", "BAD_REQUEST", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> other(Exception e) {
        // 404 / 405 / missing header etc. from Spring itself: keep their real status
        if (e instanceof ErrorResponse er) {
            int sc = er.getStatusCode().value();
            return ResponseEntity.status(er.getStatusCode()).body(new ErrorBody(
                    "The request could not be handled (" + sc + ").", "BAD_REQUEST", Map.of("status", String.valueOf(sc))));
        }
        log.error("Unexpected error", e);
        return ResponseEntity.status(500).body(new ErrorBody(
                "Something went wrong on the server. Please try again shortly.", "SERVER_ERROR", Map.of()));
    }
}
