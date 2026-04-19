package com.westminster.smartcampus.exception;

/**
 * Thrown when a submitted payload references another resource that does not
 * exist (e.g., POSTing a sensor whose roomId does not match any Room).
 * Mapped to HTTP 422 Unprocessable Entity.
 */
public class LinkedResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;

    public LinkedResourceNotFoundException(String resourceType, String resourceId) {
        super("Referenced " + resourceType + " with id '" + resourceId + "' does not exist.");
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
}
