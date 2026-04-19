package com.westminster.smartcampus.store;

import com.westminster.smartcampus.model.Sensor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SensorStore {

    private static final SensorStore INSTANCE = new SensorStore();
    private final ConcurrentHashMap<String, Sensor> sensors = new ConcurrentHashMap<>();

    private SensorStore() {
    }

    public static SensorStore getInstance() {
        return INSTANCE;
    }

    public Collection<Sensor> findAll() {
        return sensors.values();
    }

    public List<Sensor> findByType(String type) {
        return sensors.values().stream()
                .filter(s -> s.getType() != null && s.getType().equalsIgnoreCase(type))
                .collect(Collectors.toList());
    }

    public Sensor findById(String id) {
        return sensors.get(id);
    }

    public Sensor save(Sensor sensor) {
        sensors.put(sensor.getId(), sensor);
        return sensor;
    }

    public boolean exists(String id) {
        return sensors.containsKey(id);
    }

    public Sensor delete(String id) {
        return sensors.remove(id);
    }
}
