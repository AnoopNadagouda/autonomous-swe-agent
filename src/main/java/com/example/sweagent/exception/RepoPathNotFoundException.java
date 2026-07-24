package com.example.sweagent.exception;

public class RepoPathNotFoundException extends RuntimeException {

    public RepoPathNotFoundException(String message) {
        super(message);
    }
}
