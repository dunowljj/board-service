# Persistence Standards

## JPA Usage

- Use aggregate roots for persistence
- Avoid bidirectional relationships unless necessary
- Keep transactions at use-case boundary

## Port-Level Read Shape

Design Output Ports so the Application layer cannot accidentally issue the
same query twice.

### Pagination

- A page-reading Output Port method returns both `items` and `totalElements`
  **in a single return value** (e.g. `PostPage findPage(int page, int size)`).
- Do NOT expose a separate `count()` method on the same Output Port and then
  have the Service call both `findPage(...)` and `count()`. Spring Data's
  `Page` already carries the total; throwing it away and re-issuing a count
  against the same predicate is a review-level issue.
- The Driven Adapter extracts `getContent()` + `getTotalElements()` from the
  underlying `Page` into the Port's DTO once.
- Service is responsible for computing derived values (e.g. `totalPages`) and
  for guarding edge inputs (e.g. `size == 0`).

### Existence Checks

- When a caller only needs to know whether a row exists (typical in
  `delete`/authorization pre-conditions), Output Port exposes a lightweight
  `existsById`-style method and the Service uses it.
- Loading a full aggregate via `findById` and discarding it is forbidden for
  this purpose — it forces a full row read plus entity materialization.

## Performance

- Analyze query plans for complex joins
- Avoid N+1
- Add indexes intentionally (ADR if strategic)

## Concurrency

- Explicit locking strategy
- Avoid hidden optimistic lock failures