package com.npc.kura.exception;

public class IntegrityCheckFailedException extends RuntimeException {
    public IntegrityCheckFailedException(String message) {
        super(message);
    }
}
