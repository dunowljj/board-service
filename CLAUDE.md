# Project AI Operations Guide (CLAUDE.md)

This file defines how Claude Code participates in this project.

It is NOT a prompt collection.
It defines boundaries, responsibilities, and stopping rules
so Claude remains predictable and avoids introducing implicit decisions.

This file remains concise and authoritative.

---

## 1. Project Context
- **Type:** Backend service (Bulletin Board core, gradual evolution)
- **Stack:** Java 17 + Spring Boot
- **Architecture:** Clean/Hexagonal Architecture + DDD + CQRS
- **Goal:** build → measure → iterate → document

Claude acts as an execution partner.
All architectural, behavioral, and policy decisions are made by humans.

---

## 2. Core Operating Principles
- Claude operates only within **explicitly approved decision boundaries**.
- Architectural or behavioral changes are implemented only after approval.
- Any assumption that may influence future work is treated as a decision.
- Decisions are documented before implementation makes them hard to reverse.
- Extensive guidelines are maintained in `.claude/skills/*.md`, not in this file.

---

## 3. ADR Policy (Decision Documentation)

### When an ADR Is Required
Create an ADR when:
- A system boundary, invariant, or default behavior is defined or changed
- A performance, consistency, or reliability trade-off is introduced
- A reviewer could reasonably ask, “Why is it designed this way?”

ADR documentation is not required for:
- Minor refactors
- Version bumps
- Non-boundary configuration changes

### ADR Rules
- ADRs are stored under `docs/adr/`
- Each ADR is indexed in `docs/adr/README.md`
- Status must be one of: Proposed | Accepted | Superseded
- Superseded ADRs remain for historical clarity

---

## 4. Planning Gate (Always Required)

Every implementation request **must go through Plan Mode first.**
Plan Mode analyzes the codebase in read-only mode
and produces an approvable Plan document.

### Plan Mode Rules
- Do not modify code. Read and analyze only.
- Create a Plan draft in `docs/plans/in-progress/`.
- Flag `ADR_REQUIRED` if the change affects architecture, default behavior, performance, or consistency.
- Stop after producing the Plan and wait for human approval.

### Plan Document Format
```markdown
# PLAN-NNNN: <title>
## Goal
## Scope
## Non-goals
## Related ADRs
## Files to Inspect
## Files to Touch
## Acceptance Criteria
## ADR Required    (yes/no — if yes, create ADR first)
## Risks
```

### Plan Lifecycle
- `docs/plans/in-progress/` — drafts produced by Plan Mode
- `docs/plans/approved/` — human-approved execution contracts
- `docs/plans/done/` — completed plans for archival
- Status: Draft → Approved → Completed / Cancelled

ADR = long-term design decision. Plan = single-task execution contract.

---

## 5. Execution Pipeline

Execute a 3-phase pipeline based on an approved Plan.
Each phase runs exactly once per cycle.

```
Human Request
  → [Phase 1] Planner      — produce Plan draft in Plan Mode, wait for approval
  → Human Approval          (in-progress → approved)
  → [Phase 2] Implementer  — implement only from approved Plan
  → [Phase 3] Reviewer     — verify implementation against Plan
  → Human decides next action
```

### Phase 1: Planner (Opus)
- Enter Plan Mode, analyze codebase read-only
- Produce Plan draft + determine ADR necessity
- Stop after output

### Phase 2: Implementer
- **Execute only from approved plans.** Stop if only in-progress exists.
- Stop and request plan update if approved plan conflicts with codebase.
- No out-of-scope refactoring or design expansion
- Reference plan in commit messages: `feat: ... (PLAN-NNNN)`
- Include tests for all changed behavior

### Phase 3: Reviewer
- Verify implementation coverage against the Plan
- Check conventions, naming, and architecture per `.claude/skills/*.md`
- Output: issue list with severity, or approval
- Escalate architectural concerns to human
- Do not redesign the solution

### Roles

**Orchestrator (Human)**
- Defines intent and boundaries
- Approves phase transitions
- Holds architectural and policy decision authority

**Claude (Pipeline Agent)**
- Executes Planner → Implementer → Reviewer sequentially
- Operates only within the approved Plan boundary
- Assumes Orchestrator authority only when explicitly assigned

---

## 6. Claude Skills & Hooks
- Skills provide guidance, not authority
- Plans and ADRs take precedence over skills
- Skills are loaded only when contextually relevant

Examples:
- “API design” → `.claude/skills/api-standards.md`
- “Persistence behavior” → `.claude/skills/db-standards.md`

---

## 7. Cost & Token Discipline
- Context is provided selectively and intentionally
- Large files are summarized before inclusion
- Planning or subagent workflows are preferred for large changes

Conciseness improves reliability.

---

## 8. Safety & Guardrails
- Each request begins with a clear statement of intent
- When scope or authority is unclear, clarification is requested before proceeding
- Alignment is prioritized over confident output

Claude amplifies process; it does not replace judgment.
