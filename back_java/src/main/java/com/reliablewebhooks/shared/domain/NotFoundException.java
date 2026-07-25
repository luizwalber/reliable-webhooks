package com.reliablewebhooks.shared.domain;

/** Thrown by a use case when a requested entity doesn't exist. No framework dependency — pure domain. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
