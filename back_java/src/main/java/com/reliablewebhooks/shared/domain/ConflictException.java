package com.reliablewebhooks.shared.domain;

/** Thrown by a use case when the request conflicts with the entity's current state. No framework dependency — pure domain. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
