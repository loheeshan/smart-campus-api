package com.westminster.smartcampus.resource;

import com.westminster.smartcampus.model.Room;
import com.westminster.smartcampus.store.RoomStore;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the /rooms collection.
 *
 * GET /rooms -> list all rooms
 * POST /rooms -> create a new room (returns 201 + Location)
 * GET /rooms/{roomId} -> fetch a specific room
 * DELETE /rooms/{roomId} -> delete a room, blocked (409) if it still has
 * sensors
 */
@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoomResource {

    private final RoomStore roomStore = RoomStore.getInstance();

    @GET
    public Response getAllRooms() {
        Collection<Room> rooms = roomStore.findAll();
        return Response.ok(rooms).build();
    }

    @GET
    @Path("/{roomId}")
    public Response getRoomById(@PathParam("roomId") String roomId) {
        Room room = roomStore.findById(roomId);
        if (room == null) {
            return notFound("Room with id '" + roomId + "' does not exist.");
        }
        return Response.ok(room).build();
    }

    @POST
    public Response createRoom(Room room, @Context UriInfo uriInfo) {
        // Basic validation: name and capacity must be provided.
        if (room == null || room.getName() == null || room.getName().isBlank()) {
            return badRequest("Field 'name' is required.");
        }
        if (room.getCapacity() <= 0) {
            return badRequest("Field 'capacity' must be a positive integer.");
        }

        // Auto-generate an ID if the client didn't supply one.
        if (room.getId() == null || room.getId().isBlank()) {
            room.setId("ROOM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        // Reject duplicate IDs.
        if (roomStore.exists(room.getId())) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", 409);
            body.put("error", "Conflict");
            body.put("message", "A room with id '" + room.getId() + "' already exists.");
            return Response.status(Response.Status.CONFLICT).entity(body).build();
        }

        roomStore.save(room);

        // Build the Location header pointing to the newly created resource.
        URI location = UriBuilder.fromUri(uriInfo.getAbsolutePath())
                .path(room.getId())
                .build();

        return Response.created(location).entity(room).build();
    }

    @DELETE
    @Path("/{roomId}")
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        Room room = roomStore.findById(roomId);
        if (room == null) {
            return notFound("Room with id '" + roomId + "' does not exist.");
        }

        // Safety check: block deletion if the room still has sensors assigned.
        // (Part 5 will refactor this into a custom RoomNotEmptyException +
        // ExceptionMapper.)
        if (room.getSensorIds() != null && !room.getSensorIds().isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", 409);
            body.put("error", "Conflict");
            body.put("message",
                    "Cannot delete room '" + roomId
                            + "' because it still has " + room.getSensorIds().size()
                            + " active sensor(s) assigned. Remove or reassign the sensors first.");
            body.put("activeSensors", room.getSensorIds());
            return Response.status(Response.Status.CONFLICT).entity(body).build();
        }

        roomStore.delete(roomId);
        return Response.noContent().build(); // 204
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