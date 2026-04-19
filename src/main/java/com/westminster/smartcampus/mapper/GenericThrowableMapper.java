package com.westminster.smartcampus.mapper;

import com.westminster.smartcampus.dto.ErrorResponse;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Global safety net. Any exception that slips past the more specific mappers
 * ends up here. The client never sees the stack trace; they get a clean
 * JSON 500. The full trace is logged server-side for debugging.
 *
 * Security rationale: stack traces leak internal package names, library
 * versions, file paths, and logic flow, all of which help an attacker
 * fingerprint the system and find vulnerabilities.
 */
@Provider
public class GenericThrowableMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER =
            Logger.getLogger(GenericThrowableMapper.class.getName());

    @Override
    public Response toResponse(Throwable ex) {
        // Let JAX-RS handle its own WebApplicationException types (404, 405, etc.)
        // with their default status codes instead of forcing them to 500.
        if (ex instanceof WebApplicationException wae) {
            return wae.getResponse();
        }

        LOGGER.log(Level.SEVERE, "Unhandled exception intercepted by global mapper", ex);

        ErrorResponse body = new ErrorResponse(
                500,
                "Internal Server Error",
                "An unexpected error occurred. Please contact the administrator."
        );
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
