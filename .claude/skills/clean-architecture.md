# Clean/Hexagonal Architecture + DDD + CQRS Guidelines

## Architecture Overview

Hexagon (inner) = Application + Domain
Adapters (outer) = Driving / Driven
Ports = boundary between inner and outer, defined in Application
CQRS = Command and Query paths are separated at Service and Port level

## Layer Rules

Domain:
- Pure business model: Entity, Value Object, Aggregate
- No framework dependency
- No infrastructure dependency
- No Port definitions — Domain does NOT define ports

Application:
- Input Port (Use Case interface) defined here
- Output Port (external resource interface) defined here
- Service implements Input Port, uses Output Port
- Depends only on Domain

Driving Adapter (outer):
- REST Controller, CLI, Message Listener
- Calls Input Port

Driven Adapter (outer):
- JPA Repository implementation, External API client
- Implements Output Port

## Port Design

- Output Ports are split by role (Interface Segregation)
  - LoadPostPort, SavePostPort, DeletePostPort (not a single PostRepository)
- UseCase depends only on the ports it needs
- Input Port = Use Case interface
- Output Port = external resource access interface

## Entity Separation

- Domain Entity ≠ JPA Entity
- Mapper in Driven Adapter converts between them
- Domain Entity is pure POJO (no @Entity, no Spring annotations)

## CQRS Rules

- Command (CUD) and Query (R) are separated at Service level
- CommandService implements create/update/delete Use Cases
- QueryService implements read/list Use Cases
- Command and Query may use different Output Ports as complexity grows
- Query path may bypass Domain model for read-optimized projections (when justified by ADR)

## Forbidden

- Domain defining or depending on Port interfaces
- Domain referencing JPA entities or Spring annotations
- Application calling Adapter implementations directly
- Adapter bypassing Port (direct domain access without port)
- Cross-layer cyclic dependencies
- Single large repository interface instead of role-based ports
