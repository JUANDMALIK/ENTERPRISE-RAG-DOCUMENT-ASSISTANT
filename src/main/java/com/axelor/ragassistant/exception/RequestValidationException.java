package com.axelor.ragassistant.exception;

public class RequestValidationException extends IllegalArgumentException {

    public RequestValidationException(String message) {
        super(message);
    }

    public RequestValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
