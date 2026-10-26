package com.swami.skillquiz.service;

/** Error with an HTTP status and a user-friendly message shown directly on the UI. */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
