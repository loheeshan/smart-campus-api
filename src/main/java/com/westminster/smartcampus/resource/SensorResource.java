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
import java.util.Map;
import java.util.UUID;

/**
 * Manages the /sensors collection.
 *
 * GET /sensors -> list all sensors (filter by ?type=...)
 * GET /sensors/{sensorId} -> fetch a specific sensor
 * POST /sensors -> create (validates roomId exists)
 * DELETE /sensors/{sensorId} -> remove, detaches from parent room
 * {sensorId}/readings -> delegated to SensorReadingResource
 */
@Path("/sensors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorResource {

    private final SensorStore sensorStore = SensorStore.getInstance();
    private final RoomStore roomStore = RoomStore.getInstance();

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
        if (sensor == null || sensor.getType() == null || sensor.getType().isBlank()) {
            return badRequest("Field 'type' is required.");
        }
        if (sensor.getRoomId() == null || sensor.getRoomId().isBlank()) {
            return badRequest("Field 'roomId' is required.");
        }

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

        if (sensor.getStatus() == null || sensor.getStatus().isBlank()) {
            sensor.setStatus("ACTIVE");
        }

        if (sensor.getId() == null || sensor.getId().isBlank()) {
            sensor.setId("SENSOR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        if (sensorStore.exists(sensor.getId())) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", 409);
            body.put("error", "Conflict");
            body.put("message", "A sensor with id '" + sensor.getId() + "' already exists.");
            return Response.status(Response.Status.CONFLICT).entity(body).build();
        }

        sensorStore.save(sensor);

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

        Room room = roomStore.findById(sensor.getRoomId());
        if (room != null) {
            room.getSensorIds().remove(sensorId);
        }

        sensorStore.delete(sensorId);
        return Response.noContent().build();
    }

    // ------------------------------------------------------------------
    // SUB-RESOURCE LOCATOR
    // ------------------------------------------------------------------
    //
    // Note: this method has @Path but NO @GET/@POST/etc. That is what makes
    // it a "sub-resource locator" instead of a regular resource method.
    //
    // For any request matching /sensors/{sensorId}/readings (and deeper),
    // JAX-RS calls this method, takes the returned object, and dispatches
    // the remainder of the path to methods on that object.
    //
    // This delegation keeps SensorResource focused on sensor concerns, and
    // isolates all reading-related logic inside SensorReadingResource.
    // ------------------------------------------------------------------
    @Path("/{sensorId}/readings")
    public SensorReadingResource getReadingResource(@PathParam("sensorId") String sensorId) {
        return new SensorReadingResource(sensorId);
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