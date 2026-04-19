package com.westminster.smartcampus.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Cross-cutting logging concern. By implementing BOTH ContainerRequestFilter
 * and ContainerResponseFilter in one @Provider class, we get pre-routing
 * request logs and post-processing response logs for every endpoint without
 * touching any resource method.
 */
@Provider
public class LoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOGGER = Logger.getLogger(LoggingFilter.class.getName());

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        LOGGER.info("--> " + requestContext.getMethod()
                + " " + requestContext.getUriInfo().getRequestUri());
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {
        LOGGER.info("<-- " + requestContext.getMethod()
                + " " + requestContext.getUriInfo().getRequestUri()
                + " [" + responseContext.getStatus() + "]");
    }
}
