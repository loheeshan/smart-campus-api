package com.westminster.smartcampus.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Debug endpoint for demonstrating the global 500 safety-net mapper.
 * Intentionally throws a NullPointerException so we can prove the client
 * receives a clean JSON error with no stack trace leak.
 */
@Path("/debug")
@Produces(MediaType.APPLICATION_JSON)
public class DebugResource {

    @GET
    @Path("/boom")
    public String triggerUnexpectedError() {
        // Intentional NPE to demonstrate GenericThrowableMapper.
        String nothing = null;
        return nothing.toLowerCase();
    }
}
