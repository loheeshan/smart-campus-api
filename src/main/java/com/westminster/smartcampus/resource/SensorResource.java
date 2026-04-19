package com.westminster.smartcampus.resource;

import com.westminster.smartcampus.model.Room;
import com.westminster.smartcampus.model.Sensor;
import com.westminster.smartcampus.store.RoomStore;
import com.westminster.smartcampus.store.SensorStore;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the /sensors collection.
 *
 *   GET  /sensors                -> list all sensors (optionally filter by ?type=...)
 *   GET  /sensors/{sensorId}     -> fetch a specific sensor
 *   POST /sensors                -> create a new sensor (validates roomId exists)
 *   DELETE /sensors/{sensorId}   -> remove a sensor and detach it from its room
 */
@Path("/sensors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorResource {

    private final SensorStore sensorStore = SensorStore.getInstance();
    private final RoomStore roomStore = RoomStore.getInstance();

    /**
     * GET /sensors or GET /sensors?type=CO2
     * The optional @QueryParam("type") filters the collection server-side.
     */
    @GET
    public Response getAllSensors(@QueryParam("type") String type) {
        Collection<Sensor> result;
        if (type != null && !type.isBlank()) {
            result = sensorStore.findByType(type);
        } else {
            result = sensorStore.findAll();
        }
        return Response.ok(result).build();
    }

    @GET
    @Path("/{sensorId}")
    public Response getSensorById(@PathParam("sensorId") String sensorId) {
        Sensor sensor = sensorStore.findById(sensorId);
        if (sensor == null) {
            return notFound("Sensor with id '" + sensorId + "' does not exist.");
        }
        return Response.ok(sensor).build();
    }

    @POST
    public Response createSensor(Sensor sensor, @Context UriInfo uriInfo) {
        // Validate required fields.
        if (sensor == null || sensor.getType() == null || sensor.getType().isBlank()) {
            return badRequest("Field 'type' is required.");
        }
        if (sensor.getRoomId() == null || sensor.getRoomId().isBlank()) {
            return badRequest("Field 'roomId' is required.");
        }

        // Validate that the referenced room exists.
        // (Part 5 will refactor this into a LinkedResourceNotFoundException + ExceptionMapper.)
        Room room = roomStore.findById(sensor.getRoomId());
        if (room == null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", 422);
            body.put("error", "Unprocessable Entity");
            body.put("message",
                    "Cannot register sensor: referenced room '"
                            + sensor.getRoomId() + "' does not exist.");
            return Response.status(422).entity(body).build();
        }

        // Default status if the client didn't provide one.
        if (sensor.getStatus() == null || sensor.getStatus().isBlank()) {
            sensor.setStatus("ACTIVE");
        }

        // Auto-generate an ID if the client didn't supply one.
        if (sensor.getId() == null || sensor.getId().isBlank()) {
            sensor.setId("SENSOR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        // Reject duplicate IDs.
        if (sensorStore.exists(sensor.getId())) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", 409);
            body.put("error", "Conflict");
            body.put("message", "A sensor with id '" + sensor.getId() + "' already exists.");
            return Response.status(Response.Status.CONFLICT).entity(body).build();
        }

        // Persist the sensor.
        sensorStore.save(sensor);

        // IMPORTANT: link it back to the parent room so the deletion-safety check
        // in RoomResource actually has something to block against.
        if (!room.getSensorIds().contains(sensor.getId())) {
            room.getSensorIds().add(sensor.getId());
        }

        URI location = UriBuilder.fromUri(uriInfo.getAbsolutePath())
                .path(sensor.getId())
                .build();

        return Response.created(location).entity(sensor).build();
    }

    @DELETE
    @Path("/{sensorId}")
    public Response deleteSensor(@PathParam("sensorId") String sensorId) {
        Sensor sensor = sensorStore.findById(sensorId);
        if (sensor == null) {
            return notFound("Sensor with id '" + sensorId + "' does not exist.");
        }

        // Detach from the parent room's sensor list, so the room becomes deletable.
        Room room = roomStore.findById(sensor.getRoomId());
        if (room != null) {
            room.getSensorIds().remove(sensorId);
        }

        sensorStore.delete(sensorId);
        return Response.noContent().build();
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