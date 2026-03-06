# ADR-0003: 아키텍처 패턴 — Clean Architecture + DDD + Hexagonal

## Status
Accepted

## Date
2026-03-02

## Context

### 경험한 문제: 전통 계층 구조 + JPA의 붕괴 패턴

전통적인 3-레이어(Controller → Service → Repository)는 단순 CRUD에서는 잘 동작한다.
그러나 조회 요건이 복잡해지는 순간 두 가지 붕괴 패턴이 반복적으로 나타난다.

**패턴 1. Repository 메서드 폭발**

요건이 늘수록 Spring Data 메서드 이름이 길어지고,
감당이 안 되면 QueryDSL BooleanBuilder가 Repository 밖으로 새어 나온다.

```java
// 조합 조건이 5개만 넘어도 이 구조는 무너진다
findByTitleContainingAndStatusAndCategoryAndCreatedAtBetween(...)
```

**패턴 2. Service가 쿼리 조립소가 된다**

비즈니스 규칙과 조회 최적화 관심사가 같은 메서드 안에 공존한다.
어디까지가 도메인 규칙이고 어디서부터 인프라 관심사인지 경계가 사라진다.

```java
public List<PostDto> searchPosts(SearchCondition cond) {
    BooleanBuilder builder = new BooleanBuilder();       // 인프라 관심사
    if (cond.getKeyword() != null) builder.and(...);
    if (cond.getStatus() != null) builder.and(...);
    // ... 도메인 규칙은 어디에 있는가?
}
```

**근본 원인**

전통 계층 구조는 레이어를 나누지만 **의존성 방향을 강제하지 않는다.**
Service는 JPA 구현체를 직접 알고, 조회 최적화 코드가 비즈니스 로직과 같은 파일에 존재할 수 있다.
레이어가 있어도 인프라 세부 구현이 도메인으로 스며드는 것을 구조적으로 막지 못한다.

---

### 왜 Clean Architecture + DDD + Hexagonal인가

핵심은 **Port(인터페이스)를 도메인이 정의하고, Adapter(구현체)가 외부에서 꽂히는 구조**이다.

Hexagonal Architecture(Ports & Adapters)는 애플리케이션을 중심에 두고,
외부 세계와의 모든 상호작용을 Port와 Adapter로 분리한다.

```
                    [ Inbound Adapter ]
                     (Web Controller)
                           │
                           ▼
                    ┌─── Port (In) ───┐
                    │                 │
                    │   Application   │
                    │   (Use Cases)   │
                    │                 │
                    │     Domain      │
                    │  (핵심 규칙)     │
                    │                 │
                    └─── Port (Out) ──┘
                           │
                           ▼
                   [ Outbound Adapter ]
                (JPA Repository 구현체)
```

- **Port (Inbound):** 외부가 애플리케이션을 호출하는 인터페이스 (Use Case 인터페이스)
- **Port (Outbound):** 애플리케이션이 외부 자원에 접근하는 인터페이스 (Repository 인터페이스)
- **Inbound Adapter:** Port (In)을 호출하는 구현체 (REST Controller, Message Listener 등)
- **Outbound Adapter:** Port (Out)을 구현하는 구현체 (JPA Repository, External API Client 등)

이 구조가 앞서 경험한 문제를 해결하는 방식:

| 문제 | 해결 방식 |
|------|-----------|
| BooleanBuilder가 Service로 누출 | QueryDSL 코드는 Outbound Adapter 안에서만 존재 |
| 비즈니스 로직과 쿼리 로직 혼재 | Domain/Application은 Port만 알고, "어떻게 조회하는가"를 모른다 |
| 복잡 조회와 도메인 규칙이 같은 파일 | CQRS 도입 시 읽기 전용 Port/Adapter를 별도로 분리 가능 |
| 테스트가 DB에 강하게 결합 | Port 기반으로 도메인 로직을 프레임워크 없이 테스트 가능 |

DDD는 조회 복잡성보다 **쓰기 경로(도메인 규칙)**에 집중한다.
Hexagonal은 Port/Adapter를 통해 인프라가 도메인으로 역류하는 것을 구조적으로 차단한다.

## Decision

다음 세 패턴을 결합한 아키텍처를 채택한다.

### 구조 (Ports & Adapters)

```
[ Inbound Adapters ]        [ Outbound Adapters ]
  REST Controller              JPA Repository Impl
  Message Listener             External API Client
        │                             ▲
        ▼                             │
   Port (In)                     Port (Out)
        │                             ▲
        ▼                             │
  ┌──────────────────────────────────────┐
  │            Application               │
  │         (Use Case 구현)              │
  │                                      │
  │  ┌──────────────────────────────┐    │
  │  │          Domain              │    │
  │  │    Entity, Value Object      │    │
  │  │    Aggregate, Domain Event   │    │
  │  └──────────────────────────────┘    │
  └──────────────────────────────────────┘
```

- **Domain 레이어**는 어떤 외부 프레임워크에도 의존하지 않는다.
- **Application 레이어**는 Domain에만 의존하고, Inbound Port를 구현하며 Outbound Port를 사용한다.
- **Adapter 레이어**는 Port를 통해서만 Application/Domain과 상호작용한다.

### DDD 적용 범위

| 개념 | 적용 여부 |
|------|-----------|
| Entity / Value Object | 적용 |
| Aggregate / Repository (Outbound Port) | 적용 |
| Domain Event | 필요 시 도입 (별도 ADR) |
| Bounded Context | 현재 단일 컨텍스트, 확장 시 ADR |
| CQRS / Event Sourcing | 현재 미적용, 필요 시 ADR |

### 불변식 (Invariants)

- Repository 인터페이스는 **Outbound Port**로서 Domain 레이어에 위치한다.
- Spring Data JPA 등 구현체는 **Outbound Adapter**로서 Infrastructure에만 위치한다.
- Use Case 인터페이스는 **Inbound Port**로서 Application 레이어에 위치한다.
- REST Controller 등은 **Inbound Adapter**로서 Infrastructure에 위치한다.
- 도메인 모델은 순수 Java(POJO)로 작성하며, Spring 애노테이션을 포함하지 않는다.
- QueryDSL 등 복잡 조회 구현은 Outbound Adapter 안에서만 작성한다.

## Consequences

**긍정적 영향**
- JPA/QueryDSL 복잡성이 Outbound Adapter에 격리되어 Service가 쿼리 조립소가 되지 않는다.
- 도메인 로직을 프레임워크 없이 단위 테스트할 수 있다.
- 인프라 교체(JPA → 다른 ORM) 시 Outbound Adapter만 교체하면 된다.
- 조회가 복잡해질 때 별도 Inbound/Outbound Port로 CQRS 도입 경로가 자연스럽다.
- Port가 명시적이므로 시스템 경계가 코드에서 바로 보인다.

**부정적 영향 / 트레이드오프**
- 초기 보일러플레이트가 전통 계층 구조보다 많다 (Port 인터페이스 + Adapter 구현).
- 팀 전체가 Port/Adapter 규칙을 숙지해야 한다.
- 도메인 규칙이 단순한 초기 구간에서는 과설계처럼 보일 수 있다.

## Related
- PLAN-0003
- ADR-0002 (런타임 스택)
- CLAUDE.md §1 Project Context
