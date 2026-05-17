# ADR-0008: 감사 데이터 정책 — JPA Auditing 도입과 도메인 timestamp 제거

## Status
Proposed

## Date
2026-05-17

## Context

PLAN-0007 (Clock 주입 정책 적용) 머지 직후, ADR-0007 §6 가 명시한 다음 질문이 결정되어야 할 시점에 도달했다.

> **§6 Open Questions — 감사용 timestamp 의 도메인 이전.** `createdAt`/`updatedAt` 을 Post 도메인 클래스에서 빼는가 — ADR-0008 (Auditing) 슬롯의 핵심 질문. 본 ADR (0007) 은 *현재 위치 유지* (도메인 안). Auditing 도입 시 재검토 — *감사용으로 분류되면* Entity-level 로 이전, *비즈니스 의미가 있다고 판정되면* 본 ADR 의 값 전달 패턴으로 유지.

현재 `Post` 도메인 모델은 다음 두 가지를 *우연히* 들고 있다.

1. **`createdAt` / `updatedAt`** — 감사 메타데이터. 비즈니스 invariant 가 아니라 *언제 만들어졌나/수정됐나* 의 운영 정보. UI 표시·정렬·캐시 무효화 등 *외부 관찰자가 사용하는 사실* 일 뿐 도메인 행동의 *입력* 이 아니다.
2. **PLAN-0007 에서 도입한 `LocalDateTime now` 인자** — 위 timestamp 를 채우기 위해 도메인이 외부에서 받는 값. ADR-0007 §3 의 값 전달 패턴.

ADR-0007 의 정책은 *비즈니스 의미가 있는 timestamp 의 결정성* 을 보장하기 위한 것이지만, **현재 그 정책의 *적용 대상* 은 오로지 `createdAt`/`updatedAt` 두 audit 필드뿐**이다. 비즈니스 의미가 있는 timestamp (예: `publishedAt`, `editedAt`, `expiredAt`) 는 *현 도메인에 없다*. 결과 — 도메인 시그니처에 `LocalDateTime` 인자를 끼우는 *경로 의식* 이 *audit 채움이라는 비-도메인 책임* 을 위해서만 존재하는 자리.

또 Auditing 의 자연스러운 형태는 *Entity-level listener* 가 timestamp 를 자동으로 채우는 것 — `@CreatedDate` / `@LastModifiedDate`. 이 책임이 *도메인 외부* (영속 어댑터의 listener) 에 있는 게 본래 audit 의 자리이며, 도메인 invariant 가 아니다. 그러면 도메인이 timestamp 를 *받는* 이유 자체가 사라진다.

이 영역은 *지금* 정책이 정리되지 않으면 다음 부담이 누적된다.

- **도메인 모델의 거짓 invariant** — PLAN-0007 이 신설한 4 가지 도메인 invariant (now non-null × 2, 역행 금지, 경계 허용) 는 *audit timestamp* 의 의식이며 *도메인 규칙* 이 아니다. 도메인 테스트가 *audit 책임* 을 검증하는 잘못된 자리에 위치한다.
- **진실원 다중화 위험** — `Clock` 빈은 1 개지만 *시간이 도메인까지 흘러야 한다* 는 경로 의식이 박혀, 미래 Auditing 도입 시 *도메인 인자 timestamp* 와 *listener 가 채우는 timestamp* 가 동시에 존재해 *어느 쪽이 진실인가* 의 질문 발생.
- **YAGNI 위반의 잔재** — 도메인의 시간 인자가 *현 비즈니스 의미 0 개* 인 자리. 미래 비즈니스 timestamp (예: `publishedAt`) 도입 시점에 *그 메서드 한정* 으로 의식을 가져가는 게 정확.

ADR-0007 의 결정 자체는 *패턴* 으로 유효하지만, *현 적용 대상* 이 사라지면 dormant 상태가 정확하다.

## Decision

`createdAt` / `updatedAt` 은 **Entity-level audit metadata 로 완전 이전한다**. 도메인 (`Post`) 은 *이 두 필드를 모른다*. `Clock` 빈은 *시스템의 단일 시간 진실원* 으로 유지되며, JPA Auditing 이 `DateTimeProvider` 를 통해 이 빈을 사용한다. 본 ADR 은 정책 윤곽만 정의하고 구현은 PLAN-0008 에서 수행한다.

### 1. JPA Auditing 도입

- Application 컨텍스트에 `@EnableJpaAuditing(dateTimeProviderRef = "auditDateTimeProvider")` 활성화.
- 신규 `DateTimeProvider` 빈 (`auditDateTimeProvider`) 이 *ADR-0007 의 `Clock` 빈* 을 사용해 현재 시각 제공.
  ```java
  @Bean
  public DateTimeProvider auditDateTimeProvider(Clock clock) {
      return () -> Optional.of(LocalDateTime.now(clock));
  }
  ```
- **`Clock` 빈은 ADR-0007 §1 의 결정 (`Clock.systemDefaultZone()`) 그대로 유지** — 시스템에 `Clock` 빈은 *하나*, audit listener 와 (미래) 비즈니스 timestamp 캡처 양쪽이 *공유*. 진실원 단일 (§6).
- `@EnableJpaAuditing` 위치 — 신규 `AuditConfig` 또는 기존 `TimeConfig` 확장. 결정 사유: `TimeConfig` 가 *시간 관련 인프라* 의 단일 자리. 새 클래스 도입 비용 대비 가치 음 — `TimeConfig` 에 `@EnableJpaAuditing` + `DateTimeProvider` 빈 추가.

### 2. `createdAt` / `updatedAt` 의 *책임 자리* — Entity-level only

- `PostJpaEntity` 가 `@CreatedDate` / `@LastModifiedDate` 보유. `@EntityListeners(AuditingEntityListener.class)` 부착.
- listener 가 *INSERT 시* `createdAt` + `updatedAt` 양쪽을 채우고, *UPDATE 시* `updatedAt` 만 갱신.
- listener 의 시간 출처 = `DateTimeProvider` = `Clock` 빈 — 진실원 단일 정합.

### 3. `Post` 도메인 — timestamp 필드 / getter / 인자 *완전 제거*

- `Post` 클래스에서 `createdAt` / `updatedAt` 필드 + `getCreatedAt()` / `getUpdatedAt()` getter 완전 삭제.
- `Post.create(String title, String body, String author)` — `LocalDateTime now` 인자 *제거*. ADR-0007 §2 의 도메인 인자 결정 *dormant* (§7 참조).
- `Post.updateContent(String title, String body)` — `LocalDateTime now` 인자 *제거*. PLAN-0007 의 invariant 4 종 (now non-null × 2, 역행 금지, 경계 허용) *모두 제거* — 책임 자리가 listener 로 이전되었으므로 도메인 invariant 가 아니다.
- `Post.reconstitute(Long id, String title, String body, String author)` — 영속 복원 자리. **이 메서드도 timestamp 인자 제거** — 도메인이 timestamp 를 *알지 않으므로* 복원 시 받을 이유 없음.
- 결과: 도메인은 audit metadata 의 *존재 자체를 모른다*. 도메인 invariant 는 `title`/`body`/`author` 의 형식 검증만.

#### 3.1 audit metadata 의 시맨틱 — *상태 변화* 시점

`createdAt` / `updatedAt` 은 **데이터 상태 변화 시점** 을 의미한다. *행위 (PUT 호출 자체)* 의 시점이 아니다. 의식적 구분:

- `createdAt` — row 가 *생성* 된 시점 (INSERT). `@PrePersist` listener 가 채움.
- `updatedAt` — row 의 *상태가 변경* 된 마지막 시점 (Hibernate dirty check 가 변경 감지 시 UPDATE). `@PreUpdate` listener 가 채움.
- **동일 값 PUT 은 *no-op***. `PUT /api/posts/1` 의 body 가 *기존 title/body 와 동일* 하면 Hibernate dirty check 가 *변경 없음* 판정 → UPDATE SQL 미발사 → `updatedAt` 불변. HTTP 응답은 200 OK + 갱신된 `PostResponse` 정상 반환 (RFC 7231 의 PUT idempotent 보장).

표준 정합 — *직접 적용 기술 근거* 둘:
- **Spring Data JPA `@LastModifiedDate`** — 표준 라이브러리의 *그 자체의 의도*. `@PreUpdate` 가 dirty check 통과 시에만 발화.
- **Hibernate dirty check** — *변경 감지 기반 listener 발화*. 본 시스템의 기술 기반이 *상태 변화 기준* 동작을 자연스럽게 강제.

PLAN-0007 의 `Post.updateContent(now, ...)` 가 *항상 updatedAt 진행* 한 동작은 *`LocalDateTime.now()` 직접 호출의 우연한 부산물* 이지 *의도된 invariant* 아니었음 — ADR-0008 이 audit listener 의 *표준 시맨틱* 으로 정합화.

**행위 추적 시맨틱이 필요해지는 미래 시점** (예: 관리자 페이지 *수정 시도* 로그, 개인정보 수정 시 *마지막 접근* 표시, 게시물 *삭제 시도* 감사) — `updatedAt` 컬럼 의미를 흐리지 않고 *별도 책임 자리* (API access log / request audit / `touchedAt` 같은 별도 컬럼) 로 도입. PLAN-0005-C 의 `TraceIdFilter` 가 *요청 단위 로깅* 자리로 이미 존재 — 확장 후보. 별도 ADR 시점에 결정.

### 4. `AuditedPost` record — outbound port 의 반환 타입

도메인이 audit 을 모르므로, *영속 어댑터가 도메인을 복원해 반환할 때* audit metadata 를 *별도로* 함께 돌려줘야 한다. 신규 record:

```java
// application/port/out/result/AuditedPost.java
public record AuditedPost(Post post, LocalDateTime createdAt, LocalDateTime updatedAt) {}
```

- `LoadPostPort.findById(Long id)` 반환 타입: `Optional<Post>` → `Optional<AuditedPost>`.
- `LoadPostPort.findPage(int page, int size)` 반환 타입: `PostPage` → `AuditedPostPage` (또는 `PostPage` 의 내부 `items` 가 `List<AuditedPost>`).
- **`SavePostPort` 분리** — 단일 port 가 *id 유무로 신규/갱신 magic 분기* 하는 패턴은 *책임이 암묵적* 이라 거부. *CQRS 정신* (ADR-0003) 과 정합하게 *create vs update* 를 **별도 outbound port** 로 분리:
  - `CreatePostPort.save(Post post)` — `post.getId() == null` 보장 (신규 영속). 반환 `AuditedPost`.
  - `UpdatePostPort.save(Post post)` — `post.getId() != null` 보장 (기존 영속 갱신). 반환 `AuditedPost`.
  - 호출자가 *의도를 port 선택* 으로 명시. `PostCommandService.create()` 는 `CreatePostPort` 만, `update()` 는 `UpdatePostPort` 만 사용. id 분기 magic 사라짐.

#### 4.1 `UpdatePostPort` 구현 전략 — *load-mutate-save*

**도메인이 timestamp 를 모르는 상황에서 audit 보존을 위해 어댑터가 *managed entity 를 끝까지 잡고* 갱신한다**. JPA 의 *detached entity merge* 함정 회피.

- **`CreatePostPort` 구현 (신규 경로)**: `PostMapper.toEntity(post)` → `repository.save(entity)` (persist). `@PrePersist` listener 가 `createdAt`/`updatedAt` 자동 채움. `AuditedPost(toDomain(saved), saved.getCreatedAt(), saved.getUpdatedAt())` 반환.
- **`UpdatePostPort` 구현 (갱신 경로)**: *기존 entity 를 repository 에서 조회한 뒤*, mutable field 만 setter 로 갱신:
  ```java
  PostJpaEntity existing = repository.findById(post.getId())
          .orElseThrow(() -> new PostNotFoundException(post.getId()));
  existing.setTitle(post.getTitle());
  existing.setBody(post.getBody());
  // createdAt 건드리지 않음 — @LastModifiedDate listener 가 updatedAt 만 갱신
  // <flush 강제 — 아래 invariant 참조>
  return new AuditedPost(toDomain(existing), existing.getCreatedAt(), existing.getUpdatedAt());
  ```
  port 분리로 *왜 load-mutate-save 인지* 가 port 시그니처에서 자명 — `UpdatePostPort` 라는 이름과 *기존 영속 갱신* 의도가 일치. JPA 의식 없이도 *왜 이렇게 하는지* 명료.

  **flush 보장 invariant** — `UpdatePostPort` 는 *반환 audit timestamp 를 읽기 전에 flush 가 끝나 있어야 한다*. 사유: `repository.save()` 만으로는 *flush 보장 안 함*. managed entity 의 dirty 상태로 두고 트랜잭션 commit 시점에 flush 됨. `@PreUpdate` listener 도 그때 발화. service 가 *use case 반환 전* `getUpdatedAt()` 읽으면 *이전 값* (stale) 반환 위험. flush 강제로 listener 즉시 발화 → `getUpdatedAt()` 이 *방금 채워진 값*.

  구현 수단 — `repository.saveAndFlush(existing)` / `repository.flush()` / `entityManager.flush()` 중 *PLAN-0008 에서 선택*. ADR 은 *invariant* 결정, *수단 선택*은 구현 자리.

  **§3.1 의 no-op 시맨틱과 정합** — dirty 없으면 flush 해도 UPDATE 미발사 → `updatedAt` 자연 유지.

- **거부한 대안 — 단일 `SavePostPort` 가 내부 id 분기**: 어댑터 구현 내부의 *암묵적 매직*. 호출자 시그니처는 동일해도 *어댑터 내부 동작이 id 에 따라 갈리는* 자리 — JPA 의식이 있어야 *왜 신규는 toEntity / 갱신은 load-mutate 인지* 이해 가능. CQRS 정신과도 마찰.
- **거부한 대안 — `@Column(updatable = false)` 로 createdAt 컬럼 보호**: DB 컬럼은 보존되나 *merge 후 반환 entity 의 `createdAt` 은 여전히 null* (listener 가 UPDATE 시 createdAt 안 채움). `AuditedPost(post, null, updatedAt)` 반환 → audit 정보 손실. 컬럼 보호만으론 부족.
- **거부한 대안 — save 후 별도 SELECT 로 재조회**: 추가 쿼리 + transaction 내 1 차 캐시 일관성 의문. load-mutate-save 가 더 정직.

### 5. `AuditedPostResult` record — use case 반환 타입

use case 가 결과를 *adapter 친화 형태* 로 반환하기 위한 application-layer DTO. 신규 record:

```java
// application/port/in/result/AuditedPostResult.java
public record AuditedPostResult(
    Long id, String title, String body, String author,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {}
```

- `CreatePostUseCase.create(...)` 반환 타입: `Post` → `AuditedPostResult`.
- `GetPostUseCase.getById(...)` 반환 타입: `Post` → `AuditedPostResult`.
- `UpdatePostUseCase.update(...)` 반환 타입: `Post` → `AuditedPostResult`.
- `ListPostsUseCase.list(...)` 의 기존 `PostListResult` — `items` 타입을 `List<AuditedPostResult>` 로 갱신.
- `PostController` 는 `AuditedPostResult` 를 받아 `PostResponse` 매핑 (현 `PostResponse.from(Post)` → `PostResponse.from(AuditedPostResult)`).

#### 5.1 매핑 책임 — *AuditedPostResult 가 AuditedPost 를 import 하지 않는다*

- `port.in.result.AuditedPostResult` 가 `port.out.result.AuditedPost` 를 *직접 의존하면* inbound → outbound 의존 — port 간 DTO coupling 발생.
- 합성 책임은 **application service** 가 가진다. service 가 `AuditedPost` 를 분해해 `AuditedPostResult` 생성:
  ```java
  AuditedPost audited = loadPostPort.findById(id).orElseThrow(...);
  return new AuditedPostResult(
      audited.post().getId(), audited.post().getTitle(), audited.post().getBody(),
      audited.post().getAuthor(), audited.createdAt(), audited.updatedAt()
  );
  ```
- 매핑 boilerplate 가 반복되면 `AuditedPostResult` 에 `from(Post post, LocalDateTime createdAt, LocalDateTime updatedAt)` 정적 헬퍼 도입 가능 — *AuditedPost 분해된 후* 의 시그니처라 inbound/outbound 의존 없음. `from(AuditedPost)` 시그니처는 *금지*.
- 거부한 대안 — `AuditedPostResult.from(AuditedPost)` 1-인자 헬퍼: inbound port 가 outbound port 의 결과 타입을 알게 됨. application 내부 *경계* 약화.

### 6. 진실원 단일 — Clock 빈 1 개의 책임

```
[TimeConfig.systemClock()] —— ┬── DateTimeProvider —── AuditingEntityListener (createdAt/updatedAt)
                              │
                              └── (미래) Application 캡처 ── 도메인의 비즈니스 timestamp 메서드
```

- 시스템에 `Clock` 빈은 *정확히 1 개*. 모든 시간 출처가 이 빈에서 파생.
- 테스트에서 `@TestConfiguration` 으로 `Clock` 빈 override 시 *audit listener 와 (미래) 비즈니스 timestamp 양쪽 모두 결정적* — 단일 빈 교체로 시스템 전체 시간 결정성 확보.
- 진실원 단일이 깨지는 시점은 다음 둘 중 하나 — (a) 별도 `Clock` 빈 도입 (예: UTC 전용 / 도메인 전용), (b) audit 외 timestamp 출처 추가 (예: DB 의 `CURRENT_TIMESTAMP`). 둘 다 *별도 ADR* 영역.

### 7. ADR-0007 의 *dormant* 상태 처리

ADR-0007 (Clock 주입 정책) 의 status 는 **Proposed 그대로 유지**. 본 ADR-0008 이 다음을 명시:

> ADR-0007 의 도메인 시간 인자 결정 (§2/§3 의 `Post.create(LocalDateTime now, ...)` 패턴) 의 *현 적용 대상* 은 0 개다. `createdAt`/`updatedAt` 의 책임 자리가 Entity-level audit listener 로 이전되었고, 비즈니스 의미를 가진 timestamp (예: `publishedAt`, `editedAt`) 는 *현 도메인에 존재하지 않는다*. 따라서 ADR-0007 의 도메인 인자 패턴은 *dormant* — 정책 자체는 유효하나 *적용 대상이 0 인 상태*. 비즈니스 timestamp 도입 시점에 *해당 메서드 한정* 으로 자연 재활성화된다.

**ADR-0007 을 Superseded 로 변경하지 않는 이유**:
- 정책의 *패턴 자체* (값 전달 + Application 캡처 + fixture 단일 원천) 는 *비즈니스 timestamp 도입 시점에 그대로 재사용* 가능. supersede 는 *정책 폐기* 를 함의하므로 부정확.
- `Clock` 빈 결정 (§1) 과 `PostFixtures.fixedClock()` / `fixedClockAt()` 헬퍼 (§8) 는 *본 ADR-0008 도 그대로 사용*. 부분 supersede 표기는 ADR 흐름을 복잡하게 만든다.

### 8. 테스트 fixture / 패턴 — `PostFixtures` 유지

- `PostFixtures.FIXED_CLOCK` / `FIXED_NOW` / `fixedClock()` / `fixedClockAt(LocalDateTime)` — **유지**. 사유:
  - `@DataJpaTest` slice 에서 `@TestConfiguration` 으로 fixed `Clock` 빈 override → audit listener 가 *결정적 timestamp* 채움 → 테스트가 *Auditing 동작* 을 결정적으로 어서트.
  - `FIXED_NOW` 는 `aReconstitutedPost(Long)` 의 *디폴트 timestamp* 영역에서 의미 유지 (단, `Post.reconstitute(...)` 가 timestamp 인자를 빼므로 helper 시그니처 변경 필요 — PLAN-0008 의 실행 디테일).
- `PostTest` 의 PLAN-0007 신규 invariant 테스트 4 종 (now non-null × 2, 역행 금지, 경계) — *모두 삭제*. 책임이 audit listener 로 이전.
- `PostCommandServiceTest` 의 `Clock` 인자 제거 — `PostCommandService` 에서 `Clock` 필드 빠지므로.
- `PostPersistenceAdapterTest`:
  - **slice 가 audit 빈 + 테스트용 Clock 빈을 *명시적으로* import 해야 한다**. `@DataJpaTest` 의 slice scope 는 `@Configuration` 자동 import 안 함 — `TimeConfig` (`@EnableJpaAuditing` + `DateTimeProvider` 빈 포함) 가 slice 에 없으면 `AuditingEntityListener` 가 동작하지 않아 *listener 가 채워야 할 timestamp 가 null* 인 상태로 테스트 통과해 *audit 도입이 실패해도 못 잡는* 위험.
  - `TimeConfig` 만 import 하면 production `Clock.systemDefaultZone()` 빈이 등록됨 — listener 가 *현재 시각* 으로 timestamp 채워 `FIXED_NOW` 어서트 실패. **`@Primary` 로 시점 조절 가능한 Clock 빈을 제공하는 *테스트 전용 `@TestConfiguration`* 함께 import 필수**.
  - 채택 패턴 — *MutableClock 을 `@Primary` 로 등록*. 초기값 = `FIXED_NOW`. 기본 auditing 어서트와 §8.1 의 *t1 → t2 → t3* 시나리오를 **동일 빈으로** 처리:
    ```java
    @DataJpaTest
    @Import({PostPersistenceAdapter.class, TimeConfig.class, TestAuditConfig.class})
    class PostPersistenceAdapterTest { ... }

    @TestConfiguration
    class TestAuditConfig {
        @Bean @Primary
        MutableClock auditClock() {
            return MutableClock.startingAt(PostFixtures.FIXED_NOW);
        }
    }
    ```
  - `MutableClock` 은 `java.time.Clock` 을 확장하며 `setTo(LocalDateTime)` / `advance(Duration)` 을 노출 (§8.1). 초기 시점이 `FIXED_NOW` 이므로 *시점 조작 없이* 기본 auditing 어서트가 가능하고, *시점 조작 시* no-op 시나리오 검증이 가능 — 둘이 *같은 패턴* 으로 처리.
  - `TestAuditConfig` / `MutableClock` 의 위치 (예: `src/test/java/com/dunowljj/board/config/`) 와 시그니처 디테일은 PLAN-0008 실행 자리.
  - audit listener 가 채운 `createdAt`/`updatedAt` 이 (조작 없으면) `FIXED_NOW` 와 일치하는지 어서트.
- `PostControllerTest` — `PostResponse.from(AuditedPostResult)` 매핑 갱신. `MockitoBean` 으로 mock 한 use case 반환 타입도 `AuditedPostResult` 로 변경.
- `PostE2EIT` — 어셈블리 한정이라 변경 최소. response body 의 `createdAt`/`updatedAt` 어서트는 `notNullValue()` 또는 `matchesPattern` 으로 약하게 유지 (audit 동작 자체는 slice 가 검증).

#### 8.1 *시점 조절 가능 Clock* 패턴 + no-op 어서트 케이스

§8 의 `TestAuditConfig` 가 등록하는 **MutableClock** (초기값 `FIXED_NOW`) 을 활용. `setTo(LocalDateTime)` / `advance(Duration)` 으로 *시점을 명시적으로* 변경해 *t1 생성 → t2 시점 update* 같은 시나리오 구성.

거부한 대안 — *advancing fixed clock* (매 호출마다 자동 진행): 사유 — audit listener 가 시간을 *몇 번 읽는지* 에 테스트 동작이 묶임. Hibernate 의 listener 호출 횟수는 *내부 구현 디테일* 이라 *명시 시점 제어 (t1 → t2 → t3)* 의 안정 어서트 어려움.

**요구 사유** — §3.1 의 *동일 값 PUT no-op* 시맨틱을 검증하려면 *시간 변화* 와 *상태 변화* 의 분리 케이스가 어서트되어야 함.

**필수 어서트 케이스 두 개 (PLAN-0008 AC 영역)**:

1. **동일 값 PUT — no-op**:
   ```
   clock = t1; create(...) → createdAt == t1, updatedAt == t1
   clock.advance(...) → t2
   update(*동일 title/body*) → updatedAt == t1 (불변, no-op 확인)
   ```
2. **다른 값 PUT — listener 발화**:
   ```
   clock = t1; create(...) → updatedAt == t1
   clock.advance(...) → t3
   update(*다른 title/body*) → updatedAt == t3 (state 변화 → listener 발화)
   ```

이 두 케이스가 *§3.1 의 시맨틱이 실제 시스템에 살아있음* 의 증거.

## Considered Alternatives

- **도메인이 audit 필드를 *유지* 하되 read-only getter 로만 노출** (옵션 a 와 유사) — 거부 사유: `Post.create()` 직후 `getCreatedAt() == null` 의 *부분 초기화 상태* 가 발생. Aggregate 무결성 약화. 또 도메인이 *알 필요 없는* 정보를 보유 — DDD 의 *aggregate 책임* 원칙과 마찰. (Aggregate 가 audit 을 *알게* 두는 게 *현실적 절충* 이긴 하지만, *완전 제거* 가 응집도 측면에서 정직.)
- **Auditing 없이 Application service 가 timestamp 명시 캡처 + 도메인 인자** (PLAN-0007 의 현 상태 유지) — 거부 사유: 도메인이 *audit 책임을 들고 다님* — 본래 책임 자리 아님. Context 에서 정리한 *거짓 invariant* / *진실원 다중화 위험* / *YAGNI 위반 잔재* 의 누적.
- **도메인 timestamp 만 두고 Auditing 부분 도입** (도메인 + listener 가 *동시에* 채움) — 거부 사유: *두 진실원* 경쟁. listener 가 도메인 값 덮어쓰는지 / 도메인이 listener 결과 무시하는지 — 결정 못 함. 실제 동작이 *환경 의존*.
- **`AuditedPost` / `AuditedPostResult` 대신 `Post` 클래스에 audit 필드 *임시* 추가** — 거부 사유: 도메인 시그니처에 audit 을 *섞는* 자리. 본 ADR 의 *완전 분리* 결정과 충돌.
- **Spring Data Envers / 별도 audit 테이블** — 거부 사유 (현 시점): 단순 timestamp audit 만 필요. 변경 이력 / 누가 수정했나 (`@CreatedBy` / `@LastModifiedBy`) 같은 요구 *없음*. Envers 는 schema 두 배 증가 + 쿼리 복잡도 ↑. 비용 대비 가치 음. 인증/인가 (`AuditorAware`) ADR 시점에 재검토.

## Rejected Suggestions

본 ADR 설계 과정에서 *실제로 제안되었으나 거부된* 안.

- **ADR-0007 을 Superseded 로 변경** — 거부. 사유: 정책의 *패턴 자체* 는 유효 (값 전달 + Application 캡처 + fixture 단일 원천). 비즈니스 timestamp 도입 시 *그대로 재사용*. supersede 는 *폐기* 함의라 부정확. dormant 명시로 대체 (§7).
- **`AuditedPost` 의 자리를 `application/port/out/`** vs `application/port/out/result/`** — 부분 보류. 결정 사유: PLAN-0008 실행 시 *port out 의 패키지 컨벤션* 과 정합 확인 후 결정. ADR 본문에서는 `application/port/out/result/` 안 (`port/in/result/` 와 미러) 으로 *권장* 만, 실제 위치는 PLAN-0008 에서 *현 패키지 구조* 와 정합 결정.
- **`PostResponse` 가 `AuditedPostResult` 대신 *Post + audit 별도 인자* 받음** — 거부. 사유: API 가장자리(Controller) 에서 *두 인자 조합* 책임을 지면 매번 누락 위험. 단일 결과 record 가 *호출 사이트 응집도* 우선.
- **Audit listener 가 채울 `createdAt` / `updatedAt` 의 *컬럼 null 허용* 여부 결정 ADR 에 명시** — 보류. 사유: 영속 schema 결정 (`@Column(nullable = false)`) 은 *PLAN-0008 의 실행 디테일*. 다만 *invariant 의 정신* 으로 *non-null* 권장.
- **`AuditedPost` 의 `createdAt` 이 *항상 non-null* 이라는 도메인 외 invariant 를 ADR 에 박기** — 거부. 사유: listener 가 *INSERT 시* 채우므로 *영속 후 조회* 에선 항상 non-null 이지만, 그 invariant 는 *영속 어댑터 책임* 이지 *application 결과 타입의 invariant* 아님. application 은 *non-null 가정* 으로 동작하나 *강제* 는 영속 layer.
- **테스트 fixture 의 `PostFixtures.aValidPost()` 가 `AuditedPost` 를 반환하도록 변경** — 보류. 사유: PLAN-0008 의 실행 디테일. *도메인 fixture* 와 *audit-aware fixture* 를 분리할지 (예: `aValidPost()` → `Post`, `anAuditedPost(Long id)` → `AuditedPost`) 결정 영역.
- **`Post` 도메인이 *최소 정보 응집* 을 위해 `PostContent` value object 까지 풀어내기** — 거부 (본 ADR 범위 밖). `PostContent` 는 도메인 invariant (title/body 형식) 의 응집 자리. audit 분리와 무관.

## Consequences

**긍정적 영향**

- **도메인 순수도 최대화** — `Post` 가 audit metadata 의 *존재 자체를 모름*. invariant 는 *비즈니스 규칙 한정* (title/body/author 형식). 도메인 테스트가 *도메인 책임* 만 검증.
- **진실원 단일 강화** — `Clock` 빈 1 개가 시스템 시간 출처. listener / (미래) 비즈니스 timestamp 양쪽이 *공유*. 테스트가 fixed `Clock` 빈 1 개 override 로 시스템 전체 결정성.
- **거짓 invariant 제거** — PLAN-0007 의 4 종 invariant (now non-null × 2, 역행 금지, 경계 허용) 가 *audit 영역으로 이전*. 도메인 테스트의 *현 책임* 만 남음.
- **ADR-0007 의 *재활성화 자리* 확보** — 비즈니스 timestamp 도입 시 *해당 메서드 한정* 으로 자연 재활성화. 정책의 *적용 범위* 가 *비즈니스 의미가 있는 자리* 로 좁혀짐.
- **Auditing 의 *자연스러운 자리*** — `@CreatedDate` / `@LastModifiedDate` listener 가 timestamp 채우는 것이 Spring Data JPA 의 *기본 흐름* 과 정합. 코드의 *놀라움* 감소.

**부정적 영향 / 트레이드오프**

- **마이그레이션 범위 큼** — PLAN-0007 의 도메인 시그니처 변경을 *7 일 만에 다시* 변경. `Post.create` / `updateContent` / `reconstitute` 시그니처 + invariant 모두 변경. `LoadPostPort` 반환 타입 변경 (`Post` → `AuditedPost`). 기존 `SavePostPort` 제거 + `CreatePostPort` / `UpdatePostPort` 신규 (§4). use case 반환 타입 변경 (`Post` → `AuditedPostResult`) — `PostController` 까지 영향. PLAN-0008 의 변경 폭은 PLAN-0007 의 ≈ 2 배.
- **`UpdatePostPort` 의 조기 flush 비용** — §4.1 의 *flush 보장 invariant* 채택은 *응답 audit timestamp 정확성* 을 위해 다음 trade-off 를 감수: (a) **persistence context 전체 flush** — 동일 트랜잭션의 다른 dirty entity 도 함께 flush 되어 *부수 효과* 발생, (b) **constraint failure 시점 앞당김** — DB constraint 오류가 *flush 시점* 에 발생 (트랜잭션 commit 대신), 예외 추적 위치 변화, (c) **batch update 최적화 차단** — 미래 *여러 entity 를 한 statement 로 묶는* 최적화 도입 시 update 경로가 제외됨. 현 단일 entity update 시나리오에선 영향 작으나 비용 *존재* 인지.
- **정렬 tie-breaker 권장 — production 데이터 일관성 관점**: 현재 `PostJpaRepository.findAllByOrderByCreatedAtDesc(Pageable)` 는 `createdAt` 단일 정렬. *production 에서도* 동일 ms 안에 여러 INSERT 가 발생하면 (대량 입력, 동시 요청, ms 미만 정밀도 손실 등) `createdAt` 충돌 가능 — 정렬 결과가 DB 의 *임의 순서* 에 의존하면 페이지 경계에서 *동일 row 가 두 페이지에 나타나거나 사라지는* 사고 발생.
  - **결정**: 정렬에 *id DESC* tie-breaker 추가. 예: `findAllByOrderByCreatedAtDescIdDesc(Pageable)`. id 가 *insertion 순서* 의 자연 proxy 라 동일 createdAt 안에서도 결정적. PLAN-0008 의 실행 디테일.
  - **테스트 비결정성과 무관**: `PostFixtures.fixedClockAt(LocalDateTime)` 헬퍼가 *target localNow 마다 다른 Clock* 생성 가능. 정렬 테스트가 *각 INSERT 전에 다른 시점의 fixed Clock 으로 교체* 하면 audit listener 가 *서로 다른 createdAt* 채움 — 진실원 단일 의식 (§6) 안 깨짐. tie-breaker 권장은 *production 일관성* 사유.
- **`AuditedPost` / `AuditedPostResult` 두 record 신규** — 도메인 + audit 합성 자리. application/adapter layer 의 *타입 표면* 확장. 후속 도메인 추가 시 동일 패턴 (`AuditedComment`, `AuditedCommentResult`) 누적 위험. 일반화 (`Audited<T>`) 도입 시점은 *두 번째 도메인 추가 시* 재검토.
- **`Post.reconstitute(...)` 가 timestamp 인자 제거 → 호출 사이트 변경** — `PostMapper.toDomain(entity)` 에서 entity 의 audit 필드를 *별도로* 추출해 `AuditedPost(Post, createdAt, updatedAt)` 합성. mapper 책임 증가.
- **테스트 fixture 변경 비용** — `PostFixtures.aReconstitutedPost(Long id, LocalDateTime createdAt, LocalDateTime updatedAt)` overload 의 *반환 타입* 결정 필요. `Post` 반환 + audit 별도 / `AuditedPost` 반환 둘 중 선택 — PLAN-0008 실행 디테일.
- **PLAN-0007 의 도메인 invariant 테스트 4 종 삭제 — 회귀 안전망 일부 손실** — 그러나 그 invariant 는 *audit listener 의 책임* 으로 이전되었고 Spring Data JPA 가 *그 책임을 검증된 방식으로 처리*. 안전망의 *위치* 가 옮긴 것이지 *사라진* 것 아님. `@DataJpaTest` 의 audit 어서트가 *대체*.
- **ADR-0007 의 *짧은 수명* 인상** — PLAN-0007 머지 (2026-05-17) 와 PLAN-0008 머지 시점 사이가 짧음. 정책 변동성에 대한 우려 가능. 본 ADR 본문이 그 변동을 *Auditing 도입과 자연 동반* 으로 명시 → ADR-0007 *정책 자체* 는 dormant 로 보존되므로 *변동* 이 아닌 *진화*.

## Open Questions

- **`@CreatedBy` / `@LastModifiedBy` (auditor 추적) 도입 시점** — Spring Security 도입 ADR 시점에 함께 결정. 현재는 *시간 audit 만*.
- **`Audited<T>` 일반화 record** — 두 번째 도메인 (예: `Comment`) 추가 시점에 *반복 패턴이 명백해진 시점에* 재검토. 본 ADR 은 도메인별 명시 record (`AuditedPost`) 채택.
- **`AuditedPost` 의 위치 (`port/out/` vs `port/out/result/`)** — PLAN-0008 실행 시점에 *현 패키지 구조* (예: `application/common/PostPage.java`) 와 정합 결정. ADR 본문은 권장만.
- **`PostMapper.toDomain(entity)` 가 `AuditedPost` 반환 vs `Post + audit 별도 호출`** — mapper 책임 형태 결정. PLAN-0008 의 실행 디테일.
- **`PostListResponse` 의 `posts[*]` 항목 형태** — `PostResponse` 가 audit 포함하도록 유지 vs 분리. UI 가 항상 timestamp 표시하므로 *포함* 권장 (현 `PostResponse` 와 호환).
- **Soft delete (`deletedAt`) 와 Auditing 의 관계** — 사용자 결정으로 *미루기*. `deletedAt` 도입 ADR 시점에 *audit 채움 vs 도메인 메서드 (`softDelete(now)`) 채움* 결정.

## Related

- ADR-0003 (Clean/Hexagonal + DDD + CQRS) — 도메인 framework 무지. 본 ADR 의 *도메인이 audit 모름* 결정 근거. `AuditedPost` / `AuditedPostResult` 는 *application layer* 결과 타입이라 도메인 경계 유지.
- ADR-0007 (시간 정책 — Clock 주입) — 본 ADR 의 *dormant 처리* 대상. 본 ADR 이 ADR-0007 의 §1 (Clock 빈) 과 §4 (fixture 단일 원천) 은 *그대로 사용*, §2 / §3 (도메인 시간 인자 + Application 캡처) 은 *적용 대상 0 으로 휴면*.
- ADR-0006 (테스트 전략) §1 표 — Driven Adapter (Persistence) 행이 *auditing* 명시. 본 ADR 이 그 항목의 직접 실현.
- ADR-0005 (예외/에러 응답 정책) — 무관 (audit 은 정상 경로).
- PLAN-0007 (Clock 주입 구현) — 본 ADR 의 직전 작업. PLAN-0008 이 이를 부분 되돌림 (도메인 시간 인자).
- PLAN-0008 (예정) — 본 ADR 의 구현 단위.
