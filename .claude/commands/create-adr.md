# Command: Create ADR

Use when a decision boundary is crossed — system boundaries, invariants, default behavior, or performance/consistency trade-offs.

## Input

$ARGUMENTS

## Rules

1. Check existing ADRs in `docs/adr/` to determine the next number.
2. Use format: `NNNN-kebab-case-title.md` (e.g., `0004-use-redis-for-caching.md`)
3. Add the new ADR to `docs/adr/README.md` index.
4. Initial status must be `Proposed`.

## ADR Template

```markdown
# NNNN. <Title>

## Status
Proposed

## Context
Why this decision is needed. What forces are at play.

## Decision
The chosen approach and rationale.

## Alternatives Considered
- Option A: ...
- Option B: ...

## Consequences
### Positive
- ...
### Negative
- ...

## Related Plans
- Link to relevant plans in `docs/plans/`

## Related ADRs
- Link to related ADRs if applicable
```
