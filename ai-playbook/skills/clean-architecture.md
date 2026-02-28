# Clean Architecture Guidelines

## Layer Rules

Domain:
- Pure business logic
- No framework dependency
- No infrastructure dependency

Application:
- Use-case orchestration
- Depends only on Domain

Infrastructure:
- Implements ports
- Depends inward only

## Forbidden

- Domain referencing JPA entities
- Application calling repository implementations directly
- Cross-layer cyclic dependencies