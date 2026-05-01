# Plan Lifecycle

Plan의 작성·완료 처리 형식과 규약. CLAUDE.md §4가 게이트(언제 Plan을 쓰는가)를, 이 문서가 형식·운영(어떻게 쓰고 어떻게 정리하는가)을 담당한다.

## Plan 작성 형식 — 두 계층

Plan은 **상층(승인 대상)** 과 **하층(구현 재량)** 으로 구성한다. 상층은 사람·리뷰어가 30초 안에 방향을 판정하는 자리, 하층은 에이전트(Claude·Codex)가 동일하게 실행하도록 만드는 자리다.

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

### `## Required Reading`

Plan을 실행/리뷰하기 전 에이전트(Claude·Codex)가 *반드시 읽어야 할 모든 경로*를 나열한다. 외부 에이전트가 세션 컨텍스트 없이 Plan을 받아도 동일하게 작업할 수 있게 만드는 자기충족 매니페스트다. 포함 대상:

- `## Related ADRs`에 적힌 ADR들 (위에 이미 적었어도 경로로 다시 명시)
- 작업 이해에 필요한 기존 코드 파일 (변경 안 하더라도)
- `CLAUDE.md`, 관련 `.claude/skills/*.md`

### `## Implementation Hints` (optional)

구조 골격(새/변경 파일 경로, 클래스·함수 시그니처, 핵심 invariant, 테스트 케이스 윤곽)을 적는다.

채우는 경우:
- Codex Mode 3 (Codex가 구현 주체)을 쓸 계획
- 새 패턴/구조를 도입하는 Plan, 여러 파일에 걸친 구조 변경
- 다음 도메인이 같은 골격을 따라야 하는 Plan

비워두는 경우:
- 1파일 단순 수정, 의존성 버전 업데이트, 문서 변경

**작성 한계**: Hints는 구조 골격까지. 의사코드 수준으로 가지 않는다 — "마크다운으로 코드 짜기"가 되면 작성 비용이 구현 비용을 넘는다. 하층은 상층에서 *도출*되어야 하며, 상층 Scope를 새로 만들지 않는다.

### ADR ↔ Plan 번호 정합 (ADR-0004 §3)

- ADR-NNNN과 그로부터 파생된 Plan은 같은 번호 NNNN을 공유한다.
- 한 ADR이 여러 Plan으로 펼쳐질 경우 Plan은 `-A`, `-B`, `-C` 접미사로 구분한다.
- ADR 없이 단독 진행되는 Plan은 해당 NNNN의 ADR 슬롯을 비워둘 수 있다 (`docs/adr/README.md`에 사유 기록).
- 회고 ADR이 허용된다 (Proposed → Accepted 정상 흐름).

## Plan Archival — 머지 후 필수 후속 단계

Plan 구현 PR이 squash-merge된 직후, **별개 후속 커밋**으로 archival을 수행한다. 누락이 반복되어 시스템 규칙으로 격상한다.

### 트리거

매 세션 진입 시 에이전트가 `docs/plans/in-progress/` 및 `approved/`에 있는 Plan 중 구현 커밋이 main에 머지된 것이 있는지 점검한다. 발견 시, 다음 작업(새 Plan, Phase 1 Planner 등)에 진입하기 전에 archival을 먼저 처리한다.

### 절차

1. 새 브랜치 `chore/archive-plan-NNNN[-X]` (CLAUDE.md §9 컨벤션).
2. Plan 파일은 `in-progress/` 또는 `approved/` 둘 중 한 곳에만 있다 — 그 위치에서 `done/`으로 `git mv`. 한 셸 명령으로 처리하려면:
   ```bash
   for d in in-progress approved; do
     git mv docs/plans/$d/PLAN-NNNN-*.md docs/plans/done/ 2>/dev/null || :
   done
   ```
   양쪽에 동시에 존재하면 lifecycle 위반 — archival을 중단하고 보고한다.
3. **참조 일괄 갱신** — `grep -rn 'docs/plans/\(in-progress\|approved\)/PLAN-NNNN' docs/`의 모든 결과를 `docs/plans/done/` 경로로 치환. (다른 Plan의 Required Reading, ADR 본문, README 등.) 슬러그 멘션(`PLAN-NNNN`)은 경로 무관이므로 변경 불필요.
4. 커밋 메시지: `chore: archive PLAN-NNNN as completed` (선례: `75f3c05`).

### Block 규칙

`in-progress/` 또는 `approved/`에 머지 완료된 Plan이 남아 있으면 새 Plan 작업을 시작하지 않는다. lifecycle 무결성을 깨뜨리지 않기 위해 archival이 선행되어야 한다.
