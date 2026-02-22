# Service Layer Template

Templates for Application Service and Command records. Based on `application/service/TenantService.java`.

## Command Records

Path: `backend/src/main/java/com/worklog/application/command/`

```java
package com.worklog.application.command;

// Create command — all fields needed for creation
public record Create{Aggregate}Command({allFields}) {}

// Update command — ID + mutable fields
public record Update{Aggregate}Command(UUID id, {mutableFields}) {}
```

If tenant-scoped, include `UUID tenantId` as the first field in CreateCommand.

## {Aggregate}Service.java

Path: `backend/src/main/java/com/worklog/application/service/`

```java
package com.worklog.application.service;

import com.worklog.application.command.Create{Aggregate}Command;
import com.worklog.application.command.Update{Aggregate}Command;
import com.worklog.domain.{aggregate}.{Aggregate};
import com.worklog.domain.{aggregate}.{Aggregate}Id;
import com.worklog.infrastructure.repository.{Aggregate}Repository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class {Aggregate}Service {

    private final {Aggregate}Repository repository;

    public {Aggregate}Service({Aggregate}Repository repository) {
        this.repository = repository;
    }

    @Transactional
    public UUID create(Create{Aggregate}Command command) {
        {Aggregate} agg = {Aggregate}.create({command.fields()});
        repository.save(agg);
        return agg.getId().value();
    }

    @Transactional
    public void update(Update{Aggregate}Command command) {
        {Aggregate} agg = repository
                .findById(new {Aggregate}Id(command.id()))
                .orElseThrow(() -> new IllegalArgumentException("{Aggregate} not found: " + command.id()));

        agg.update({command.fields()});
        repository.save(agg);
    }

    @Transactional
    public void delete(UUID id) {
        {Aggregate} agg = repository
                .findById(new {Aggregate}Id(id))
                .orElseThrow(() -> new IllegalArgumentException("{Aggregate} not found: " + id));

        agg.delete();
        repository.save(agg);
    }

    public {Aggregate} findById(UUID id) {
        return repository.findById(new {Aggregate}Id(id)).orElse(null);
    }
}
```

## Key Rules

- `@Transactional` on all write methods (create, update, delete)
- Read methods (findById) do NOT need `@Transactional`
- Constructor injection (no `@Autowired`)
- `create()` returns `UUID`; `update()` and `delete()` return `void`
- `update()` takes a command object (not individual params) — consistent with `create()`
- Throw `IllegalArgumentException` for not-found (consistent with existing code)
