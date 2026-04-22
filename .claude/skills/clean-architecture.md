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

### Port Result DTO Layout (strict)

Goal: prevent Driving Adapters from becoming coupled to Port-internal types,
and prevent Input and Output port contracts from leaking into each other.

- **Input-side Command records** (e.g. `CreatePostCommand`) — nested inside the
  UseCase interface is allowed. Direction is Adapter → Application only, so the
  coupling is unidirectional and harmless.
- **Input-side Result records** (e.g. `PostListResult`) — MUST be top-level types,
  placed under `application/port/in/result/`. A Web/CLI/Message adapter that
  consumes a UseCase result must never import a type declared *inside* an Input
  Port interface.
- **Output-side result DTOs** (e.g. `PostPage`) — top-level, placed under
  `application/port/out/dto/`. Input result and Output result types are
  physically separate packages so the two port surfaces cannot bleed into each
  other.
- Mapping between Output result → Domain → Input result happens inside the
  Application Service.

This layering adds one mapping hop. That cost is intentional: long-lived
projects need the boundary more than they need the shortcut.

## Entity Separation

- Domain Entity ≠ JPA Entity
- Mapper in Driven Adapter converts between them
- Domain Entity is pure POJO (no @Entity, no Spring annotations)

## Domain Invariants

- Aggregate Root exposes two factory-style paths:
  - `create(...)` — construct a brand-new instance for fresh business actions
  - `reconstitute(...)` — rebuild an instance from a persisted snapshot
- Both paths MUST enforce the same business invariants on fields that belong to
  the domain (e.g. author not blank, content rules).
  - Reason: `reconstitute` is not a backdoor. If the DB somehow violates an
    invariant, the code should fail loudly at the boundary rather than let a
    malformed aggregate flow into the system.
- `reconstitute` additionally rejects nulls that the persistence layer is
  supposed to guarantee (id, createdAt, updatedAt, etc.).
- Shared validation logic is extracted into a private static helper
  (e.g. `validateAuthor`) so `create` and `reconstitute` cannot drift.

## Equality Policy

- **Value Objects** — MUST override `equals` and `hashCode` based on all
  components. They are value-equal by definition.
- **Entities / Aggregate Roots** — equality policy is a deliberate design
  decision (usually identifier-based). Until a project-level decision is made,
  absence of `equals`/`hashCode` on entities is acceptable and reviewers MUST
  NOT flag it as an issue on its own.

## Web Exception Handling Location

- `@RestControllerAdvice` handlers and the standard API error response DTO are
  **Driving Adapter (web)** concerns. They belong under
  `adapter/in/web/exception/` and `adapter/in/web/dto/response/`.
- They MUST NOT live under a `common/` package. `common/` is for cross-layer
  utilities that do not belong to any specific adapter. An HTTP error handler
  does belong to a specific adapter (HTTP).
- Domain exceptions (e.g. `PostNotFoundException`) belong under
  `domain/<aggregate>/`. Temporary placement in `common/exception/` is
  tolerated only while explicitly called out in a Plan.

## CQRS Rules

- Command (CUD) and Query (R) are separated at Service level
- CommandService implements create/update/delete Use Cases
- QueryService implements read/list Use Cases
- Command and Query may use different Output Ports as complexity grows
- Query path may bypass Domain model for read-optimized projections (when justified by ADR)

### CQRS Coupling Boundary

- CommandService MAY depend on a `Load*Port` when it needs pre-condition checks
  (existence, authorization state, conflict detection). This is pragmatic and
  does not violate CQRS at current complexity.
- If the read needs of Command and Query start to diverge (different
  projections, different consistency models, different data sources), split
  them via an ADR — do not quietly introduce a parallel read port.
- For existence-only pre-conditions, CommandService MUST use the lightweight
  `existsById`-style API rather than loading a full aggregate. Loading the full
  aggregate just to throw it away is a review-level issue.

## Forbidden

- Domain defining or depending on Port interfaces
- Domain referencing JPA entities or Spring annotations
- Application calling Adapter implementations directly
- Adapter bypassing Port (direct domain access without port)
- Cross-layer cyclic dependencies
- Single large repository interface instead of role-based ports
- Driving Adapter importing a type declared *inside* an Input Port interface
  (Result/nested-record leakage)
- Web exception handler or error response DTO placed under `common/`
- `create` and `reconstitute` enforcing different invariants on the same
  aggregate
- CommandService loading a full aggregate solely for existence checks
