package com.jokkorm.application.physical;

/** Mappée en 404 problem+json {@code room-equipment-not-found}. */
public class RoomEquipmentNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public RoomEquipmentNotFoundException(String message) { super(message); }
}
