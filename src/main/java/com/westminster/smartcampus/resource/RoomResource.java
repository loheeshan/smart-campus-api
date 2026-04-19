package com.westminster.smartcampus.resource;

import com.westminster.smartcampus.exception.RoomNotEmptyException;
import com.westminster.smartcampus.model.Room;
import com.westminster.smartcampus.store.RoomStore;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

import com.westminster.smartcampus.dto.ErrorResponse;

import java.net.URI;
import java.util.Collection;
import java.util.UUID;

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
            throw notFound("Room with id '" + roomId + "' does not exist.");
        }
        return Response.ok(room).build();
    }

    @POST
    public Response createRoom(Room room, @Context UriInfo uriInfo) {
        if (room == null || room.getName() == null || room.getName().isBlank()) {
            throw badRequest("Field 'name' is required.");
        }
        if (room.getCapacity() <= 0) {
            throw badRequest("Field 'capacity' must be a positive integer.");
        }

        if (room.getId() == null || room.getId().isBlank()) {
            room.setId("ROOM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        if (roomStore.exists(room.getId())) {
            throw conflict("A room with id '" + room.getId() + "' already exists.");
        }

        roomStore.save(room);

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
            throw notFound("Room with id '" + roomId + "' does not exist.");
        }

        if (room.getSensorIds() != null && !room.getSensorIds().isEmpty()) {
            // Thrown -> intercepted by RoomNotEmptyMapper -> becomes 409 JSON.
            throw new RoomNotEmptyException(roomId, room.getSensorIds().size());
        }

        roomStore.delete(roomId);
        return Response.noContent().build();
    }

    // ----- WebApplicationException helpers for generic 400/404/409 -----

    private WebApplicationException notFound(String message) {
        return new WebApplicationException(
                Response.status(Response.Status.NOT_FOUND)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(new ErrorResponse(404, "Not Found", message))
                        .build());
    }

    private WebApplicationException badRequest(String message) {
        return new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(new ErrorResponse(400, "Bad Request", message))
                        .build());
    }

    private WebApplicationException conflict(String message) {
        return new WebApplicationException(
                Response.status(Response.Status.CONFLICT)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(new ErrorResponse(409, "Conflict", message))
                        .build());
    }
}
