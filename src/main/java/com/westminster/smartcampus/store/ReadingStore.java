package com.westminster.smartcampus.store;

import com.westminster.smartcampus.model.SensorReading;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stores historical readings keyed by sensorId.
 * CopyOnWriteArrayList keeps reads lock-free while writes are safe.
 */
public class ReadingStore {

    private static final ReadingStore INSTANCE = new ReadingStore();
    private final ConcurrentHashMap<String, List<SensorReading>> readings = new ConcurrentHashMap<>();

    private ReadingStore() {
    }

    public static ReadingStore getInstance() {
        return INSTANCE;
    }

    public List<SensorReading> findBySensorId(String sensorId) {
        return readings.getOrDefault(sensorId, new CopyOnWriteArrayList<>());
    }

    public SensorReading add(String sensorId, SensorReading reading) {
        readings.computeIfAbsent(sensorId, k -> new CopyOnWriteArrayList<>()).add(reading);
        return reading;
    }
}
