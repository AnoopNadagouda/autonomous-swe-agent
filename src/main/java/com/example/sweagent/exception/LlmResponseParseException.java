package com.example.sweagent.exception;

public class LlmResponseParseException extends RuntimeException {

    private final String rawOutput;

    public LlmResponseParseException(String message, String rawOutput, Throwable cause) {
        super(message, cause);
        this.rawOutput = rawOutput;
    }

    public String getRawOutput() {
        return rawOutput;
    }
}
