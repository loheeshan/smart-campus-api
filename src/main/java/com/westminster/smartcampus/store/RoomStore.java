package com.westminster.smartcampus.store;

import com.westminster.smartcampus.model.Room;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory store for Room objects.
 *
 * Why ConcurrentHashMap? JAX-RS instantiates a new resource class per request
 * by default, but they all share this singleton store. Without a thread-safe
 * map, two simultaneous POSTs could corrupt the data (lost writes, ghost reads).
 */
public class RoomStore {

    private static final RoomStore INSTANCE = new RoomStore();
    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    private RoomStore() {
    }

    public static RoomStore getInstance() {
        return INSTANCE;
    }

    public Collection<Room> findAll() {
        return rooms.values();
    }

    public Room findById(String id) {
        return rooms.get(id);
    }

    public Room save(Room room) {
        rooms.put(room.getId(), room);
        return room;
    }

    public boolean exists(String id) {
        return rooms.containsKey(id);
    }

    public Room delete(String id) {
        return rooms.remove(id);
    }
}
