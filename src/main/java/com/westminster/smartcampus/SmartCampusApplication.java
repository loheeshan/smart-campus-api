package com.westminster.smartcampus;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Root JAX-RS Application class. The @ApplicationPath annotation sets the
 * versioned base URI for all resources: /api/v1
 *
 * Jersey auto-discovers @Path and @Provider annotated classes on the classpath,
 * so we don't need to manually register each resource here.
 */
@ApplicationPath("/api/v1")
public class SmartCampusApplication extends Application {
}
