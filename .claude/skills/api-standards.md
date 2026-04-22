# API Standards

- RESTful endpoints
- Use HTTP status codes properly
- Standard error response format

## Error Format

{
"code": "ERROR_CODE",
"message": "Human readable message",
"timestamp": "ISO8601"
}

## Exception Handler Location

- `@RestControllerAdvice` and the standard error response DTO are Web Adapter
  concerns. Place them under `adapter/in/web/exception/` and
  `adapter/in/web/dto/response/` respectively.
- They MUST NOT live in `common/`. See `clean-architecture.md` → "Web Exception
  Handling Location".

## Input Validation Boundary

- Request-body validation belongs at the Web Adapter boundary. The long-term
  standard is Bean Validation (`@Valid` on the controller parameter with
  `@NotBlank`, `@Size`, etc. on the request record).
- Relying on domain constructors to throw `IllegalArgumentException` is a
  transitional state only: acceptable when a Plan explicitly defers Bean
  Validation, not acceptable as the end state.

## Error Mapping Policy

- Domain-specific exceptions (e.g. `PostNotFoundException`) MUST map to
  meaningful HTTP statuses (e.g. 404) with distinct error codes.
- Blanket mapping of `IllegalArgumentException` → 400 is a **transitional**
  policy that collapses multiple causes into one code. It is tolerated while a
  Plan explicitly keeps it, but the long-term direction is a domain-specific
  exception hierarchy with per-exception mappings.
- Reviewers: do not flag the blanket 400 mapping as an issue when the active
  Plan lists it under Non-goals. Do flag it if the Plan does not.

## Pagination

- Explicit page/size or cursor
- Consistent response structure
- The Input Port returns a top-level result DTO (e.g. `PostListResult`) and
  the Web Response DTO maps from it. Response DTOs MUST NOT import a record
  declared inside the UseCase interface. See `clean-architecture.md` → "Port
  Result DTO Layout".