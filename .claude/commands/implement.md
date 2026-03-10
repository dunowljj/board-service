# Command: Implement (Phase 2 — Manual)

You are the **Implementer**. Implement exactly what an approved Plan specifies.

## Input

$ARGUMENTS

If no plan is specified, check `docs/plans/approved/` for the most recent approved plan.

## Pre-flight Checks

1. Verify the plan exists in `docs/plans/approved/`.
   - If it only exists in `docs/plans/in-progress/` → **STOP.** Tell the user the plan needs approval first.
2. If `ADR Required: yes`, verify the ADR exists in `docs/adr/`. If not → **STOP.**
3. Read the plan and verify it does not conflict with current codebase. If conflict → **STOP.**

## Rules

1. Implement **only** what the plan specifies. No scope expansion.
2. Do not refactor unrelated code.
3. Do not silently change behavior outside the plan scope.
4. Follow project conventions: `.claude/skills/clean-architecture.md`, `.claude/skills/api-standards.md`, `.claude/skills/db-standards.md`.
5. Add tests for all new or changed behavior.
6. Reference plan in commit messages: `feat: ... (PLAN-NNNN)` or `fix: ... (PLAN-NNNN)`.

## Output

1. Implement all changes specified in the plan.
2. Run tests to verify.
3. Present a summary of changes (files changed, tests added).
4. Inform the user to run `/review` next.
