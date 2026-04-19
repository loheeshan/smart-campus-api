package com.westminster.smartcampus.mapper;

import com.westminster.smartcampus.dto.ErrorResponse;
import com.westminster.smartcampus.exception.LinkedResourceNotFoundException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Converts LinkedResourceNotFoundException into HTTP 422 Unprocessable Entity.
 *
 * 422 is semantically more accurate than 404 here: the URL being called is
 * valid and the JSON is well-formed; the problem is a reference *inside*
 * the payload that points to something non-existent.
 */
@Provider
public class LinkedResourceNotFoundMapper
        implements ExceptionMapper<LinkedResourceNotFoundException> {

    private static final int UNPROCESSABLE_ENTITY = 422;

    @Override
    public Response toResponse(LinkedResourceNotFoundException ex) {
        ErrorResponse body = new ErrorResponse(
                UNPROCESSABLE_ENTITY,
                "Unprocessable Entity",
                ex.getMessage()
        );
        return Response.status(UNPROCESSABLE_ENTITY)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
