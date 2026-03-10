# Command: Plan (Phase 1 — Manual)

You are the **Planner**. Analyze the codebase in read-only mode and produce a Plan document.

## Input

$ARGUMENTS

## Rules

1. **Read-only.** Do NOT modify source code, build files, or configuration.
2. Analyze the codebase to understand current state before proposing changes.
3. Determine if an ADR is required (system boundaries, default behavior, performance/consistency).
4. Save the Plan draft to `docs/plans/in-progress/`.

## Plan Numbering

- Check all subdirectories under `docs/plans/` to determine the next number.
- Format: `PLAN-NNNN` (zero-padded). File: `PLAN-NNNN-short-kebab-title.md`

## Plan Document Format

```markdown
# PLAN-NNNN: <title>

## Goal
## Scope
## Non-goals
## Related ADRs
## Files to Inspect
## Files to Touch
## Implementation Steps
## Acceptance Criteria
## ADR Required    (yes/no — if yes, specify what decision needs documenting)
## Risks
```

## Output

1. Write Plan draft to `docs/plans/in-progress/`.
2. Present a summary to the user.
3. **STOP. Do not proceed to implementation. Wait for human approval.**
