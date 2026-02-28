# Persistence Standards

## JPA Usage

- Use aggregate roots for persistence
- Avoid bidirectional relationships unless necessary
- Keep transactions at use-case boundary

## Performance

- Analyze query plans for complex joins
- Avoid N+1
- Add indexes intentionally (ADR if strategic)

## Concurrency

- Explicit locking strategy
- Avoid hidden optimistic lock failures