# Controller Layer Template

Template for REST controller with nested DTO records. Based on `api/TenantController.java`.

File goes in `backend/src/main/java/com/worklog/api/{Aggregate}Controller.java`.

## Top-Level Aggregate (not tenant-scoped)

```java
package com.worklog.api;

import com.worklog.application.command.Create{Aggregate}Command;
import com.worklog.application.service.{Aggregate}Service;
import com.worklog.domain.{aggregate}.{Aggregate};
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/{urlPath}")
public class {Aggregate}Controller {

    private final {Aggregate}Service service;

    public {Aggregate}Controller({Aggregate}Service service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateRequest request) {
        Create{Aggregate}Command command = new Create{Aggregate}Command({request.fields()});
        UUID id = service.create(command);

        Map<String, Object> response = new HashMap<>();
        response.put("id", id.toString());
        // Add returned fields from request
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> findById(@PathVariable UUID id) {
        {Aggregate} agg = service.findById(id);
        if (agg == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", agg.getId().value().toString());
        // Add fields
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable UUID id, @RequestBody UpdateRequest request) {
        service.update(id, {request.fields()});
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Nested DTOs ---

    public record CreateRequest({fields}) {}

    public record UpdateRequest({mutableFields}) {}
}
```

## Tenant-Scoped Aggregate

URL pattern changes to include tenantId:

```java
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/{urlPath}")
public class {Aggregate}Controller {

    // ...

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable UUID tenantId,
            @RequestBody CreateRequest request) {
        Create{Aggregate}Command command = new Create{Aggregate}Command(tenantId, {request.fields()});
        UUID id = service.create(command);
        // ...
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> findById(
            @PathVariable UUID tenantId,
            @PathVariable UUID id) {
        // ...
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(@PathVariable UUID tenantId) {
        // Return all aggregates for this tenant
    }
}
```

## HTTP Status Conventions

| Operation | Method | Success Status | Failure Status |
|-----------|--------|---------------|----------------|
| create | POST | 201 Created | 400 Bad Request |
| findById | GET `/{id}` | 200 OK | 404 Not Found |
| list | GET | 200 OK | — |
| update | PATCH `/{id}` | 204 No Content | 404 Not Found |
| delete | DELETE `/{id}` | 204 No Content | 404 Not Found |

## Key Rules

- DTOs are `public record` nested inside the controller class
- Use `Map<String, Object>` for responses (project convention)
- Constructor injection (no `@Autowired`)
- `@PathVariable` for URL params, `@RequestBody` for JSON body
