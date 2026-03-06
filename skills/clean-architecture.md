# Clean Architecture + Hexagonal Guidelines

## Port & Adapter Rules

Domain:
- Pure business logic
- No framework dependency
- No infrastructure dependency
- Outbound Port (Repository interface) defined here

Application:
- Use-case orchestration
- Depends only on Domain
- Inbound Port (Use Case interface) defined here
- Uses Outbound Port to access external resources

Inbound Adapter (Infrastructure):
- REST Controller, Message Listener
- Calls Inbound Port

Outbound Adapter (Infrastructure):
- JPA Repository implementation, External API client
- Implements Outbound Port

## Forbidden

- Domain referencing JPA entities or Spring annotations
- Application calling Adapter implementations directly
- Adapter bypassing Port (direct domain access without port)
- Cross-layer cyclic dependencies
