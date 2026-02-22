# Repository Layer Template

Event sourcing repository with projection table. Based on `infrastructure/repository/TenantRepository.java`.

File goes in `backend/src/main/java/com/worklog/infrastructure/repository/{Aggregate}Repository.java`.

## {Aggregate}Repository.java

```java
package com.worklog.infrastructure.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.{aggregate}.*;
import com.worklog.eventsourcing.EventStore;
import com.worklog.eventsourcing.StoredEvent;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class {Aggregate}Repository {

    private final EventStore eventStore;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public {Aggregate}Repository(EventStore eventStore, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void save({Aggregate} aggregate) {
        List<DomainEvent> events = aggregate.getUncommittedEvents();
        if (events.isEmpty()) {
            return;
        }

        eventStore.append(
                aggregate.getId().value(),
                aggregate.getAggregateType(),
                events,
                aggregate.getVersion());

        aggregate.setVersion(aggregate.getVersion() + events.size());
        updateProjection(aggregate);
        aggregate.clearUncommittedEvents();
    }

    public Optional<{Aggregate}> findById({Aggregate}Id id) {
        List<StoredEvent> storedEvents = eventStore.load(id.value());
        if (storedEvents.isEmpty()) {
            return Optional.empty();
        }

        {Aggregate} aggregate = createEmpty();

        for (StoredEvent storedEvent : storedEvents) {
            DomainEvent event = deserializeEvent(storedEvent);
            aggregate.replay(event);
        }

        aggregate.clearUncommittedEvents();
        return Optional.of(aggregate);
    }

    // If tenant-scoped, add:
    // public List<{Aggregate}> findByTenantId(TenantId tenantId) {
    //     List<UUID> ids = jdbcTemplate.queryForList(
    //             "SELECT id FROM {table_name} WHERE tenant_id = ?",
    //             UUID.class,
    //             tenantId.value());
    //     return ids.stream()
    //             .map(id -> findById({Aggregate}Id.of(id)))
    //             .filter(Optional::isPresent)
    //             .map(Optional::get)
    //             .toList();
    // }

    private void updateProjection({Aggregate} aggregate) {
        jdbcTemplate.update(
                "INSERT INTO {table_name} (id, {columns}, version, created_at, updated_at) "
                        + "VALUES ({placeholders}, NOW(), NOW()) "
                        + "ON CONFLICT (id) DO UPDATE SET "
                        + "{column_updates}, "
                        + "version = EXCLUDED.version, "
                        + "updated_at = NOW()",
                // Parameter values in order:
                aggregate.getId().value()
                // , aggregate.getTenantId().value()  // if tenant-scoped
                // , aggregate.getName()
                // , aggregate.getVersion()
                );
    }

    private DomainEvent deserializeEvent(StoredEvent storedEvent) {
        try {
            return switch (storedEvent.eventType()) {
                case "{Aggregate}Created" ->
                    objectMapper.readValue(storedEvent.payload(), {Aggregate}Created.class);
                case "{Aggregate}Updated" ->
                    objectMapper.readValue(storedEvent.payload(), {Aggregate}Updated.class);
                case "{Aggregate}Deleted" ->
                    objectMapper.readValue(storedEvent.payload(), {Aggregate}Deleted.class);
                default ->
                    throw new IllegalArgumentException("Unknown event type: " + storedEvent.eventType());
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event: " + storedEvent.eventType(), e);
        }
    }

    private {Aggregate} createEmpty() {
        try {
            Constructor<{Aggregate}> constructor = {Aggregate}.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create empty {Aggregate} instance", e);
        }
    }
}
```

## Projection SQL Pattern

The `updateProjection()` SQL must match the migration table schema:

```sql
INSERT INTO {table_name} (id, tenant_id, name, status, version, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    status = EXCLUDED.status,
    version = EXCLUDED.version,
    updated_at = NOW()
```

## Key Rules

- `save()`: append events → bump version → update projection → clear events (this exact order)
- `findById()`: load stored events → create empty aggregate via reflection → replay all events → clear uncommitted
- `deserializeEvent()` switch must cover ALL event types for this aggregate
- `createEmpty()` uses reflection because aggregate constructor is private
- Always include `version` in projection for optimistic locking
- `@Transactional` only on `save()` — reads don't need it
