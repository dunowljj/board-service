# ADR-0007: 시간 정책 — `Clock` 주입과 결정적 시간 의식

## Status
Proposed

## Date
2026-05-17

## Context

현재 도메인의 시간 의존 로직은 `LocalDateTime.now()` 직접 호출로 구성된다.

```java
// src/main/java/com/dunowljj/board/domain/post/Post.java
public static Post create(String title, String body, String author) {
    LocalDateTime now = LocalDateTime.now();   // ← ambient JVM clock
    return new Post(null, new PostContent(title, body), author, now, now);
}

public void updateContent(String title, String body) {
    this.content = new PostContent(title, body);
    this.updatedAt = LocalDateTime.now();      // ← ambient JVM clock
}
```

이 직접 호출은 다음 부담을 누적시킨다.

- **테스트 비결정성** — 시간 어서트가 *대소 관계* 또는 *값이 갱신됨* 정도로만 가능. PLAN-0006-C 의 `save_assigns_id_and_findById_returns_same_post` 가 `assertThat(saved.getCreatedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after)` 같은 *boundary* 검증으로 후퇴한 근거가 이 자리. PLAN-0006-D 의 `updatedAt` 어서트도 *절대값 회피* 를 Risks #3 에 박아둠.
- **JDBC/DB 정밀도 round-trip 위험** — Codex 가 PR #12 에서 P2 로 지적한 자리. JVM `LocalDateTime.now()` 가 마이크로~나노 정밀도, DB 가 더 낮으면 `saved.getCreatedAt() != found.getCreatedAt()` 분기 가능. *시계 자체가 결정적*이면 round-trip 비교 전략(정규화/tolerance) 도 더 깔끔히 도입 가능.
- **Auditing 도입 시 시간 출처 분기 위험** — Spring Data JPA Auditing 의 `@CreatedDate`/`@LastModifiedDate` 는 `DateTimeProvider` 빈을 통해 시간을 얻는다. 도메인 직접 호출과 *두 시간 출처* 가 공존하면 같은 트랜잭션에서 도메인이 채운 `createdAt` 과 listener 가 채운 timestamp 가 서로 다른 시계로 정해질 수 있음. 단일 출처로 통일해야 Auditing 도입 시 의식이 흔들리지 않음.
- **ADR-0006 §5 가 이미 deferred slot 으로 명시** — *"시간 의존 로직은 `Clock` 주입을 *목표*로 한다 — 단, 별도 ADR/Plan."* PLAN-0006 시리즈 완결과 함께 이 약속을 회수해야 후속 작업(Auditing · 댓글/조회수 · soft delete · Testcontainers) 이 *결정적 시간 위에서* 진행됨.

이 영역은 *지금* 정책이 없으면 신규 도메인(Comment, ViewCount, 등) 추가 시 동일 함정이 곱셈으로 누적되는 자리다.

## Decision

도메인의 시간 의존 로직은 **`LocalDateTime` *값* 을 인자로 받고, Application 계층이 `Clock` 으로 그 값을 캡처한다**. ambient JVM 시계(`LocalDateTime.now()` 인자 없는 호출) 직접 호출 금지. 도메인은 `Clock` 추상화를 모른다 — *값* 만 받는다. 정책의 윤곽만 정의하고 구현은 PLAN-0007 에서 단계적 수행.

### 1. 시간 출처 — 단일 `Clock` 빈

- Application 컨텍스트에 `Clock` 빈 1 개 등록. 위치 — `BoardServiceApplication` 또는 별도 `@Configuration TimeConfig`. 구현체:
  ```java
  @Bean
  public Clock systemClock() {
      return Clock.systemDefaultZone();
  }
  ```
- `Clock.systemDefaultZone()` 채택, **`Clock.systemUTC()` 보류**. 사유 — 현재 도메인은 `LocalDateTime`(timezone-naive) 을 사용하고 운영/저장도 default zone 의식 위에 깔려 있음. UTC 로의 변경은 *시간대 의식* 변경(예: `Instant`/`OffsetDateTime` 으로 마이그레이션) 과 동시에 결정되어야 하므로 별도 ADR (Open Questions).
- 도메인은 Spring 을 모르므로 `Clock` 빈을 *직접 알지 않는다*. Application 계층이 빈을 주입받아 도메인 factory/메서드에 *명시적으로 전달*한다.

### 2. 도메인 API — `LocalDateTime` *값* 을 명시 파라미터로

도메인 factory 와 시간 의존 메서드는 `LocalDateTime` *값* 을 첫 번째 인자로 받는다. 도메인은 `Clock` 추상화를 *모른다* — 값만 받는다.

```java
// domain/post/Post.java
public static Post create(LocalDateTime now, String title, String body, String author) {
    return new Post(null, new PostContent(title, body), author, now, now);
}

public void updateContent(LocalDateTime now, String title, String body) {
    this.content = new PostContent(title, body);
    this.updatedAt = now;
}
```

- 도메인 순수도 — JDK 의 `Clock` 도 추상화 의존인데, 그조차 도메인에서 빼고 *값* 만 받으므로 더 얕음. 도메인은 "시간을 어떻게 얻는가" 를 모르고 "시간이 이 값이라면 어떤 상태가 되는가" 만 안다.
- 트랜잭션 일관성 — 한 use case 안에서 여러 도메인 호출(예: 미래 `Comment` 작성 + `ViewCount` 증가)이 *동일한 `now`* 를 공유. Application 이 `now` 를 한 번 캡처해 모든 도메인 호출에 같은 값을 넘기는 패턴이 자연스럽게 강제됨.
- 테스트 — `Clock.fixed(...)` 없이 `LocalDateTime.of(2026, 5, 17, 10, 0)` 리터럴로 충분. 도메인 단위 테스트가 가장 가벼움.
- 거부한 대안 — *도메인이 `Clock` 인자를 받음* (`Post.create(Clock clock, ...)`): 도메인이 시계 추상화를 알게 되고, 호출마다 미세하게 다른 `now` 값을 가질 수 있어 트랜잭션 일관성이 흐려짐. Rejected Suggestions 에 별도 기록.
- 거부한 대안 — 도메인 인스턴스 필드로 `Clock` 보유: 상태와 무관한 협력자를 들고 다님. JPA/equals/직렬화 비용.
- 거부한 대안 — `@Component PostFactory` 가 `Clock` 필드 보유: 도메인에 Spring 빈 → ADR-0003 `domain_pure` 위반.

#### 2.1 사전조건 — *값* 을 받게 되면서 도메인 invariant 가 새로 생긴다

도메인이 외부에서 시간 값을 받게 되면 기존(내부 `now()` 호출) 에는 불가능했던 입력이 들어올 수 있다. 다음을 *도메인 invariant* 로 명시한다.

- **`now` 는 non-null.** `create(...)` / `updateContent(...)` 등 시간 값을 받는 모든 메서드. 위반 시 `IllegalArgumentException` 또는 `NullPointerException` (`Objects.requireNonNull`). `reconstitute(...)` 가 이미 timestamp non-null 을 보장하는 정신과 정합.
- **`updateContent(now, ...)` 의 `now` 는 *역행 금지*.** `now < this.updatedAt` 거부 (`IllegalArgumentException`). 사유:
  - `createdAt`/`updatedAt` 은 §6 가 명시한 *감사용 메타데이터* 성격. ADR-0008 (Auditing) 도입 시 `@LastModifiedDate` listener 가 채우게 될 자리 — listener 는 자연 monotonic 이므로 *지금* 역행 금지 invariant 를 박아두면 Auditing 이전 시 *행동 변화 없음*. 반대로 *역행 허용*을 박으면 Auditing 이전이 *invariant 약화→강화* 가 되어 후속 영향이 큼.
  - "재현 테스트 / 데이터 보정 시 과거 시각 주입" 가치는 보존 — 그건 *audit timestamp* 가 아닌 *비즈니스 의미 timestamp* (예: `publishedAt`, `editedAt`) 의 영역. 그쪽 메서드는 *purpose-specific invariant* 로 별도 결정 (§2.2).
- **`createdAt == updatedAt` (`create(...)` 직후) 는 유지.** `create(now, ...)` 가 두 필드를 모두 `now` 로 셋.

위 결정은 *도메인 invariant 의 결정* 이며 구체 검증 코드 (`Objects.requireNonNull` / 비교 위치, 메시지 등) 는 PLAN-0007 의 영역.

#### 2.2 비즈니스 의미 timestamp 의 invariant 는 *purpose-specific*

> **YAGNI 명시.** 본 ADR 은 `publishedAt` / `editedAt` / `deletedAt` 같은 비즈니스 timestamp 의 *도입 결정* 을 내리지 않는다. 현 product 에 그런 필드는 *없으며*, 추가 여부는 각자의 trigger ADR/Plan (예: draft 도메인 ADR, soft delete ADR — item 5) 영역. 아래 예시 메서드 (`publish` / `markEdited` / `softDelete`) 는 *패턴 적용 형태* 를 보여주는 자리이며 *현 도입 예정 아님*.

`createdAt`/`updatedAt` (감사용) 과 별개로, 도메인이 *비즈니스 의미* 를 가진 timestamp 를 가질 수 있다 — 예: `publishedAt` (게시 시점), `editedAt` (사용자 의도 편집), `deletedAt` (soft delete 시점), `expiredAt` (만료 시점). 본 ADR 은 이런 필드의 *존재 여부* 를 결정하지 않는다 (해당 도메인 ADR/Plan 의 영역). 다만 *시간 인자 패턴* 은 동일하게 적용:

- 각 비즈니스 timestamp 메서드 (예: `publish(now)`, `markEdited(now)`, `softDelete(now)`) 는 *자체 invariant* 를 가진다. 일반화된 "역행 허용/금지" 룰은 없다 — *그 timestamp 의 의미* 가 invariant 를 정한다.
- 예시 invariant (참고):
  - `publish(now)`: `this.publishedAt == null` 강제 (한 번만 게시). `now >= this.createdAt` 강제.
  - `markEdited(now)`: `this.publishedAt != null` 강제 (게시된 글만 편집). `now >= this.editedAt` (역행 금지).
  - `softDelete(now)`: `this.deletedAt == null` 강제. `now >= this.updatedAt` 강제.
- 구체 메서드/필드 도입은 비즈니스 요구가 명확해진 시점의 별도 ADR/Plan. 본 ADR 은 *패턴* 만 결정.

### 3. Application 계층 — `Clock` 빈 주입 + *값* 캡처 후 도메인으로 위임

`Clock` 빈은 Application 계층까지만 흐른다. Application 이 `Clock` 으로 *현재 시각 값* 을 캡처해 도메인 factory 에 전달한다.

```java
// application/service/PostCommandService.java
@Service
@Transactional
@RequiredArgsConstructor
public class PostCommandService implements CreatePostUseCase, UpdatePostUseCase, DeletePostUseCase {
    private final Clock clock;                       // ← Spring 주입, Application 까지만
    private final SavePostPort savePostPort;
    // ...

    @Override
    public Post create(CreatePostCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        Post post = Post.create(now, command.title(), command.body(), command.author());
        return savePostPort.save(post);
    }

    @Override
    public Post update(UpdatePostCommand command) {
        Post post = loadPostPort.findById(command.id())
                .orElseThrow(() -> new PostNotFoundException(command.id()));
        LocalDateTime now = LocalDateTime.now(clock);
        post.updateContent(now, command.title(), command.body());
        return savePostPort.save(post);
    }
}
```

- *시계 의식의 책임 자리* 는 Application. 도메인은 *값으로 받은 시간 위에서 상태를 정한다* 는 책임만.
- 한 use case 내 여러 도메인 호출이 필요하면 `LocalDateTime now = LocalDateTime.now(clock)` 을 *한 번* 캡처해 *같은 값* 을 모든 호출에 전달 — 트랜잭션 시간 일관성을 호출자가 보장.

### 4. 테스트 패턴 — *fixture 가 시간 원천을 단일로 보유*

테스트 layer 별로 시간 의존이 다르지만(도메인은 `LocalDateTime` 값, Application 은 `Clock`), **테스트 시간 원천은 fixture 한 곳에서 단일 관리**. `FIXED_CLOCK` 과 `FIXED_NOW` 가 *derive 관계* 로 정합 — 두 테스트 layer 가 같은 시간 의식을 공유.

```java
// PostFixtures.java — 테스트 공용 시간 원천
public class PostFixtures {

    private static final Clock FIXED_CLOCK = fixedClockAt(
            LocalDateTime.of(2026, 5, 17, 10, 0));
    public static final LocalDateTime FIXED_NOW = LocalDateTime.now(FIXED_CLOCK);

    public static Clock fixedClock() { return FIXED_CLOCK; }

    // localNow 를 시스템 zone 기준 Instant 로 환산해 Clock 생성 —
    // LocalDateTime.now(clock) 결과가 정확히 localNow (환경 zone 무관,
    // prod 빈 systemDefaultZone() 의식과 일치).
    public static Clock fixedClockAt(LocalDateTime localNow) {
        ZoneId zone = ZoneId.systemDefault();
        return Clock.fixed(localNow.atZone(zone).toInstant(), zone);
    }
}
```

- **도메인 단위 테스트** — `PostFixtures.FIXED_NOW` 사용. `Clock` 등장 안 함.
  ```java
  @Test
  void create_assigns_now_to_both_timestamps() {
      Post post = Post.create(PostFixtures.FIXED_NOW, "t", "b", "a");
      assertThat(post.getCreatedAt()).isEqualTo(PostFixtures.FIXED_NOW);
      assertThat(post.getUpdatedAt()).isEqualTo(PostFixtures.FIXED_NOW);
  }
  ```
- **Application 단위 테스트** — `PostFixtures.fixedClock()` 을 생성자 주입. fixture 의 `FIXED_NOW` 와 *동일 시간* 보장.
  ```java
  @Test
  void create_captures_clock_now_and_delegates_to_domain() {
      PostCommandService sut = new PostCommandService(PostFixtures.fixedClock(), savePostPort, ...);
      sut.create(new CreatePostCommand("t", "b", "a"));

      var captor = ArgumentCaptor.forClass(Post.class);
      verify(savePostPort).save(captor.capture());
      assertThat(captor.getValue().getCreatedAt()).isEqualTo(PostFixtures.FIXED_NOW);
  }
  ```
- **E2E 테스트(`PostE2EIT`)** — `@TestConfiguration` 으로 `PostFixtures.fixedClock()` 을 `Clock` 빈으로 override 가능. 단 *어셈블리* 검증이 본질이라 시간 어서트 강화 여부는 PLAN-0007 결정.

#### 4.1 환경 zone 의식 — `Clock.fixed(Instant, ZoneId)` 의 함정

`Clock.fixed(Instant, ZoneId)` 의 zone 선택이 `LocalDateTime.now(clock)` 결과를 환경별로 흔든다 — `Instant.parse("...Z")` + `ZoneId.systemDefault()` 로 만들면 KST 환경은 UTC 환경 대비 9시간 차이의 LocalDateTime 을 본다. 본 ADR §1 이 production 빈을 `systemDefaultZone()` 으로 채택했으므로, **테스트도 *target `LocalDateTime` 부터 역산* 하는 `fixedClockAt(LocalDateTime)` 헬퍼** 로 환경 비의존성 확보.

- 거부한 대안 — `Clock.fixed(Instant, ZoneOffset.UTC)` 채택: 테스트가 환경 무관해지나 production 빈(`systemDefaultZone()`) 과 *zone 의식* 불일치. 향후 production 이 UTC 로 옮길 때(별도 ADR) 테스트도 함께 정리. 헬퍼 패턴이 prod 의식 따라가는 쪽이 더 정직.
- E2E 테스트(`PostE2EIT`) — `@TestConfiguration` 으로 `Clock.fixed` 빈 override 가능. 단 *어셈블리* 검증이 본질이라 시간 어서트 강화 여부는 PLAN-0007 결정.
- 픽스처(`PostFixtures.aValidPost()`) — `LocalDateTime` 인자를 받거나, 기본 상수 (`PostFixtures.FIXED_NOW`) + override 가능 helper. PLAN-0007 실행 디테일.

### 5. 도메인 메서드의 *시간 인자 명시 원칙*

도메인이 시간을 *값으로 받아야 하는* 메서드 — `create`, `updateContent`, `softDelete`(미래), `expire`(미래) — 는 모두 `LocalDateTime` 을 첫 인자로 받는다. **반대로**, 외부 시간을 받지 않고 자체 필드만 비교하는 메서드는 인자 추가 불요(예: `isOlderThan(other.createdAt)`).

원칙: *시간 값이 새로 만들어지는 자리* 만 인자 추가, *기존 시간 값을 사용하는 자리* 는 평소대로.

### 6. 감사용 timestamp vs 비즈니스 timestamp — *현재 통합, 향후 분리 가능성*

현 도메인의 `createdAt`/`updatedAt` 은 *감사용 메타데이터* 성격이 강함 — 비즈니스 규칙(invariant)이 아니라 *언제 만들어졌나/수정됐나* 의 운영 정보. ADR-0008 (예정 — Auditing) 도입 시 이 필드들이 *Entity-level* (`@CreatedDate`/`@LastModifiedDate`) 로 이전될 수 있음.

본 ADR 의 *값 전달 패턴* 은 두 경우 모두 호환:
- **현재** — `createdAt`/`updatedAt` 이 도메인 안에 있으므로 Application 이 `now` 를 캡처해 도메인 factory 에 전달.
- **Auditing 이후** — 감사용 필드는 Entity-level listener 가 채우므로 도메인 factory 에서 빠질 수 있음. 그러나 *비즈니스 의미를 가진 시간 값*(예: 예약 발행 시각, 만료 시각, soft delete 의 `deletedAt` 이 도메인 규칙에 사용될 때)은 *그대로 본 ADR 의 값 전달 패턴*. 도메인 — Application 책임 분리는 동일.

즉 본 ADR 은 *어느 시간이 도메인의 책임인가* 를 결정하지 않는다 — 그건 ADR-0008 의 영역. 본 ADR 은 *도메인이 시간을 만드는 자리에서 값으로 받는다* 만 결정.

## Considered Alternatives

- **도메인이 `Clock` 을 인자로 받음** (`Post.create(Clock clock, ...)`) — 초기 draft 가 채택한 안. 거부 사유: (a) 도메인이 시계 *추상화* 까지 알아야 하는 마찰 — *값* 만 받으면 충분, (b) 호출마다 미세하게 다른 `now` 값이 가능해 트랜잭션 시간 일관성이 흐려짐, (c) 단위 테스트가 `Clock.fixed(...)` 빌더를 다뤄야 해 `LocalDateTime` 리터럴 대비 verbose, (d) Application 이 *시간을 만드는 자리* 라는 책임 분배가 더 정확. 채택: 도메인은 `LocalDateTime` *값* 만 받음 (§2). Rejected Suggestions 에 회고 기록.
- **`TimeProvider` 도메인 인터페이스 도입** — 도메인 내에 `interface TimeProvider { Instant now(); }` 정의, Application 이 `Clock` 기반 구현체를 주입. 거부 사유: (a) `java.time.Clock` 이 이미 같은 역할의 표준 추상화 — 자체 인터페이스는 *재발명*, (b) 더 깊은 결함: 도메인이 시계 추상화를 가질 필요 자체가 없음 (값을 받으면 충분, §2).
- **Spring Data JPA Auditing 만으로 해결 (Clock 자체 미도입)** — `@CreatedDate`/`@LastModifiedDate` listener 가 timestamp 채움, 도메인은 timestamp 비-소유. 거부 사유: (a) 도메인이 `createdAt`/`updatedAt` 을 *불변식의 일부* 로 갖는 현재 모델과 충돌, (b) Auditing 은 *Entity-level* 의식이라 순수 도메인 객체(`Post`) 가 `null` timestamp 로 생성된 뒤 listener 가 채울 때까지 *부분 초기화 상태* — Aggregate 무결성 약화, (c) 미래 *비즈니스 의미를 가진 시간*(예약 발행, 만료, 도메인 규칙에 사용되는 `deletedAt`) 은 Auditing 으로 처리 불가 — 본 ADR 의 값 전달 패턴이 *어차피* 필요. Clock 주입(본 ADR)과 Auditing(ADR-0008 예정) 은 *경쟁* 이 아니라 *직렬* 이며 책임이 다름: Clock 은 비즈니스 시간, Auditing 은 감사용 메타데이터(§6).
- **테스트에서 시간 truncate 만 적용** — Codex 의 PR #12 P2 권고. Clock 도입 없이 어서트에서 `truncatedTo(ChronoUnit.MICROS)` 사용. 거부 사유: 증상 봉합. 시계 자체가 비결정적이면 truncate 도 *환경별 정밀도* 에 의존(JVM/OS/DB 조합) → 본질적 해결책 아님. ADR-0006 §5 가 이미 *주입을 목표* 로 결정.

## Rejected Suggestions

본 ADR 설계 과정에서 *실제로 제안되었으나 거부된* 안.

- **도메인이 `Clock` 인자를 받는 패턴** (초기 draft §2/§3) — 거부 (사용자 검토 라운드에서). 사유: 도메인이 *시계 추상화* 를 알 필요가 없음 — *값* 만 받으면 도메인 순수도와 트랜잭션 일관성 모두 향상. 채택: §2 의 *값 전달* 패턴 (Application 이 Clock 보유, `LocalDateTime.now(clock)` 한 번 캡처해 도메인에 전달). 결정 회고: 처음 Spring 관행("Clock 을 어디든 주입") 에 끌려 도메인까지 Clock 을 흘려보냈음. 도메인은 *시계 의식이 없는 자리* 가 정확.
- **Clock 도입과 Auditing 도입을 한 ADR/Plan 으로 묶기** — 거부. 사유: ADR-0006 §5 가 "테스트와 도메인 시간 정책 변경을 한 실행 단위에 섞지 않는다" 의 정신과 충돌. 두 결정은 책임이 다름 — Clock 은 *비즈니스 의미를 가진 시간 값* 의 결정성, Auditing 은 *감사용 메타데이터* 의 자동 채움 (§6). 직렬 진행: 본 ADR(Clock 만) → PLAN-0007(Clock 구현) → ADR-0008(Auditing, `DateTimeProvider`) → PLAN-0008.
- **`Clock.systemUTC()` 채택** — 거부(현 시점). 사유: `LocalDateTime` 은 timezone-naive 라 UTC 채택이 *zone 변경 의식* 을 강제로 끌어옴(저장된 값 의미 변화 + Jackson 직렬화 변화 + 사용자 표시 변화). 시간대 정책 변경은 별도 결정. *현 의미 보존* 을 위해 `systemDefaultZone()` 시작, UTC 전환은 Open Questions.
- **`@Component PostFactory` 가 Clock 을 들고 `Post.create()` 를 호출** — 거부. 사유: 도메인 패키지에 Spring 컴포넌트가 생기면 ADR-0003 §"도메인은 framework 를 모른다" 위반. ArchUnit `domain_pure` 규칙이 즉시 빨개짐. Application service 가 Clock 을 들고 도메인 factory 에 전달하는 패턴이 layer 경계와 정합.
- **도메인 인스턴스가 `Clock` 필드 보유 (`new Post(clock, ...)`)** — 거부. 사유: 도메인 모델이 *상태와 무관한 협력자* 를 들고 다니게 됨. JPA Entity 매핑 시 transient 처리 필요, equals/hashCode 의 결정 어려움, fixture/직렬화 비용 증가. *시간을 만드는 행동* 에만 Clock 이 필요하므로 *메서드 인자* 가 정확한 자리.
- **`PostFixtures.aValidPost()` 에 기본 `Clock` 을 정적으로 박기 + override 패턴** — 일부 보류. 사유: PLAN-0007 의 실행 디테일. ADR 본문에 박지 않고 Plan 에서 결정.

## Consequences

**긍정적 영향**

- 도메인 시간이 결정적 — 도메인 단위 테스트가 `LocalDateTime` 리터럴로 *시간값 자체* 를 어서트 가능. Application 테스트는 `Clock.fixed` 로. PLAN-0006-C 의 boundary 검증(`isAfterOrEqualTo`/`isBeforeOrEqualTo`) · PLAN-0006-D 의 *절대값 회피* Risks 가 *강한 어서트* 로 격상 가능.
- ADR-0008 (예정 Auditing) 의 `DateTimeProvider` 가 같은 `Clock` 빈을 의존성으로 받아 *단일 시간 출처* 보장.
- 후속 도메인(Comment 의 `createdAt`, ViewCount 의 mutation timestamp, soft delete 의 `deletedAt`) 이 *처음부터* Clock 주입 패턴 위에 작성됨 — 일관성.
- Codex 의 PR #12 P2 (timestamp precision) 가 *결정적 시간* 위에서 다시 보면 *정밀도 정규화* 가 더 깔끔히 도입 가능 (별도 작업).

**부정적 영향 / 트레이드오프**

- `Post.create(...)` / `Post.updateContent(...)` 호출 모든 사이트에 `LocalDateTime` 인자 추가 → 마이그레이션 비용 (production: Application service 2 메서드 + 테스트 ≈ 10 자리). 한 번 정착하면 끝나지만 PLAN-0007 머지 PR 의 변경 범위가 넓음.
- 픽스처 API 변경 — `PostFixtures.aValidPost()` 가 `LocalDateTime` 인자 또는 기본 상수 의식을 가져야 함. 기존 테스트의 *호출 시그니처* 변경 발생.
- Application 이 `Clock` 의존을 새로 보유 — `PostCommandService` 생성자 시그니처 변경. 기존 manual constructor 주입 단위 테스트 갱신 필요.
- 도메인은 JDK 표준만 의존(이미 `LocalDateTime` 사용 중). 추가 의존 없음. ArchUnit `domain_pure` 영향 없음.
- `Clock.systemDefaultZone()` 채택으로 UTC 전환은 *별도 작업* 으로 미뤄짐 — 시스템이 international 화 시점에 timezone ADR 와 함께 정리해야 함. 그 사이 default zone 가정 위에 작성된 코드가 누적될 위험.

## Open Questions

- **`LocalDateTime` → `Instant`/`OffsetDateTime` + UTC 전환** — 본 ADR 은 `Clock.systemDefaultZone()` 시작. 시간대 의식 변경은 별도 ADR. 트리거: international 화 또는 *서버 간 시계 불일치* 가 드러나는 시점.
- **`UUID`/random provider** — Clock 과 같은 패턴(주입 가능 추상화) 이 random 에도 필요한가? 현재 도메인에 UUID 사용 없음 — 도입 시점에 별도 ADR.
- **`DateTimeProvider` 와 Auditing** — 별도 ADR-0008 슬롯. 본 ADR 머지 후 즉시 작성. Spring Data JPA Auditing 채택 여부와 `@CreatedDate`/`@LastModifiedDate` 의 도메인-Entity 경계 결정 포함.
- **`PostFixtures` 의 시간 인자 API** — PLAN-0007 실행 디테일. 후보: (a) 모든 helper 가 `LocalDateTime` 인자 필수, (b) 기본 상수 (`PostFixtures.FIXED_NOW`) + override 가능 helper, (c) builder 패턴.
- **감사용 timestamp 의 도메인 이전 — `createdAt`/`updatedAt` 을 Post 도메인 클래스에서 빼는가** — ADR-0008 (Auditing) 슬롯의 핵심 질문. 본 ADR 은 *현재 위치 유지* (도메인 안). Auditing 도입 시 재검토 — *감사용* 으로 분류되면 Entity-level 로 이전, *비즈니스 의미* 가 있다고 판정되면 본 ADR 의 값 전달 패턴으로 유지.

## Related

- ADR-0003 (Clean/Hexagonal + DDD + CQRS) — 도메인 framework 무지 원칙. 본 ADR 의 *Clock 인자 명시* 결정 근거.
- ADR-0006 (테스트 전략) §5 — *결정성* 원칙 + Clock 주입을 *목표*로 명시한 deferred slot. 본 ADR 이 그 약속의 회수.
- ADR-0008 (예정 — Auditing + `DateTimeProvider`) — 본 ADR 의 직접 후속.
- PLAN-0007 (예정) — 본 ADR 의 구현 단위.
