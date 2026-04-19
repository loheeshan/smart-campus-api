package com.westminster.smartcampus.exception;

/**
 * Thrown when a client attempts to DELETE a Room that still has active
 * sensors assigned to it. Mapped to HTTP 409 Conflict.
 */
public class RoomNotEmptyException extends RuntimeException {

    private final String roomId;
    private final int sensorCount;

    public RoomNotEmptyException(String roomId, int sensorCount) {
        super("Room '" + roomId + "' cannot be deleted: "
                + sensorCount + " sensor(s) still assigned.");
        this.roomId = roomId;
        this.sensorCount = sensorCount;
    }

    public String getRoomId() { return roomId; }
    public int getSensorCount() { return sensorCount; }
}
