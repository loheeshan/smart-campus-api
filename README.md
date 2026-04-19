# Smart Campus Sensor & Room Management API

A RESTful web service built with **JAX-RS (Jersey 3.1.5)** on an embedded **Grizzly** HTTP server. The service exposes a campus-wide infrastructure management API for rooms, sensors, and historical sensor readings, following REST architectural principles with proper resource nesting, meaningful HTTP status codes, and a resilient error-handling strategy.

**Module:** 5COSC022W – Client-Server Architectures
**Base URI:** `http://localhost:8080/api/v1`

---

## 1. API Overview

The API models three primary entities:

- **Room** – a physical space on campus (id, name, capacity, list of sensor IDs assigned to it).
- **Sensor** – a device deployed inside a room (id, type, status, currentValue, roomId).
- **SensorReading** – an immutable historical measurement recorded by a sensor (id, timestamp, value).

Data is held exclusively in thread-safe in-memory data structures (`ConcurrentHashMap`, `CopyOnWriteArrayList`). No database or file persistence is used, as required by the specification.

### Resource map

| Method | Path                                  | Purpose                                                        |
| ------ | ------------------------------------- | -------------------------------------------------------------- |
| GET    | `/api/v1`                             | Discovery endpoint with metadata and HATEOAS links             |
| GET    | `/api/v1/rooms`                       | List all rooms                                                 |
| POST   | `/api/v1/rooms`                       | Create a new room (returns 201 + Location header)              |
| GET    | `/api/v1/rooms/{roomId}`              | Fetch a specific room                                          |
| DELETE | `/api/v1/rooms/{roomId}`              | Delete a room (blocked with 409 if it has sensors)             |
| GET    | `/api/v1/sensors`                     | List sensors, optionally filtered by `?type=`                  |
| GET    | `/api/v1/sensors/{sensorId}`          | Fetch a specific sensor                                        |
| POST   | `/api/v1/sensors`                     | Register a sensor (422 if `roomId` does not exist)             |
| DELETE | `/api/v1/sensors/{sensorId}`          | Delete a sensor and detach it from its room                    |
| GET    | `/api/v1/sensors/{sensorId}/readings` | Fetch reading history for a sensor                             |
| POST   | `/api/v1/sensors/{sensorId}/readings` | Append a reading and update the parent sensor's `currentValue` |

### Error handling

All error paths return a consistent JSON body shape (`status`, `error`, `message`, `timestamp`) produced by dedicated `ExceptionMapper` providers. A global `ExceptionMapper<Throwable>` acts as a safety net so that stack traces never leak to clients.

---

## 2. Build & Run Instructions

### Prerequisites

- Java 17 or newer (tested on Java 23)
- Apache Maven 3.8+
- Port 8080 available

### Build

From the project root:

```bash
mvn clean package
```

This produces a single executable JAR at `target/smart-campus-api.jar` containing all dependencies (shaded).

### Run

```bash
java -jar target/smart-campus-api.jar
```

You should see:

```
Smart Campus API is running
Discovery endpoint: http://localhost:8080/api/v1/
Press Ctrl+C to stop.
```

The service is now reachable at `http://localhost:8080/api/v1`.

### Stop the server

Press `Ctrl+C` in the terminal where the server is running.

---

## 3. Sample `curl` Commands

The five commands below exercise distinct parts of the API. (On Windows PowerShell, use `curl.exe` explicitly rather than the `curl` alias.)

### 3.1 Discovery endpoint

```bash
curl http://localhost:8080/api/v1
```

### 3.2 Create a room

```bash
curl -i -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"LAB-101","name":"Physics Lab","capacity":30}'
```

Returns `201 Created` with a `Location: http://localhost:8080/api/v1/rooms/LAB-101` header.

### 3.3 Register a sensor inside that room

```bash
curl -i -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-001","type":"Temperature","status":"ACTIVE","currentValue":22.5,"roomId":"LAB-101"}'
```

### 3.4 Filter sensors by type

```bash
curl http://localhost:8080/api/v1/sensors?type=Temperature
```

### 3.5 Post a new reading (triggers the parent-sensor `currentValue` side effect)

```bash
curl -i -X POST http://localhost:8080/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":23.7}'
```

A subsequent `GET /api/v1/sensors/TEMP-001` will show `currentValue: 23.7`.

---

## 4. Project Structure

```
src/main/java/com/westminster/smartcampus/
├── Main.java                      Bootstraps the Grizzly HTTP server
├── SmartCampusApplication.java    @ApplicationPath("/api/v1") subclass
├── model/                         POJOs: Room, Sensor, SensorReading
├── store/                         Thread-safe in-memory stores
├── resource/                      JAX-RS resource classes
├── exception/                     Custom checked scenarios
├── mapper/                        ExceptionMapper providers
├── filter/                        Request/response logging filter
└── dto/                           Shared ErrorResponse shape
```

---

## 5. Conceptual Report

The following sections contain the written answers required by the coursework specification. Each answer corresponds to a question posed in a specific part of the brief.

---

### 5.1 (Part 1.1) JAX-RS Resource Lifecycle and In-Memory Data Synchronisation

By default, a JAX-RS resource class follows a **request-scoped (per-request) lifecycle**: the container instantiates a fresh instance of the resource class for every incoming HTTP request and discards it once the response is written. The runtime does not treat it as a singleton unless the class is explicitly annotated with `@Singleton` or registered as a singleton via the `Application` subclass.

This decision has significant implications for any shared state the API relies on. Fields declared as instance variables on the resource class are effectively useless for persistence, because they are garbage-collected with the instance at the end of each request. Any data that must survive between requests — for example, the map of rooms and sensors — must therefore live **outside** the resource class, typically in a shared singleton store.

Once state is shared across multiple instances, concurrency becomes a concern. Because Grizzly (like any servlet container) dispatches incoming requests on a thread pool, two or more requests can be processed in parallel, each holding its own resource instance but all touching the same underlying collection. A plain `HashMap` or `ArrayList` is not safe under this model: concurrent writes can corrupt internal buckets, readers can observe half-constructed entries, and in extreme cases the container can enter an infinite loop on resize.

The design therefore uses thread-safe structures deliberately. Rooms and sensors live in `ConcurrentHashMap` instances held by static singletons (`RoomStore`, `SensorStore`), which provide lock-striping on writes and lock-free reads. The per-sensor reading history uses `CopyOnWriteArrayList`, which suits an append-heavy, read-heavy log where readers must never block. Where a single logical operation spans multiple structures (for example, registering a sensor and attaching it to a room), the operations are kept small and the individual stores' atomic primitives are used, rather than layering broad synchronised blocks that would harm throughput.

---

### 5.2 (Part 1.2) The Value of Hypermedia (HATEOAS)

HATEOAS — Hypermedia As The Engine Of Application State — treats the responses of an API as self-describing documents that include the links a client needs to navigate the service, rather than as terminal data payloads. In the discovery endpoint of this API, the response body advertises the paths to every primary resource collection under a `_links` object, so a client can discover `/rooms`, `/sensors`, and the templated `/sensors/{sensorId}/readings` path without ever reading a PDF of the URI scheme.

The benefits over static documentation are practical. Static documentation becomes stale: if a URI is renamed or a new collection is added, every consumer must be updated in sync, or their hard-coded paths silently break. Hypermedia, in contrast, allows the server to evolve its URI structure without breaking clients that follow links rather than construct them. It also makes the API easier to explore; a new developer can hit the root endpoint and follow their way down the resource graph, much as a human would click through a website. Finally, hypermedia improves security posture because clients stop accumulating hard-coded knowledge about server internals, which in turn reduces the blast radius of any future restructuring.

---

### 5.3 (Part 2.1) Returning Full Objects vs. IDs in Collection Responses

When a collection endpoint such as `GET /rooms` returns the full Room object for every entry, every response carries the human-readable name, capacity, and the full list of assigned sensor IDs. This is convenient for simple clients — a single round trip fetches everything needed for a list view — but it scales poorly. On a campus with thousands of rooms, payload size grows linearly with both the number of rooms and the number of sensors per room, because the nested `sensorIds` list is serialised on every element. Network bandwidth and JSON parsing cost both become non-trivial, especially on mobile clients or over high-latency links.

Returning only IDs inverts the trade-off: responses are compact and cheap to transmit, but the client must now issue a follow-up request per element to retrieve any detail, which can cause the "N+1 request" problem familiar from ORMs. A middle ground is often the most practical choice in production: the collection endpoint returns a lightweight summary of each resource (identifier plus a couple of headline fields) while detail is reserved for the per-resource endpoint. This keeps list views cheap without forcing clients into a cascade of round trips for trivial use cases. The current implementation returns full objects because the scale of the coursework brief does not justify the complexity of a summary projection, but the design would be the natural first refactor under real load.

---

### 5.4 (Part 2.2) Is the DELETE Operation Idempotent?

Idempotency means that executing the same operation multiple times produces the same observable server state as executing it once. The `DELETE /rooms/{roomId}` implementation is idempotent in that sense.

The first call looks up the room, verifies that no sensors are attached, removes the entry from the store, and returns `204 No Content`. The room is now gone. If a client — perhaps due to a flaky network, a retrying proxy, or a nervous user clicking twice — sends the exact same request again, the lookup returns `null`, the custom `WebApplicationException` fires, and the response is `404 Not Found`. The response _status code_ is different on the second call, but the _state of the server_ is unchanged: the room was absent before the second call and remains absent after it. No further damage is done; no exception corrupts the store; no cascading side effect is triggered.

This distinction matters because HTTP's definition of idempotency concerns server state, not response codes. `404` on a repeat DELETE is sometimes regarded as noise rather than a contract violation; some designs return `204` unconditionally to hide this wrinkle from clients, but doing so obscures genuine mistakes (for example, a DELETE against a typo'd ID). The implementation here prefers the honest `404`, accepting that retries will see it, because the safety property that actually matters — "the room is deleted" — holds regardless of how many times the request arrives.

---

### 5.5 (Part 3.1) Consequences of an `@Consumes` Media-Type Mismatch

The `@Consumes(MediaType.APPLICATION_JSON)` annotation on the sensor POST method declares a contract with JAX-RS: this method can only accept request bodies whose `Content-Type` header matches `application/json`. If a client sends `text/plain` or `application/xml`, the container does not attempt to deserialise the body against the method's parameter type and instead short-circuits the request with an **HTTP 415 Unsupported Media Type** response. The resource method is never invoked, so no half-baked payload ever reaches the validation logic.

The practical consequence is that content negotiation is enforced at the framework layer rather than inside business logic. The resource author does not need to write defensive code to reject XML or plain text; JAX-RS rejects it before control is transferred. This separation improves both safety (the body reader is only asked to parse formats it understands, eliminating a whole class of deserialisation vulnerabilities) and clarity (the method body can assume a valid JSON object was received). On the client side, the 415 response is a clear signal that the `Content-Type` header is wrong, which is far more useful than a vague 400 with a parser error message.

---

### 5.6 (Part 3.2) Why `@QueryParam` Is Preferable to Path Parameters for Filtering

Path parameters identify **what** resource is being addressed; query parameters describe **how** the collection is being viewed. This semantic distinction is important, and REST conventions place filtering firmly in the latter category.

If the API exposed `/sensors/type/CO2` instead of `/sensors?type=CO2`, the URI would suggest that "CO2" is a distinct sub-resource in its own right, on par with `/sensors/TEMP-001`. That is misleading: "CO2" is not a resource, it is a filter predicate over the sensor collection. The path form also does not compose. Filtering by both `type` and, say, `status` would require either a new nested segment (`/sensors/type/CO2/status/ACTIVE`, which explodes combinatorially) or abandoning the path convention mid-design. Query parameters compose trivially: `/sensors?type=CO2&status=ACTIVE` is unambiguous and extends without breaking earlier URIs.

Query parameters are also the form that HTTP caching infrastructure, browsers, and search engines already understand as "same resource, different view". `/sensors?type=CO2` and `/sensors?type=Temperature` share a cache key root at `/sensors` and are naturally treated as variations of the same collection, which is precisely the relationship the API intends to express.

---

### 5.7 (Part 4.1) Architectural Benefits of the Sub-Resource Locator Pattern

A sub-resource locator is a method that carries `@Path` but no HTTP verb annotation. Instead of returning a `Response`, it returns another resource class instance, and JAX-RS continues the path-matching process against that returned object. In this API, `SensorResource#getReadingResource` matches `/{sensorId}/readings` and returns a new `SensorReadingResource` constructed with the parent sensor's ID.

The first benefit is **separation of concerns**. `SensorResource` is responsible for sensors; everything about sensor reading history lives inside `SensorReadingResource`. If the reading logic grows to include, say, aggregation endpoints or CSV exports, those methods live in the dedicated class rather than bloating the sensor controller. Over the lifetime of a real API this is the difference between a controller that stays at a couple of hundred lines and one that drifts into four-figure territory with every engineer tempted to just "add one more method".

The second benefit is **contextual state**. The locator passes the parent identifier into the constructor of the sub-resource, so every method inside `SensorReadingResource` already knows which sensor it is operating on without re-parsing the path. This is cleaner than repeating `@PathParam("sensorId")` on every method in a flat controller and avoids inconsistency if one method forgets to validate the parent.

The third benefit is **testability**. `SensorReadingResource` can be instantiated directly in a unit test with a mock sensor ID, exercising its behaviour without needing the full JAX-RS routing infrastructure. A flat controller with a dozen nested paths is much harder to test in isolation.

---

### 5.8 (Part 5.2) Why 422 Is More Semantically Accurate Than 404 for a Missing Reference in a Payload

The `404 Not Found` status code is defined with respect to the **request URI**: the resource identified by the URL does not exist on this server. When a client POSTs a new sensor to `/api/v1/sensors` with a `roomId` of `"DOES-NOT-EXIST"`, the request URI is perfectly valid — `/api/v1/sensors` is the collection endpoint, and it exists. Returning 404 would be semantically confused, because the client is not asking to fetch `/sensors/DOES-NOT-EXIST`; they are asking to create a new sensor, and the problem lies inside the body they submitted.

`422 Unprocessable Entity` exists precisely for this case. The HTTP specification defines it as "the server understands the content type of the request entity, and the syntax of the request entity is correct, but it was unable to process the contained instructions". The JSON parses cleanly, the required fields are present, the `Content-Type` was honoured — only the referential integrity has failed. A 422 tells the client, unambiguously, "the payload itself is the problem, not the URL or the format", which directs debugging toward the right place.

From an API-consumer perspective, this distinction reduces guesswork. A 404 on a POST implies "the endpoint doesn't exist" and might send a developer chasing routing configuration. A 422 on a POST implies "your data has a semantic issue", which is a much tighter hint and is the kind of feedback a validation framework would produce. Consistently using 422 for referential problems and 404 strictly for missing URIs keeps the API's error vocabulary precise.

---

### 5.9 (Part 5.4) Cybersecurity Risks of Exposing Stack Traces

A raw Java stack trace returned to an external consumer is a gift to an attacker performing reconnaissance. Several categories of sensitive information leak from it at once.

The trace reveals the **package structure** of the application (`com.westminster.smartcampus.resource.SensorResource`), which tells an attacker what frameworks and patterns are in use and often lets them infer which vendor or internal team wrote the software. It reveals **library versions** through third-party frames (for example, Jersey internals or Jackson deserialiser classes), which can be cross-referenced against public CVE databases to identify known vulnerabilities in those exact versions. It reveals **file paths on the host** (for example, `/home/deploy/app/...`), which aids further probing for misconfigured file-serving endpoints, backup files, or directory traversal opportunities.

Perhaps more damagingly, stack traces reveal **control flow and logic**. An attacker sending crafted inputs can observe which internal methods are reached, how deep validation runs, and where defensive checks sit relative to the actual database or business calls. This gives them a free map of the application's invariants and is often enough to construct a targeted payload — for example, a SQL injection attempt tuned to the specific ORM in use or a deserialisation gadget chain matching the exact Jackson version.

The global `ExceptionMapper<Throwable>` closes this leak. It intercepts every unexpected exception, logs the full trace server-side for legitimate debugging, and returns a sanitised JSON body to the client containing only a generic message and a 500 status. The defender retains the information they need; the attacker loses it.

---

### 5.10 (Part 5.5) Why Filters Are Superior to Inline `Logger.info()` Calls

Logging is a classic cross-cutting concern: it applies everywhere, it is orthogonal to business logic, and it benefits from being uniform. Scattering `Logger.info()` statements throughout every resource method violates all three of those properties.

Every new resource method becomes an opportunity to forget the logging call, or to format it differently, or to log a subtly wrong field. Over time the logs become inconsistent — some endpoints log the URI, others log only the method name, others log nothing at all — and becomes unreliable precisely when it is most needed, during an incident. Worse, changing the log format or routing it to a new destination requires editing every resource class, which is both tedious and risky.

Implementing `ContainerRequestFilter` and `ContainerResponseFilter` in a single `@Provider` class solves all of this. The filter runs automatically for every request that JAX-RS routes, so no resource author can accidentally opt out. The log format is defined once and is therefore uniform across the entire API. Changing where logs go — to a file, to a structured JSON logger, to a central aggregator like the ELK stack — requires editing one class. The resource methods themselves stay focused on their domain work, which makes them shorter and easier to read.

This is the same principle that drives middleware, interceptors, and aspect-oriented programming in other ecosystems: concerns that apply everywhere should be expressed in one place and composed into the request pipeline, not duplicated across every handler.

---

## 6. Video Demonstration

The mandatory video demonstration accompanying this submission can be found at:

> **[VIDEO LINK PLACEHOLDER — replace with your Blackboard/upload link before submitting]**

The video walks through the Postman test collection, covering each of the five parts of the coursework, including the 201/Location headers on POST, the `@QueryParam` filtering on `/sensors`, the sub-resource navigation to `/sensors/{id}/readings`, and the full set of error responses (409, 422, 403, 500).

---

_End of report._
