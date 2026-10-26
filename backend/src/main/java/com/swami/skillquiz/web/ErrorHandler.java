package com.swami.skillquiz.web;

import com.swami.skillquiz.service.ApiException;
import com.swami.skillquiz.web.Dtos.ErrorBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Every error goes to the UI as {"error": "readable message"} */
@RestControllerAdvice
public class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("Form mein kuch galat hai");
        return ResponseEntity.badRequest().body(new ErrorBody(msg));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorBody> badInput(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorBody("Request sahi format mein nahi hai."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> other(Exception e) {
        // 404 / 405 / missing header etc. from Spring itself: keep their real status
        if (e instanceof ErrorResponse er) {
            return ResponseEntity.status(er.getStatusCode()).body(new ErrorBody("Request galat hai (" + er.getStatusCode().value() + ")."));
        }
        log.error("Unexpected error", e);
        return ResponseEntity.status(500).body(new ErrorBody("Server error. Thodi der baad try karo."));
    }
}
