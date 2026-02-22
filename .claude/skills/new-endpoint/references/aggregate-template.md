# Domain Layer Templates

Templates for ID, Event, and Aggregate classes. Based on `domain/tenant/` patterns.

All files go in `backend/src/main/java/com/worklog/domain/{aggregate}/`.

## {Aggregate}Id.java

```java
package com.worklog.domain.{aggregate};

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

public record {Aggregate}Id(UUID value) implements EntityId {

    public {Aggregate}Id {
        if (value == null) {
            throw new IllegalArgumentException("{Aggregate}Id value cannot be null");
        }
    }

    public static {Aggregate}Id generate() {
        return new {Aggregate}Id(UUID.randomUUID());
    }

    public static {Aggregate}Id of(UUID value) {
        return new {Aggregate}Id(value);
    }

    public static {Aggregate}Id of(String value) {
        return new {Aggregate}Id(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
```

## {Aggregate}Created.java (Event)

One event record per operation. All events follow this pattern:

```java
package com.worklog.domain.{aggregate};

import com.worklog.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record {Aggregate}Created(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        // Add all creation fields here (e.g. String name, UUID tenantId)
        {fields})
        implements DomainEvent {

    public static {Aggregate}Created create(UUID aggregateId, {factoryParams}) {
        return new {Aggregate}Created(UUID.randomUUID(), Instant.now(), aggregateId, {args});
    }

    @Override
    public String eventType() {
        return "{Aggregate}Created";
    }

    @Override
    public UUID eventId() {
        return eventId;
    }

    @Override
    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public UUID aggregateId() {
        return aggregateId;
    }
}
```

**Updated event** — only mutable fields:

```java
public record {Aggregate}Updated(UUID eventId, Instant occurredAt, UUID aggregateId, {mutableFields})
        implements DomainEvent {

    public static {Aggregate}Updated create(UUID aggregateId, {params}) {
        return new {Aggregate}Updated(UUID.randomUUID(), Instant.now(), aggregateId, {args});
    }

    @Override
    public String eventType() {
        return "{Aggregate}Updated";
    }
    // ... same eventId(), occurredAt(), aggregateId() accessors
}
```

**Deleted event** — typically no extra fields:

```java
public record {Aggregate}Deleted(UUID eventId, Instant occurredAt, UUID aggregateId)
        implements DomainEvent {

    public static {Aggregate}Deleted create(UUID aggregateId) {
        return new {Aggregate}Deleted(UUID.randomUUID(), Instant.now(), aggregateId);
    }

    @Override
    public String eventType() {
        return "{Aggregate}Deleted";
    }
    // ... same accessors
}
```

## {Aggregate}.java (Aggregate Root)

```java
package com.worklog.domain.{aggregate};

import com.worklog.domain.shared.AggregateRoot;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.DomainException;
// import tenant ID if tenant-scoped:
// import com.worklog.domain.tenant.TenantId;

public class {Aggregate} extends AggregateRoot<{Aggregate}Id> {

    private {Aggregate}Id id;
    // If tenant-scoped:
    // private TenantId tenantId;
    // Add fields for each property
    // private String name;

    private {Aggregate}() {}

    /**
     * Factory method — creates a new aggregate and raises Created event.
     */
    public static {Aggregate} create({createParams}) {
        // Validate inputs
        // validateName(name);

        {Aggregate} agg = new {Aggregate}();
        {Aggregate}Id aggId = {Aggregate}Id.generate();

        {Aggregate}Created event = {Aggregate}Created.create(aggId.value(), {eventArgs});
        agg.raiseEvent(event);

        return agg;
    }

    /**
     * Update mutable fields.
     */
    public void update({updateParams}) {
        // Validate inputs
        {Aggregate}Updated event = {Aggregate}Updated.create(this.id.value(), {eventArgs});
        raiseEvent(event);
    }

    /**
     * Soft-delete (if delete operation is included).
     */
    public void delete() {
        {Aggregate}Deleted event = {Aggregate}Deleted.create(this.id.value());
        raiseEvent(event);
    }

    @Override
    protected void apply(DomainEvent event) {
        switch (event) {
            case {Aggregate}Created e -> {
                this.id = {Aggregate}Id.of(e.aggregateId());
                // this.tenantId = TenantId.of(e.tenantId());
                // this.name = e.name();
            }
            case {Aggregate}Updated e -> {
                // this.name = e.name();
            }
            case {Aggregate}Deleted e -> {
                // handle deletion state
            }
            default ->
                throw new IllegalArgumentException(
                        "Unknown event type: " + event.getClass().getName());
        }
    }

    // Validation methods
    // private static void validateName(String name) {
    //     if (name == null || name.isBlank()) {
    //         throw new DomainException("NAME_REQUIRED", "Name cannot be null or blank");
    //     }
    // }

    // Getters

    @Override
    public {Aggregate}Id getId() {
        return id;
    }

    @Override
    public String getAggregateType() {
        return "{Aggregate}";
    }

    // public TenantId getTenantId() { return tenantId; }
    // public String getName() { return name; }
}
```

## Key Rules

- Private no-arg constructor (required for reflection-based reconstitution in Repository)
- All state changes go through `raiseEvent()` → `apply()`
- `apply()` switch must cover ALL event types for this aggregate
- Validation happens in public methods BEFORE raising events
- Use `DomainException` for domain rule violations (not `IllegalArgumentException`)
- If tenant-scoped, store `TenantId` as a field and include `tenantId` in the Created event
