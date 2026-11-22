package com.jokkorm.commons.role;

/** Mappée en 403 forbidden-role. */
public class ForbiddenRoleException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public ForbiddenRoleException(String message) { super(message); }
}
