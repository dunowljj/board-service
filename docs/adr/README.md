# Architecture Decision Records

| ADR ID   | Title                                          | Status   | Related Plan |
|----------|------------------------------------------------|----------|--------------|
| ADR-0001 | Architecture Decision Records 도입             | Accepted | PLAN-0001    |
| ADR-0002 | 런타임 스택 — Java 17 + Spring Boot 4.x         | Accepted | PLAN-0002    |
| ADR-0003 | 아키텍처 패턴 — Clean/Hexagonal Architecture + DDD + CQRS | Accepted | PLAN-0003    |
| ADR-0004 | 초기 단계 정책 — 단단한 골격 우선, 디테일 점진적 보강 | Proposed | PLAN-0004 (이미 done; 회고 ADR) |
| ADR-0005 | 예외 / 에러 응답 정책                            | Proposed | PLAN-0005-A, -B, -C |
| ADR-0006 | 테스트 전략 — 계층별 책임 분리                   | Proposed | PLAN-0006-A·B·C·D    |
| ADR-0007 | 시간 정책 — `Clock` 주입과 결정적 시간 의식       | Proposed | PLAN-0007            |
| ADR-0008 | 감사 데이터 정책 — JPA Auditing 도입과 도메인 timestamp 제거 | Proposed | PLAN-0008            |
| ADR-0009 | equals/hashCode 코드 컨벤션 — `instanceof` pattern matching 통일 | Proposed | PLAN-0009            |
| ADR-0010 | 로컬 개발 / 테스트용 DB 인프라 — Docker + Testcontainers + PostgreSQL/pgvector | Proposed | PLAN-0010 (예정)     |

Status:
- Proposed
- Accepted
- Superseded

Superseded ADRs remain for traceability.

## ADR ↔ Plan 번호 규약

- ADR-NNNN과 그로부터 파생된 Plan은 같은 번호 NNNN을 공유한다.
- 한 ADR이 여러 Plan으로 펼쳐질 경우 `-A`, `-B`, `-C` 접미사로 구분한다 (예: PLAN-0005-A).
- ADR 없이 단독 진행되는 Plan은 해당 NNNN의 ADR 슬롯을 비워둘 수 있으며, 본 표에 사유를 한 줄 기록한다.
- 회고 ADR이 허용된다 (Proposed → Accepted 정상 흐름).

자세한 규약은 ADR-0004 §3 참조.

## ADR 표준 섹션

각 ADR은 다음 섹션을 갖는다. `*` 표시는 옵셔널.

- `## Status` — Proposed | Accepted | Superseded
- `## Date`
- `## Context` — 결정이 필요해진 배경
- `## Decision` — 채택한 정책 본문
- `## Considered Alternatives` * — 검토했지만 채택하지 않은 다른 방향들과 그 이유
- `## Rejected Suggestions` * — 논의 중 제안되었으나 거부된 안 (에이전트 제안 포함). 거부 사유를 한두 줄로.
- `## Consequences` * — 채택으로 인한 trade-off, 후속 부담, 모니터링 포인트
- `## Open Questions` * — defer 한 항목들

`Rejected Suggestions`는 "왜 이 결정이 *그* 결정인지"의 근거를 남기는 자리다.
거부된 안의 *논거 자체*가 회고·면접·후임자에게 가장 큰 가치를 가진다.
