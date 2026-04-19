package com.westminster.smartcampus.mapper;

import com.westminster.smartcampus.dto.ErrorResponse;
import com.westminster.smartcampus.exception.RoomNotEmptyException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Converts RoomNotEmptyException into an HTTP 409 Conflict response
 * with a structured JSON error body.
 */
@Provider
public class RoomNotEmptyMapper implements ExceptionMapper<RoomNotEmptyException> {

    @Override
    public Response toResponse(RoomNotEmptyException ex) {
        ErrorResponse body = new ErrorResponse(
                409,
                "Conflict",
                ex.getMessage()
        );
        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
