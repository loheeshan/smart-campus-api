package com.westminster.smartcampus.exception;

/**
 * Thrown when a client attempts to POST a reading to a sensor whose status
 * is MAINTENANCE (or otherwise unavailable). Mapped to HTTP 403 Forbidden.
 */
public class SensorUnavailableException extends RuntimeException {

    private final String sensorId;
    private final String status;

    public SensorUnavailableException(String sensorId, String status) {
        super("Sensor '" + sensorId + "' is currently '" + status
                + "' and cannot accept new readings.");
        this.sensorId = sensorId;
        this.status = status;
    }

    public String getSensorId() { return sensorId; }
    public String getStatus() { return status; }
}
