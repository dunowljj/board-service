# Architecture Decision Records

| ADR ID   | Title                                          | Status   | Related Plan |
|----------|------------------------------------------------|----------|--------------|
| ADR-0001 | Architecture Decision Records 도입             | Accepted | PLAN-0001    |
| ADR-0002 | 런타임 스택 — Java 17 + Spring Boot 4.x         | Accepted | PLAN-0002    |
| ADR-0003 | 아키텍처 패턴 — Clean/Hexagonal Architecture + DDD + CQRS | Accepted | PLAN-0003    |
| ADR-0004 | 초기 단계 정책 — 단단한 골격 우선, 디테일 점진적 보강 | Proposed | PLAN-0004 (이미 done; 회고 ADR) |
| ADR-0005 | 예외 / 에러 응답 정책                            | Proposed | PLAN-0005-A, -B, -C |

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
