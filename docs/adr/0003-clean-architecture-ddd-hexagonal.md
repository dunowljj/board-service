# ADR-0003: 아키텍처 패턴 — Clean/Hexagonal Architecture + DDD + CQRS

## Status
Accepted

## Date
2026-03-10 (revised)

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

### 왜 Hexagonal Architecture인가

Hexagonal Architecture(Ports & Adapters)는 애플리케이션을 중심에 두고,
외부 세계와의 모든 상호작용을 Port와 Adapter로 분리한다.

핵심은 **Port(인터페이스)를 Application이 정의하고, Adapter(구현체)가 외부에서 꽂히는 구조**이다.
Domain은 순수 비즈니스 모델만 포함하며, 어떤 Port도 정의하지 않는다.

```
                    [ Driving Adapter ]
                     (Web Controller)
                           │
                           ▼
                    ┌─── Port (In) ───┐
                    │                 │
                    │   Application   │
                    │   (Use Cases)   │
                    │      ↓ 의존      │
                    │     Domain      │
                    │  (순수 모델)     │
                    │                 │
                    └─── Port (Out) ──┘
                           │
                           ▼
                   [ Driven Adapter ]
                (JPA Repository 구현체)
```

- **Input Port (Driving Port):** 외부가 애플리케이션을 호출하는 인터페이스 (Use Case 인터페이스)
- **Output Port (Driven Port):** 애플리케이션이 외부 자원에 접근하는 인터페이스
- **Driving Adapter:** Input Port를 호출하는 구현체 (REST Controller, CLI, Message Listener 등)
- **Driven Adapter:** Output Port를 구현하는 구현체 (JPA Repository, External API Client 등)

이 구조가 앞서 경험한 문제를 해결하는 방식:

| 문제 | 해결 방식 |
|------|-----------|
| BooleanBuilder가 Service로 누출 | QueryDSL 코드는 Driven Adapter 안에서만 존재 |
| 비즈니스 로직과 쿼리 로직 혼재 | Domain/Application은 Port만 알고, "어떻게 조회하는가"를 모른다 |
| 복잡 조회와 도메인 규칙이 같은 파일 | CQRS 도입 시 읽기 전용 Port/Adapter를 별도로 분리 가능 |
| 테스트가 DB에 강하게 결합 | Port 기반으로 도메인 로직을 프레임워크 없이 테스트 가능 |

## Decision

Clean Architecture의 의존성 규칙(Dependency Rule)과 Hexagonal Architecture의 Ports & Adapters 패턴을 기본 아키텍처로 채택하고, DDD의 전술적 패턴(Entity, Value Object, Aggregate)을 Domain 모델링에 사용하며, CQRS 패턴으로 Command와 Query 경로를 분리한다.

### Hexagonal 구조

```
[ Driving Adapters ]           [ Driven Adapters ]
  REST Controller                JPA Repository Impl
  Message Listener               External API Client
        │                               ▲
        ▼                               │
   Input Port                     Output Port
  (Use Case IF)               (Repository IF 등)
        │                               ▲
        ▼                               │
  ┌──────────────────────────────────────────┐
  │              Application                 │
  │  ┌────────────────────────────────────┐  │
  │  │  Port (In)     │    Port (Out)     │  │
  │  │  Use Case IF   │  Load/Save Port   │  │
  │  ├────────────────────────────────────┤  │
  │  │           Service                  │  │
  │  │     (Use Case 구현)                │  │
  │  └────────────────────────────────────┘  │
  │                                          │
  │  ┌────────────────────────────────────┐  │
  │  │            Domain                  │  │
  │  │   Entity, Value Object, Aggregate  │  │
  │  │   (순수 비즈니스 모델, Port 없음)     │  │
  │  └────────────────────────────────────┘  │
  └──────────────────────────────────────────┘
```

### 레이어 책임

**Domain**
- 순수 비즈니스 모델: Entity, Value Object, Aggregate
- 프레임워크 의존 없음, Port 없음
- 비즈니스 규칙과 불변식만 표현

**Application**
- Input Port (Use Case 인터페이스) 정의
- Output Port (외부 자원 접근 인터페이스) 정의
- Service (Use Case 구현체): Input Port를 구현하고, Output Port를 사용
- Domain에만 의존

**Adapter (Driving)**
- REST Controller, CLI, Message Listener
- Input Port를 호출

**Adapter (Driven)**
- JPA Repository 구현, External API Client, Cache, Search Engine
- Output Port를 구현

### Port 설계 원칙

Output Port는 역할별로 분리한다 (Interface Segregation).

```java
// ✅ 역할별 분리
public interface LoadPostPort {
    Post findById(Long id);
    List<Post> findAll(int page, int size);
}

public interface SavePostPort {
    Post save(Post post);
}

public interface DeletePostPort {
    void delete(Long id);
}

// ❌ 하나의 거대 인터페이스
public interface PostRepository {
    Post findById(Long id);
    List<Post> findAll(...);
    Post save(Post post);
    void delete(Long id);
}
```

UseCase는 필요한 Output Port만 의존한다:
- `GetPostUseCase` → `LoadPostPort`만 의존
- `CreatePostUseCase` → `SavePostPort`만 의존

### DDD 적용 범위

| 개념 | 적용 여부 |
|------|-----------|
| Entity / Value Object | 적용 |
| Aggregate | 적용 |
| Domain Event | 필요 시 도입 (별도 ADR) |
| Bounded Context | 현재 단일 컨텍스트, 확장 시 ADR |
| CQRS | 적용 — Command/Query Service 및 Port 분리 |
| Event Sourcing | 현재 미적용, 필요 시 ADR |

### 패키지 구조

```
com.dunowljj.board
├── domain
│   ├── post/           # Post (Aggregate Root), PostContent (VO)
│   └── comment/        # Comment (Entity)
│
├── application
│   ├── port
│   │   ├── in/         # Input Port — CreatePostUseCase, GetPostUseCase 등
│   │   └── out/        # Output Port — LoadPostPort, SavePostPort 등
│   └── service/        # UseCase 구현 — CommandService (CUD), QueryService (R) — CQRS 분리
│
├── adapter
│   ├── in
│   │   └── web/        # Driving Adapter — Controller, DTO
│   └── out
│       └── persistence/ # Driven Adapter — JpaEntity, JpaRepository, PersistenceAdapter, Mapper
│
└── common/             # 공통 예외, 설정
```

### 불변식 (Invariants)

1. Domain은 순수 비즈니스 모델만 포함한다. Port를 정의하지 않는다.
2. 모든 Port(Input/Output)는 Application 레이어에 정의한다.
3. Input Port는 Use Case 인터페이스이다. Driving Adapter가 이를 호출한다.
4. Output Port는 외부 자원 접근 인터페이스이다. Driven Adapter가 이를 구현한다.
5. Service는 Input Port를 구현하고, Output Port를 사용한다.
6. Adapter는 Port를 통해서만 Application/Domain과 상호작용한다.
7. Domain 모델은 순수 Java(POJO)로 작성하며, Spring/JPA 애노테이션을 포함하지 않는다.
8. JPA Entity와 Domain Entity는 분리한다. 변환은 Driven Adapter 내 Mapper가 담당한다.
9. Output Port는 역할별로 분리한다 (Interface Segregation Principle).
10. QueryDSL 등 복잡 조회 구현은 Driven Adapter 안에서만 작성한다.

## Consequences

**긍정적 영향**
- JPA/QueryDSL 복잡성이 Driven Adapter에 격리되어 Service가 쿼리 조립소가 되지 않는다.
- Domain이 완전히 순수하여 프레임워크 없이 단위 테스트할 수 있다.
- 인프라 교체(JPA → 다른 ORM) 시 Driven Adapter만 교체하면 된다.
- Output Port가 역할별로 분리되어 UseCase가 최소 의존만 갖는다.
- CQRS 패턴으로 Command/Query 경로가 분리되어, 조회 최적화와 명령 처리를 독립적으로 진화시킬 수 있다.
- Port가 명시적이므로 시스템 경계가 코드에서 바로 보인다.
- Phase 5에서 SearchPort, NotificationPort 등 새 Output Port를 추가해도 기존 구조에 영향 없다.

**부정적 영향 / 트레이드오프**
- 초기 보일러플레이트가 전통 계층 구조보다 많다 (Port 인터페이스 + Adapter 구현 + Mapper).
- Output Port를 역할별로 분리하면 인터페이스 수가 늘어난다.
- 도메인 규칙이 단순한 초기 구간에서는 과설계처럼 보일 수 있다.

## Related
- PLAN-0003
- ADR-0002 (런타임 스택)
