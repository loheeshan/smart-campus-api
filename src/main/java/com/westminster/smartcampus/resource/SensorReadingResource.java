package com.westminster.smartcampus.resource;

import com.westminster.smartcampus.model.Sensor;
import com.westminster.smartcampus.model.SensorReading;
import com.westminster.smartcampus.store.ReadingStore;
import com.westminster.smartcampus.store.SensorStore;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sub-resource class for sensor reading history.
 *
 * This class is NOT annotated with @Path at the class level because it is
 * instantiated directly by a sub-resource locator method in
 * {@link SensorResource}.
 * JAX-RS passes remaining path processing to this object once it's returned
 * from the locator.
 *
 * Handles:
 * GET /sensors/{sensorId}/readings -> fetch all readings for the parent sensor
 * POST /sensors/{sensorId}/readings -> append a new reading AND update the
 * parent sensor's currentValue
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorReadingResource {

    private final String sensorId;
    private final SensorStore sensorStore = SensorStore.getInstance();
    private final ReadingStore readingStore = ReadingStore.getInstance();

    /**
     * Constructed by the sub-resource locator with the parent sensor's ID.
     * All methods in this class operate in the context of that sensor.
     */
    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }

    /**
     * GET /sensors/{sensorId}/readings
     * Returns the full historical log for the parent sensor.
     */
    @GET
    public Response getReadings() {
        Sensor sensor = sensorStore.findById(sensorId);
        if (sensor == null) {
            return notFound("Sensor with id '" + sensorId + "' does not exist.");
        }
        List<SensorReading> history = readingStore.findBySensorId(sensorId);
        return Response.ok(history).build();
    }

    /**
     * POST /sensors/{sensorId}/readings
     * Appends a new reading for this sensor.
     * Side effect: updates the parent sensor's currentValue field.
     */
    @POST
    public Response addReading(SensorReading reading) {
        Sensor sensor = sensorStore.findById(sensorId);
        if (sensor == null) {
            return notFound("Sensor with id '" + sensorId + "' does not exist.");
        }

        // State constraint: sensors in MAINTENANCE cannot accept new readings.
        // (Part 5 will refactor this into a SensorUnavailableException +
        // ExceptionMapper.)
        if ("MAINTENANCE".equalsIgnoreCase(sensor.getStatus())) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", 403);
            body.put("error", "Forbidden");
            body.put("message",
                    "Sensor '" + sensorId + "' is currently under MAINTENANCE "
                            + "and cannot accept new readings.");
            return Response.status(Response.Status.FORBIDDEN).entity(body).build();
        }

        if (reading == null) {
            return badRequest("Request body must contain a reading payload.");
        }

        // Auto-fill fields the client didn't provide.
        if (reading.getId() == null || reading.getId().isBlank()) {
            reading.setId(UUID.randomUUID().toString());
        }
        if (reading.getTimestamp() == 0L) {
            reading.setTimestamp(System.currentTimeMillis());
        }

        // Persist the reading.
        readingStore.add(sensorId, reading);

        // REQUIRED SIDE EFFECT: update the parent sensor's currentValue.
        sensor.setCurrentValue(reading.getValue());

        return Response.status(Response.Status.CREATED).entity(reading).build();
    }

    // ----- small JSON error helpers -----

    private Response notFound(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 404);
        body.put("error", "Not Found");
        body.put("message", message);
        return Response.status(Response.Status.NOT_FOUND).entity(body).build();
    }

    private Response badRequest(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 400);
        body.put("error", "Bad Request");
        body.put("message", message);
        return Response.status(Response.Status.BAD_REQUEST).entity(body).build();
    }
}