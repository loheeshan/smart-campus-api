package com.westminster.smartcampus;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.URI;

/**
 * Bootstraps an embedded Grizzly HTTP server hosting the JAX-RS application.
 *
 * The base URI includes the /api/v1 path segment so that all resources
 * registered in com.westminster.smartcampus are served under that prefix,
 * matching the @ApplicationPath("/api/v1") annotation on
 * SmartCampusApplication.
 *
 * Scans com.westminster.smartcampus for @Path resources, @Provider mappers,
 * and @Provider filters automatically.
 */
public class Main {

    public static final String BASE_URI = "http://localhost:8080/api/v1/";

    public static HttpServer startServer() {
        ResourceConfig config = new ResourceConfig()
                .packages("com.westminster.smartcampus");
        return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), config);
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = startServer();

        System.out.println("============================================================");
        System.out.println("  Smart Campus API is running");
        System.out.println("  Discovery endpoint: " + BASE_URI);
        System.out.println("  Press Ctrl+C to stop.");
        System.out.println("============================================================");

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            server.shutdownNow();
        }
    }
}