# PLAN-0007: Clock 주입 구현

ADR-0007 (시간 정책 — `Clock` 주입과 결정적 시간 의식) 의 구현 단위. 도메인의 `LocalDateTime.now()` 직접 호출 제거 → 도메인은 `LocalDateTime` *값* 인자로, Application 은 `Clock` 빈을 캡처. 테스트 시간 원천을 `PostFixtures` 단일로 통일.

## Goal

Post 도메인의 시간 정책을 ADR-0007 에 맞게 마이그레이션. 구체적으로:

1. `Post.create` / `Post.updateContent` 가 `LocalDateTime now` 를 *첫 인자* 로 받음. 도메인 ambient JVM clock (`LocalDateTime.now()` no-arg) 호출 0 건.
2. 도메인 invariant 신설 — `now` non-null + `updateContent` 의 `now` *역행 금지* (`now >= this.updatedAt`).
3. `PostCommandService` 가 `Clock` 을 필드로 주입받아 `LocalDateTime.now(clock)` 으로 캡처 후 도메인에 전달.
4. Application 컨텍스트에 `Clock` 빈 1 개 등록 (`Clock.systemDefaultZone()`).
5. `PostFixtures` 에 *시간 원천 단일* 도입 — `FIXED_CLOCK` ⇄ `FIXED_NOW` derive 관계, `fixedClockAt(LocalDateTime)` 헬퍼.
6. 모든 영향 받는 테스트가 fixture 시간 원천 사용 + `./gradlew check` 통과 + ArchUnit 11 규칙 유지.

## Scope

### Included — Production

- **`Post.java` 도메인 변경** (ADR-0007 §2/§2.1):
  - `create(LocalDateTime now, String title, String body, String author)` — `now` 첫 인자, `Objects.requireNonNull(now)`. `createdAt = updatedAt = now` 유지.
  - `updateContent(LocalDateTime now, String title, String body)` — `now` 첫 인자, non-null 강제, **`now < this.updatedAt` 이면 `IllegalArgumentException`**. content 갱신 후 `this.updatedAt = now`.
  - `reconstitute(...)` 시그니처 변경 *없음* — 영속에서 timestamp 가 *이미 정해진* 자리라 외부 시계 의식 무관.
- **`PostCommandService.java` Application 변경** (ADR-0007 §3):
  - `private final Clock clock;` 필드 추가 (`@RequiredArgsConstructor` 가 생성자 자동 생성 → Spring DI).
  - `create` 메서드: `LocalDateTime now = LocalDateTime.now(clock); Post.create(now, ...)`.
  - `update` 메서드: `LocalDateTime now = LocalDateTime.now(clock); post.updateContent(now, ...)`.
  - 한 use case 안에서 `now` 1 회 캡처 — 미래 다중 도메인 호출 시 시간 일관성 보장 패턴 선재.
- **신규 `TimeConfig.java`** (ADR-0007 §1):
  - `src/main/java/com/dunowljj/board/config/TimeConfig.java` (신규 패키지 `config`).
  - `@Configuration` + `@Bean Clock systemClock() { return Clock.systemDefaultZone(); }`.
  - 결정 사유: `BoardServiceApplication` 에 `@Bean` 부착 옵션도 가능하나 시간/Auditing/(미래 DateTimeProvider) 같은 cross-cutting 인프라가 누적될 자리를 미리 분리. ArchUnit 영향 0 (`..config..` 는 어떤 layer 규칙에도 안 걸림).

### Included — Test

- **`PostFixtures.java` 확장**:
  ```java
  private static final Clock FIXED_CLOCK = fixedClockAt(LocalDateTime.of(2026, 5, 17, 10, 0));
  public static final LocalDateTime FIXED_NOW = LocalDateTime.now(FIXED_CLOCK);

  public static Clock fixedClock() { return FIXED_CLOCK; }
  public static Clock fixedClockAt(LocalDateTime localNow) {
      ZoneId zone = ZoneId.systemDefault();
      return Clock.fixed(localNow.atZone(zone).toInstant(), zone);
  }
  ```
  - `aValidPost()` 갱신 — `Post.create(FIXED_NOW, "title", "body", "author")`.
  - `aReconstitutedPost(Long id)` 갱신 — `LocalDateTime.now()` → `FIXED_NOW` 사용.
  - `aReconstitutedPost(Long id, LocalDateTime createdAt, LocalDateTime updatedAt)` 시그니처 유지 (호출자 명시 timestamp).
- **`PostTest.java` 마이그레이션** — *모든 `LocalDateTime.now()` 호출을 `PostFixtures.FIXED_NOW` 로 통일* (ADR-0007 §4 "테스트 시간 원천 단일" 원칙):
  - `Post.create("t", "b", "a")` → `Post.create(PostFixtures.FIXED_NOW, "t", "b", "a")`. 호출 ≈ 6 자리.
  - **`create_*` boundary 어서트 강화** — line 17, 19 의 `before`/`after` 변수 제거. `isAfter(before).isBefore(after)` → `isEqualTo(PostFixtures.FIXED_NOW)`.
  - **`reconstitute_*` 예외 테스트의 `LocalDateTime now = LocalDateTime.now();` (line 83/91/99/107/115/123/131/139 ≈ 8 자리)** → `LocalDateTime now = PostFixtures.FIXED_NOW;`. 이 테스트들은 *값 자체* 가 어서트 대상이 아니라 *non-null timestamp 요구* invariant 검증 용도 — `FIXED_NOW` 로 바꿔도 검증 의미 동일하고 원천 통일성 ↑. (또는 변수 인라인화 — `Post.reconstitute(null, "t", "b", "a", PostFixtures.FIXED_NOW, PostFixtures.FIXED_NOW)`.)
  - `updateContent_replaces_content_and_advances_updatedAt` (line 146-156) — `post.updateContent(PostFixtures.FIXED_NOW.plusMinutes(1), "t2", "b2")` 같이 *명시 시점* 으로 호출. 어서트도 `isEqualTo(PostFixtures.FIXED_NOW.plusMinutes(1))` 로 강화.
  - 신규 테스트 4 개 (도메인 invariant 검증):
    - `create_throws_when_now_is_null`
    - `updateContent_throws_when_now_is_null`
    - `updateContent_throws_when_now_is_before_updatedAt` (역행 금지)
    - `updateContent_accepts_when_now_equals_updatedAt` (경계 — 같은 값 허용)
  - **PostTest 의 `LocalDateTime.now()` 호출 0 건** 이 마이그레이션 후 상태. `grep -n "LocalDateTime.now" src/test/java/com/dunowljj/board/domain/post/PostTest.java` 결과 0.
- **`PostCommandServiceTest.java` 마이그레이션**:
  - 생성자 호출 갱신 — `new PostCommandService(PostFixtures.fixedClock(), loadPostPort, savePostPort, deletePostPort)`.
  - `create_passes_post_with_expected_state_to_save_port` (line 43-60) — boundary 어서트(`isAfterOrEqualTo(before).isBeforeOrEqualTo(after)`) 를 `isEqualTo(PostFixtures.FIXED_NOW)` 로 강화. `before`/`after` 캡처 변수 제거.
  - `update_passes_mutated_post_to_save_port` (line 62-82) — Application 이 `Clock` 으로 캡처한 `FIXED_NOW` 가 `updatedAt` 으로 셋. 어서트 `isAfterOrEqualTo(createdAt)` → `isEqualTo(PostFixtures.FIXED_NOW)`. **단** `existing` fixture 의 `createdAt` 이 `FIXED_NOW` 면 역행 금지에 걸리지 않도록 *과거* 값 필요 — `aReconstitutedPost(42L, FIXED_NOW.minusDays(1), FIXED_NOW.minusDays(1))` 같은 명시.
- **`PostPersistenceAdapterTest.java` 마이그레이션** (필수, *boundary 제거 강제*):
  - line 35: `Post.create("t", "b", "a")` → `Post.create(PostFixtures.FIXED_NOW, "t", "b", "a")`.
  - **line 34, 36 의 `before`/`after` 캡처 변수 제거 필수**. 저장 시각이 `FIXED_NOW = 2026-05-17T10:00` 으로 고정되면 *테스트 실행 시각* 과 거의 항상 불일치 → 기존 boundary 어서트(line 42-43) 가 *항상 실패*. `isAfterOrEqualTo(before).isBeforeOrEqualTo(after)` → `isEqualTo(PostFixtures.FIXED_NOW)` 로 *반드시 강화*.

### Optional — 일관성 정리 (PR 에 포함 권장)

- **`PostControllerTest.java`** (4 자리: line 77/163/201/295): `LocalDateTime.now()` → `PostFixtures.FIXED_NOW`. Post 도메인 시그니처 변경과 무관(`Post.reconstitute` 호출이라) 하므로 *기능 마이그레이션 아님* — 컨벤션 정합을 위해 함께 정리 권장.

### Not Touched

- **`PostE2EIT.java`** — HTTP 계층, 직접 도메인 호출 없음. Spring 컨텍스트가 `TimeConfig` 의 `systemDefaultZone()` 빈 사용 — 행동 변화 없음. 테스트 코드 수정 0.
- **`PostQueryServiceTest.java`** — 시간 의식 없음.
- **`PostPersistenceAdapter.java`** — `Post.reconstitute` 사용 (시그니처 미변경). 변경 없음.

## Non-goals

- **Auditing 도입** (`@CreatedDate` / `@LastModifiedDate` + `DateTimeProvider`) — ADR-0008 슬롯.
- **`Clock.systemUTC()` 전환** — ADR-0007 Open Questions 의 timezone ADR 영역.
- **`Random` / `UUID` provider 추상화** — Clock 과 같은 패턴이지만 별도 ADR.
- **비즈니스 timestamp 필드 도입** (`publishedAt` / `editedAt` / `deletedAt`) — ADR-0007 §2.2 가 명시 *현 도입 결정 아님*.
- **`PostFixtures` builder 패턴 도입** — `aValidPost()` / `aReconstitutedPost()` 정적 helper 유지. builder 는 후속 도메인 추가 시 재검토.
- **`PostFixtures.aReconstitutedPost(Long)` 호출자별 timestamp 인자화** — 호출자가 명시 timestamp 가 필요하면 *2-인자 overload* 사용. 기존 1-인자 helper 는 `FIXED_NOW` 디폴트.
- **PostControllerTest / PostE2EIT 의 시간 어서트 강화** — 본 PR 의 *기능 마이그레이션 영역 아님*. 강화는 별도 정리 PR.
- **운영 코드에서 `LocalDateTime.now()` 의 *모든* 직접 호출 제거** — 본 PR 은 *도메인 한정*. `GlobalExceptionHandler` 의 `LocalDateTime.now()` (ProblemDetail timestamp) 는 별도 ADR/Plan (시간 정책의 *Application 외 계층* 확장).

## Related ADRs

- **ADR-0007** (Clock 주입 정책) — 본 Plan 권위. 특히 §1 (Clock 빈), §2/§2.1 (도메인 API + invariant), §3 (Application 캡처), §4 (테스트 fixture 단일 원천), §5 (시간 인자 명시 원칙).
- **ADR-0006** (테스트 전략) §5 — *결정성* 원칙 + Clock 주입 deferred slot 회수. §1 표의 Domain/Application/Persistence/Web/E2E 테스트가 fixture 시간 원천 공유로 결정성 향상.
- **ADR-0003** (Clean/Hexagonal + DDD + CQRS) — 도메인 framework 무지. `Clock` 흐름이 Application 까지만, 도메인은 JDK `LocalDateTime` 값만.

## Files to Inspect

- `docs/adr/0007-clock-injection-policy.md` — 본 Plan 권위.
- `docs/adr/0006-test-strategy.md` §5 — 결정성 원칙, §11.1 ArgumentCaptor 패턴 (PostCommandServiceTest 가 따를).
- `docs/adr/0003-clean-architecture-ddd-hexagonal.md` — 도메인 의존 규칙.
- `docs/plans/done/PLAN-0006-B-domain-application-tests.md` — 도메인/Application 테스트 컨벤션, `PostFixtures` 의도.
- `docs/plans/done/PLAN-0006-C-web-persistence-tests.md` — Web/Persistence slice 컨벤션.
- `docs/plans/done/PLAN-0006-D-e2e-golden-flow-tests.md` — E2E 어셈블리 (Clock 빈 등록이 어셈블리에 미치는 영향 확인).
- `.claude/skills/plan-lifecycle.md` — Plan 형식 / archival.
- `.claude/skills/clean-architecture.md` — layer 경계.
- `CLAUDE.md` — §3/§4/§5 (ADR / Plan / Pipeline).
- `src/main/java/com/dunowljj/board/domain/post/Post.java` — *변경 대상*. 현재 `LocalDateTime.now()` 직접 호출 2 자리 (line 26, 54).
- `src/main/java/com/dunowljj/board/domain/post/PostContent.java` — title/body 검증. 변경 없음 (참고).
- `src/main/java/com/dunowljj/board/application/service/PostCommandService.java` — *변경 대상*. `@RequiredArgsConstructor` 라 Clock 필드 추가만으로 생성자 자동 갱신.
- `src/main/java/com/dunowljj/board/application/service/PostQueryService.java` — 시간 의식 없음. 변경 없음.
- `src/main/java/com/dunowljj/board/BoardServiceApplication.java` — `@SpringBootApplication`. `TimeConfig` 가 컴포넌트 스캔 범위에 포함되는지 확인 (root package `com.dunowljj.board` 하위라 자동 포함).
- `src/main/resources/application.yml` — 변경 없음 (Java config 채택).
- `src/test/java/com/dunowljj/board/domain/post/PostFixtures.java` — *변경 대상*.
- `src/test/java/com/dunowljj/board/domain/post/PostTest.java` — *변경 대상*. `Post.create` / `updateContent` 호출 자리 + 신규 invariant 테스트.
- `src/test/java/com/dunowljj/board/domain/post/PostContentTest.java` — 시간 무관. 변경 없음 (참고).
- `src/test/java/com/dunowljj/board/application/service/PostCommandServiceTest.java` — *변경 대상*. 생성자 호출 + 시간 어서트.
- `src/test/java/com/dunowljj/board/application/service/PostQueryServiceTest.java` — 시간 의식 없음. 변경 없음.
- `src/test/java/com/dunowljj/board/adapter/out/persistence/post/PostPersistenceAdapterTest.java` — *변경 대상* (1 자리, line 35).
- `src/test/java/com/dunowljj/board/adapter/in/web/PostControllerTest.java` — *선택 정리* (4 자리, `LocalDateTime.now()` → `FIXED_NOW`).
- `src/test/java/com/dunowljj/board/e2e/PostE2EIT.java` — 변경 없음.
- `src/test/java/com/dunowljj/board/architecture/HexagonalArchitectureTest.java` — *통과 유지* (변경 없음 — Clock 은 JDK).
- `src/test/java/com/dunowljj/board/architecture/TestStrategyArchitectureTest.java` — *통과 유지*.
- `build.gradle` — 변경 없음 (신규 의존성 0).

## Files to Touch

신규:
- `src/main/java/com/dunowljj/board/config/TimeConfig.java` — `@Configuration` + `@Bean Clock systemClock()`.

수정:
- `src/main/java/com/dunowljj/board/domain/post/Post.java`
- `src/main/java/com/dunowljj/board/application/service/PostCommandService.java`
- `src/test/java/com/dunowljj/board/domain/post/PostFixtures.java`
- `src/test/java/com/dunowljj/board/domain/post/PostTest.java`
- `src/test/java/com/dunowljj/board/application/service/PostCommandServiceTest.java`
- `src/test/java/com/dunowljj/board/adapter/out/persistence/post/PostPersistenceAdapterTest.java`

선택 수정 (PR 에 포함 권장):
- `src/test/java/com/dunowljj/board/adapter/in/web/PostControllerTest.java`

## Implementation Steps

순서는 *의존성* 기준의 *작업 흐름 분해*. 각 Step 은 *atomic commit* 이 아님 — 본 PR 은 *단일 commit* 으로 머지 (선례 PLAN-0006-C/D). **Step 2 (`Post.java` 시그니처 변경) 후부터 Step 8 완료 전까지는 컴파일 깨진 상태** — 호출자 마이그레이션 진행 중. Step 10 의 `./gradlew check` 가 *최종 통과 시점*.

1. **`PostFixtures` 확장 (단, `aValidPost()` 미변경)** — `FIXED_CLOCK` / `FIXED_NOW` / `fixedClock()` / `fixedClockAt(LocalDateTime)` 추가. `aReconstitutedPost(Long)` 내부의 `LocalDateTime.now()` → `FIXED_NOW`. **`aValidPost()` 는 이 시점 *변경 금지*** — 아직 `Post.create(LocalDateTime, ...)` 시그니처가 없어 컴파일 깨짐. Step 3 에서 처리. 이 단계 후 컴파일 통과.
2. **`Post.java` 시그니처 변경 + invariant** — `create(LocalDateTime now, ...)` + `updateContent(LocalDateTime now, ...)`. `Objects.requireNonNull(now)` + `updateContent` 의 역행 금지 (`now < this.updatedAt` → `IllegalArgumentException`). 이 단계 후 *컴파일 깨짐* — 호출자들 (`PostFixtures.aValidPost`, `PostCommandService`, 테스트들) 모두 시그니처 불일치. 다음 Steps 3-4 가 복구.
3. **`PostFixtures.aValidPost()` 업데이트** — `Post.create(FIXED_NOW, "title", "body", "author")` 로 변경. PostFixtures 자체 컴파일 복구.
4. **`PostCommandService` 변경** — `private final Clock clock;` 필드 *첫 번째* 로 추가 (`@RequiredArgsConstructor` 가 생성자 자동 갱신, 인자 순서 — clock/loadPort/savePort/deletePort). `create` / `update` 에서 `LocalDateTime now = LocalDateTime.now(clock)` 캡처 후 도메인 호출. 운영 코드 컴파일 복구.
5. **`TimeConfig` 신규** — `src/main/java/com/dunowljj/board/config/TimeConfig.java`. `@Configuration` + `@Bean Clock systemClock() { return Clock.systemDefaultZone(); }`. 런타임 빈 등록 보장.
6. **`PostTest` 마이그레이션** — `Post.create(...)` / `updateContent(...)` 호출 자리에 `FIXED_NOW` 명시. *모든* `LocalDateTime.now()` 호출 (≈ 8 자리 reconstitute 예외 테스트 포함) → `PostFixtures.FIXED_NOW`. 어서트 강화 (boundary → 절대값). 신규 invariant 테스트 4 개 추가 (now non-null × 2, 역행 금지, 같은 값 허용).
7. **`PostCommandServiceTest` 마이그레이션** — 생성자에 `PostFixtures.fixedClock()` *첫 인자* 로 추가. 시간 어서트 강화 (`isEqualTo(FIXED_NOW)`). `update_passes_mutated_post_to_save_port` 의 `existing` fixture timestamp 를 *과거*로 (`FIXED_NOW.minusDays(1)`) 설정해 역행 금지 invariant 통과 + advances 의미 보존.
8. **`PostPersistenceAdapterTest` 마이그레이션** — line 35 `Post.create` 갱신 + line 34/36 의 `before`/`after` 캡처 제거 + line 42-43 어서트를 `isEqualTo(PostFixtures.FIXED_NOW)` 로 강화 (필수, P1 ).
9. **(선택) `PostControllerTest` 정리** — 4 자리 `LocalDateTime.now()` → `PostFixtures.FIXED_NOW`.
10. **`./gradlew check` 통과 확인** — `test` (Domain/Application/Web slice/Persistence slice/ArchUnit) + `integrationTest` (E2E + smoke) 전부 green. ArchUnit 11 규칙 통과 유지.

## Acceptance Criteria

- 운영 코드에서 *도메인의* ambient JVM clock 호출 0 건 — `grep -rn "LocalDateTime.now()" src/main/java/com/dunowljj/board/domain` 결과 0.
- `Post.create` 시그니처: `public static Post create(LocalDateTime now, String title, String body, String author)`. `now` non-null 검증 포함.
- `Post.updateContent` 시그니처: `public void updateContent(LocalDateTime now, String title, String body)`. `now` non-null + 역행 금지 검증 포함.
- `Post.updateContent` 의 검증 순서 — `Objects.requireNonNull(now)` → 역행 금지 (`now >= this.updatedAt`) → content 검증 (`PostContent` 생성자가 title/body invariant 강제). **모든 검증 통과 후에만 mutation 적용** — 어느 검증이라도 실패하면 `this.content` / `this.updatedAt` 상태 변경 없음. 신규 invariant 테스트가 *실패 후 도메인 불변* 도 어서트 (예: `assertThat(post.getUpdatedAt()).isEqualTo(originalUpdatedAt)`).
- `Post.reconstitute` 시그니처 *불변* — 영속에서 timestamp 가 이미 정해진 자리.
- `PostCommandService` 가 `Clock` 필드 보유. `create` / `update` 가 `LocalDateTime.now(clock)` 으로 한 번 캡처 후 도메인 호출.
- Application 컨텍스트에 `Clock` 타입 빈 정확히 1 개 (`Clock.systemDefaultZone()`).
- `PostFixtures` 에 `FIXED_CLOCK` (private), `FIXED_NOW` (public static), `fixedClock()` (public static getter), `fixedClockAt(LocalDateTime)` (public static helper) 존재. `FIXED_CLOCK` 과 `FIXED_NOW` 가 *derive 관계* — `FIXED_NOW == LocalDateTime.now(FIXED_CLOCK)`.
- `PostTest` 가 도메인 invariant 4 종 모두 검증 — now non-null (create/update 각 1), updateContent 역행 금지, 경계(같은 값 허용).
- `PostCommandServiceTest` 의 시간 어서트가 *boundary* (`isAfterOrEqualTo`/`isBeforeOrEqualTo`) 가 아닌 *절대값* (`isEqualTo(PostFixtures.FIXED_NOW)`) 사용. `before`/`after` 변수 제거.
- `./gradlew check` BUILD SUCCESSFUL — `test` + `integrationTest` 모두 green.
- ArchUnit 11 규칙 통과 유지. 신규 규칙 추가 *없음*.
- 신규 의존성 추가 *없음*.
- `@DisplayName` 한국어, 동작/규칙 문장 형태 (ADR-0006 §4).
- 한 테스트 = 한 어서션 그룹 (ADR-0006 §4) — 신규 invariant 테스트가 한 invariant 만 검증.

## ADR Required

no — ADR-0007 이 권위. 본 Plan 은 §1·§2·§2.1·§3·§4·§5 의 *직접 구현*. 새 시스템 결정 없음. 다음 두 가지는 ADR-0007 본문 결정 — *적용*만 함:
- `Clock` 빈 구현 = `Clock.systemDefaultZone()` (§1).
- 도메인 invariant: `now` non-null + `updateContent` 역행 금지 (§2.1).

선택 사항인 *`Clock` 빈 위치*(별도 `TimeConfig` vs `BoardServiceApplication` 부착) 는 ADR-0007 §1 이 "둘 다 가능" 으로 열어둠 — 본 Plan 이 `TimeConfig` 채택을 사유와 함께 적용. ADR 격상 불요 (다른 도메인이 추가될 때 같은 자리가 재사용되면 그 시점에 ADR 검토).

## Risks

1. **역행 금지 invariant 가 기존 테스트의 boundary 어서트와 마찰.** PostTest `updateContent_replaces_content_and_advances_updatedAt` (line 146-156) 의 현 어서트 `isAfterOrEqualTo(created)` 는 *같은 값* 도 허용. 본 Plan 의 *같은 값 허용* (`now == this.updatedAt`) 결정과 정합. 마이그레이션 후 명시 시점(`FIXED_NOW.plusMinutes(1)`) 으로 호출하면 `isEqualTo` 어서트로 강화 가능 — *기능 회귀 0*.

2. **PostCommandServiceTest 의 `update` 테스트 fixture 충돌.** `existing` 이 `aReconstitutedPost(42L)` 로 `FIXED_NOW` 를 createdAt/updatedAt 으로 보유. Application 이 캡처하는 `now` 도 `FIXED_NOW` 라 `updateContent` 의 역행 금지 — *같은 값* 이라 통과. 그러나 *advances* 의미를 어서트하려면 `existing` 의 timestamp 를 *과거*로 명시 — `aReconstitutedPost(42L, FIXED_NOW.minusDays(1), FIXED_NOW.minusDays(1))` 호출 패턴. 추가 호출 시그니처 이미 존재 (2-인자 overload).

3. **`TimeConfig` 의 컴포넌트 스캔 범위.** `BoardServiceApplication` 이 `com.dunowljj.board` 루트에 있어 `..config..` 자동 포함. `@SpringBootApplication` 의 `@ComponentScan` 기본 동작. 확인 완료.

4. **ArchUnit `application_spring_narrow` 규칙 트립 위험.** 본 규칙은 `..application..` 가 `org.springframework.stereotype`/`transaction` + `jakarta.transaction` 외 Spring 의존 금지. `PostCommandService` 가 새로 `java.time.Clock` 필드 보유 — JDK 표준이라 영향 없음. `@Bean` 도 application 패키지 *밖* 의 `..config..` 에 있어 무관. ArchUnit 통과 확인 필요 (Implementer 가 step 10 에서 확인).

5. **`@RequiredArgsConstructor` + `Clock` 필드 — 생성자 인자 순서 변경.** Lombok 이 *필드 선언 순서* 로 생성자 인자 생성. `Clock` 을 *어느 위치* 에 두느냐가 PostCommandServiceTest 의 `new PostCommandService(...)` 인자 순서를 결정. **결정**: `clock` 을 *첫 번째 필드* — 의존성의 *외부 시간 의식* 이 다른 의존(LoadPort/SavePort/DeletePort) 보다 더 일반적이라 위치 우선순위 자연. 테스트 호출도 `new PostCommandService(fixedClock, loadPort, savePort, deletePort)` 로 일관.

6. **`Post.reconstitute` 가 변경되지 않음에 따른 일관성 의문.** *영속에서 도메인으로 복원* 하는 자리라 외부 시간 의식 무관 — timestamp 가 이미 *고정 값*. 시그니처 유지가 ADR-0007 §1 의 "시간을 *만드는* 자리만 인자 추가" 정신과 정합. PostFixtures.aReconstitutedPost 도 같은 의식 — *기본 `FIXED_NOW`* 사용은 호출자 편의이고 *invariant 결정 아님*.

7. **PostControllerTest 의 4 자리 `LocalDateTime.now()` 가 선택 사항인 이유.** 본 Plan 의 *기능 마이그레이션* 영역 아님 — `Post.reconstitute` 호출 (Clock 시그니처 변경과 무관). 일관성 정합을 위해 포함 권장이지만 *제외해도 Plan 통과*. PR 검토 부담 줄이려면 제외, 일관성 우선이면 포함.

8. **운영 코드 다른 `LocalDateTime.now()` 호출 잔존.** `GlobalExceptionHandler.enrich(...)` 가 `LocalDateTime.now()` 호출 (ProblemDetail timestamp). 본 Plan *범위 밖* — 도메인 한정 마이그레이션. 후속 ADR/Plan 에서 *Application 외 계층* 으로 시간 정책 확장 시 함께 정리. Risks 기록만, 본 PR 미수정.

### Pre-resolved

- **`Clock.systemDefaultZone()` 채택** — ADR-0007 §1 결정. UTC 검토 거부.
- **`TimeConfig` 신규 패키지 채택** — `BoardServiceApplication` 부착 옵션 vs 별도 config. 별도 채택 사유: 미래 Auditing/DateTimeProvider 같은 cross-cutting 인프라가 누적될 자리 분리.
- **도메인 invariant: 역행 금지** — ADR-0007 §2.1 결정.
- **테스트 시간 원천 단일 fixture** — ADR-0007 §4 결정.
