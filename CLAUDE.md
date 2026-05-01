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
- ADR ↔ Plan 번호 정합 규약은 `.claude/skills/plan-lifecycle.md` 참조.

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

### Plan Lifecycle
- `docs/plans/in-progress/` — drafts produced by Plan Mode
- `docs/plans/approved/` — human-approved execution contracts
- `docs/plans/done/` — completed plans for archival
- Status: Draft → Approved → Completed / Cancelled
- **머지 직후 archival은 필수 후속 단계.** in-progress/ 또는 approved/에 머지 완료된 Plan이 남아 있으면 새 Plan 작업을 시작하지 않는다.

Plan 작성 형식(두 계층 구조, Required Reading, Implementation Hints)과 archival 절차(트리거/셸 명령/참조 갱신/Block 규칙)는 `.claude/skills/plan-lifecycle.md` 참조.

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
- "API design" → `.claude/skills/api-standards.md`
- "Persistence behavior" → `.claude/skills/db-standards.md`
- "Layer boundaries / filter exception handling" → `.claude/skills/clean-architecture.md`
- "Plan format / archival" → `.claude/skills/plan-lifecycle.md`

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

---

## 9. Branch & PR Conventions

이 저장소는 작업 단위를 `Plan 구현 / 문서 결정 / 수정 / 정리`로 나누고, 브랜치명과 PR 템플릿도 이 구분을 따른다.

### Branch Naming

기본 형식:

```text
<type>/<slug>
```

허용 type:

- `plan/`: Plan 구현
- `docs/`: ADR/Plan 단독 변경
- `fix/`: Plan과 무관한 버그 수정
- `chore/`: 도구, 의존성, 포맷팅, 저장소 정리

Plan 브랜치는 Plan 번호를 포함한다.

```text
plan/0005-c-observability-logging
```

### PR Templates

기본 PR 템플릿은 Plan 구현 PR용이다.

- `.github/pull_request_template.md`

다른 유형은 상황별 템플릿을 사용한다.

- `.github/PULL_REQUEST_TEMPLATE/docs-plan-adr.md`
- `.github/PULL_REQUEST_TEMPLATE/fix.md`
- `.github/PULL_REQUEST_TEMPLATE/chore.md`

상세 사용법(URL parameter, type 선택 기준)은 `.github/PULL_REQUEST_TEMPLATE/README.md` 참조.

### Squash Merge

이 저장소는 squash merge를 기본으로 한다. PR 본문의 `Squash Commit Message`는 최종 squash commit message의 초안이므로, 머지 후 `git log`만 봐도 변경 의도가 읽히도록 작성한다.
