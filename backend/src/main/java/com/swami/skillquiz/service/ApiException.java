package com.swami.skillquiz.service;

import java.util.Map;

/**
 * Error with an HTTP status, a stable code and an English message.
 * The app shows the message in the user's language by looking up the code
 * (params fill placeholders such as {topic}); the English text is the fallback.
 */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, String> params;

    public ApiException(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(int status, String code, String message, Map<String, String> params) {
        super(message);
        this.status = status;
        this.code = code;
        this.params = params == null ? Map.of() : params;
    }

    public int status() { return status; }
    public String code() { return code; }
    public Map<String, String> params() { return params; }
}
