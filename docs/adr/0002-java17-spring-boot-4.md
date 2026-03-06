# ADR-0002: 런타임 스택 — Java 17 + Spring Boot 4.x

## Status
Accepted

## Date
2026-03-01

## Context

게시판 서비스의 초기 구현 스택을 확정해야 한다.
선택 시점에서 고려한 주요 축:

| 항목 | 고려 대상 |
|------|-----------|
| 언어/런타임 | Java 17 LTS, Java 21 LTS |
| 프레임워크 | Spring Boot 3.x, Spring Boot 4.x, Quarkus, Micronaut |
| 빌드 | Gradle, Maven |

**Java 17 선택 배경**
- LTS 릴리스로 기업 환경 호환성이 검증됨
- Records, Sealed Classes, Pattern Matching 등 DDD 표현력을 높이는 언어 기능 제공
- Spring Boot 4.x가 요구하는 최소 버전

**Spring Boot 4.x 선택 배경**
- 팀의 기존 숙련도와 생태계(Spring Data, Spring Security 등)를 활용 가능
- Convention-over-configuration으로 초기 생산성 확보
- 초기 단계이므로 마이그레이션 비용 없이 최신 버전(4.x)으로 바로 시작

## Decision

- **Java 17** (LTS)을 기본 런타임으로 채택한다.
- **Spring Boot 4.x** (4.0.2)를 사용한다.
- 빌드 도구는 **Gradle**을 사용한다.

## Consequences

**긍정적 영향**
- LTS 보안 패치 지원으로 장기 운영 안정성 확보
- Spring 생태계 통합(Data JPA, Security, Actuator 등) 즉시 활용 가능
- Records/Sealed Classes로 도메인 모델 표현력 향상
- Spring Boot 4.x의 최신 기능과 개선사항을 바로 활용 가능

**부정적 영향 / 트레이드오프**
- JVM 콜드 스타트 시간은 Quarkus/Micronaut 대비 불리하다.
  (단, 게시판 서비스 초기 규모에서 임계치가 아님을 전제)
- Java 21 Virtual Threads(Loom) 기능을 즉시 활용하지 못한다.
  필요 시 Java 21 업그레이드 ADR을 별도 작성한다.
- Spring Boot 4.x는 비교적 최신이므로 커뮤니티 자료가 3.x 대비 적을 수 있다.

## Related
- PLAN-0002
- ADR-0003 (아키텍처 패턴)
