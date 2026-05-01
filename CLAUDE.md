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
- **ADR ↔ Plan 번호 정합 규약** (ADR-0004 §3):
  - ADR-NNNN과 그로부터 파생된 Plan은 같은 번호 NNNN을 공유한다.
  - 한 ADR이 여러 Plan으로 펼쳐질 경우 Plan은 `-A`, `-B`, `-C` 접미사로 구분한다.
  - ADR 없이 단독 진행되는 Plan은 해당 NNNN의 ADR 슬롯을 비워둘 수 있다 (README에 사유 기록).
  - 회고 ADR이 허용된다 (Proposed → Accepted 정상 흐름).

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

Plan은 **상층(승인 대상)** 과 **하층(구현 재량)** 두 블록으로 구성한다. 상층은 사람·리뷰어가 30초 안에 방향을 판정하는 자리, 하층은 에이전트(Claude·Codex)가 동일하게 실행하도록 만드는 자리다.

```markdown
# PLAN-NNNN: <title>

<!-- 상층: 승인 게이트 — 방향/범위/완료 기준 -->
## Goal
## Scope
## Non-goals
## Related ADRs
## Acceptance Criteria
## ADR Required    (yes/no — if yes, create ADR first)
## Risks

<!-- 하층: 실행 재량 — 코드베이스 충돌 시 갱신 가능 -->
## Required Reading
## Files to Touch
## Implementation Hints   (optional)
```

**`## Required Reading`** — Plan을 실행/리뷰하기 전 에이전트(Claude·Codex 등)가 *반드시 읽어야 할 모든 경로*를 나열한다. 외부 에이전트(예: 다른 세션의 Codex)가 세션 컨텍스트 없이 Plan을 받아도 동일하게 작업할 수 있게 만드는 자기충족 매니페스트다. 포함 대상:
- `## Related ADRs`에 적힌 ADR들 (위에 이미 적었어도 경로로 다시 명시)
- 작업 이해에 필요한 기존 코드 파일 (변경 안 하더라도)
- `CLAUDE.md`, 관련 `.claude/skills/*.md`

**`## Implementation Hints` (optional)** — 구조 골격(새/변경 파일 경로, 클래스·함수 시그니처, 핵심 invariant, 테스트 케이스 윤곽)을 적는다. 다음 경우에 채운다:
- Codex Mode 3 (Codex가 구현 주체)을 쓸 계획
- 새 패턴/구조를 도입하는 Plan, 여러 파일에 걸친 구조 변경
- 다음 도메인이 같은 골격을 따라야 하는 Plan

다음 경우엔 비워둔다:
- 1파일 단순 수정, 의존성 버전 업데이트, 문서 변경

**작성 한계**: Hints는 구조 골격까지. 의사코드 수준으로 가지 않는다 — "마크다운으로 코드 짜기"가 되면 작성 비용이 구현 비용을 넘는다. 하층은 상층에서 *도출*되어야 하며, 상층 Scope를 새로 만들지 않는다.

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
