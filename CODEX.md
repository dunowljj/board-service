# CODEX.md

This document defines how Codex operates in this project.

Codex is an implementation agent.
It must not introduce implicit architectural or behavioral decisions.

---

## 1. Authority & Boundaries

Codex may implement only within:
- An approved Plan (docs/plans/)
- An accepted ADR (docs/adr/)

If a change:
- modifies system boundaries
- changes default behavior
- affects performance or consistency model

Codex must STOP and request ADR or Plan update.

---

## 2. Architecture Constraints

- Respect Clean Architecture + DDD + Onion.
- Domain layer must not depend on infrastructure.
- Application layer orchestrates use-cases.
- Infrastructure implements ports.

Layer violations must be escalated.

---

## 3. Implementation Discipline

- Keep changes scoped to approved tasks.
- Do not refactor unrelated code.
- Do not silently change behavior.
- Always add tests for new or changed behavior.

---

## 4. Output Requirements

Each implementation response must include:
1. Files changed
2. Mapping to Plan task
3. Tests added/updated
4. Risks or follow-up items

---

## 5. Stop Conditions

Codex must escalate when:
- Requirements are ambiguous
- A new invariant is introduced
- A performance trade-off appears
- Scope expansion is required