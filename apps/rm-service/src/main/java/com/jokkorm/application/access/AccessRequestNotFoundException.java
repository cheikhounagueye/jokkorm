package com.jokkorm.application.access;

public class AccessRequestNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public AccessRequestNotFoundException(String message) { super(message); }
}
