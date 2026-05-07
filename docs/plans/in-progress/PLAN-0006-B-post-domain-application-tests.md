# PLAN-0006-B: Post 도메인 / Application 테스트

ADR-0006 의 두 번째 실행 단위. PLAN-0006-A 가 만든 인프라(ArchUnit 12 규칙 + `integrationTest` task) 위에서 **Post 도메인의 Domain 계층과 Application 계층 테스트**를 작성한다. Web/Persistence slice 와 E2E 는 후속 -C/-D.

## Goal

ADR-0006 §1 표의 *Domain*, *Application Service (Command)*, *Application Service (Query)* 세 행을 Post 도메인에 대해 첫 적용한다. 구체적으로:

1. `Post` aggregate 의 불변식·상태 전이·`create`/`reconstitute` 일치를 POJO 테스트로 명세화.
2. `PostContent` VO 의 동등성과 입력 검증을 POJO 테스트로 명세화.
3. `PostCommandService` 의 `create`/`update`/`delete` Use Case 를 *상태 변화* 위주로 검증 — Output Port mock + ArgumentCaptor.
4. `PostQueryService` 의 `get`/`list` Use Case 를 result DTO 매핑 위주로 검증.
5. 도메인별 fixture builder(`PostFixtures`) 도입.

본 Plan 의 모든 테스트는 ADR-0006 §4 "테스트는 문서다" 원칙을 따른다 — `@DisplayName` 한국어, 도메인 언어, 한 테스트 = 한 약속.

## Scope

- Domain 테스트 — `PostTest`, `PostContentTest`. JUnit 5 + AssertJ. **Spring·JPA·Mockito·Hibernate 일체 없음** (PLAN-0006-A 의 `TestStrategyArchitectureTest.domain_tests_are_pojo` 가 빌드 시점에 강제).
- Application 테스트 — `PostCommandServiceTest`, `PostQueryServiceTest`. CQRS 분리 (테스트 클래스 분리). JUnit 5 + Mockito + AssertJ. **`@SpringBootTest` 사용 금지** (`TestStrategyArchitectureTest.application_service_tests_no_springboottest` 가 강제).
- 픽스처 — `PostFixtures` 정적 빌더. 위치 — `src/test/java/.../domain/post/PostFixtures.java`. Domain 테스트 안의 도메인 언어이므로 도메인 패키지 미러에 둠.
- Application Command 테스트는 **상태 변화 우선** 검증 (ADR-0006 §2). ArgumentCaptor 로 저장되는 도메인을 잡아 필드·불변식 어서트. 호출 검증은 *보완* (예: not-found 일 때 `save()` 미호출).
- not-found 신호는 ADR-0003 §"CQRS Coupling Boundary" 에 따라 `delete` 의 row-count 로 검증 — 별도 load 호출하지 않는다.

## Non-goals

- Web slice 테스트 (`@WebMvcTest`) — PLAN-0006-C.
- Persistence slice 테스트 (`@DataJpaTest`) — PLAN-0006-C.
- E2E 골든 플로우 (`@SpringBootTest`) — PLAN-0006-D.
- Mapper 단독 테스트 — Persistence slice round-trip 으로 검증 가능한 자명 매핑이라 본 Plan 범위 밖. round-trip 으로 안 잡히는 정책이 발견되면 별도 작성 (-C 또는 후속).
- ArchUnit 규칙 추가 — PLAN-0006-A 가 12 규칙 모두 도입 완료.
- Clock 주입 리팩터링 — ADR-0006 §5, 별도 ADR/Plan.
- 다른 도메인(Comment 등) — 본 Plan 은 Post 한정.

## Related ADRs / Plans

- ADR-0006 (테스트 전략) — 본 Plan 의 권위. 특히 §1·§2·§4·§5·§11.1.
- ADR-0003 (Clean/Hexagonal + DDD + CQRS) — 계층 책임·CQRS 분리 출처.
- PLAN-0006-A (`done/`) — 본 Plan 의 *전제* (인프라·ArchUnit·`integrationTest` task).

## Acceptance Criteria

- `./gradlew test` 가 다음 신규 테스트 클래스를 모두 통과시킨다 — `PostTest`, `PostContentTest`, `PostCommandServiceTest`, `PostQueryServiceTest`.
- 모든 신규 테스트가 Spring 컨텍스트를 띄우지 않는다 — 실행 시간 합계가 *수 초 이내* 유지(slow context 로 회귀 시 PLAN 실패).
- ADR-0006 §11.1 의 ArgumentCaptor 패턴이 `PostCommandServiceTest.create` / `update` 에 적용되어 *저장되는 도메인의 필드 값*이 직접 어서트된다.
- not-found 케이스가 `PostCommandServiceTest.delete` 에서 `deletePort.deleteById(...)` row-count 로 검증되며, `loadPort.findById()` 는 호출되지 않는다.
- CQRS 분리 — Command 테스트와 Query 테스트가 *같은 mock setup·stubbing 을 공유하지 않는다*. `PostFixtures` 같은 순수 도메인 빌더는 공유 허용.
- 모든 신규 테스트 메서드에 한국어 `@DisplayName` 부착 (ADR-0006 §4).
- PLAN-0006-A 의 ArchUnit 12 규칙이 본 Plan 도입 후에도 모두 통과한다. 특히 `domain_tests_are_pojo`, `application_service_tests_no_springboottest`, `springboottest_is_localized` 가 신규 테스트에 적용되어 *0 위반*.

## ADR Required

no — ADR-0006 이 권위.

## Risks

1. **`Post.create` / `updateContent` 가 `LocalDateTime.now()` 를 직접 호출한다.** 정확 시각 어서트는 flaky 하다. 본 Plan 은 시간 필드의 *존재·null 아님·논리적 순서* (예: `updatedAt >= createdAt`) 만 검증한다. 정확 시각 검증은 Clock 주입 ADR/Plan 이후로 미룬다 (ADR-0006 §5).
2. **ArgumentCaptor + stub 의 함정** — `when(port.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0))` 패턴을 누락하면 stub 이 null 을 반환해 Service 가 NPE. 모든 Command 테스트에 동일 패턴 적용 필수.
3. **Mockito strict stubbing** — 사용하지 않은 stub 이 있으면 `UnnecessaryStubbingException`. `@MockitoSettings(strictness = LENIENT)` 로 해결하지 말고, 테스트 케이스를 stub 사용 단위로 쪼갠다 (한 테스트 = 한 약속, ADR-0006 §4).
4. **`TestStrategyArchitectureTest.domain_tests_are_pojo` 가 Mockito import 를 차단한다.** Domain 테스트에서 *어떠한 mock 도 쓰지 않음* — Domain 모델은 외부 의존 없는 POJO 라 mock 이 필요할 일이 없다. 만약 필요한 상황이 발견되면 *도메인 설계가 잘못된 신호* 로 받아들이고 별도 검토.
5. **Equality 정책** — ADR-0003 §"Equality Policy" 에 따라 `Post` aggregate 는 `equals`/`hashCode` 미정의 가능성. 어서트는 *필드 단위* 로 작성하고 `assertThat(post).isEqualTo(other)` 같은 식별자 비교는 사용하지 않는다. `PostContent` VO 는 `equals`/`hashCode` 정의되어 있어 값 비교 가능.
6. **Application Service 의 not-found 분기 시 `save()` 호출 검증.** Mockito 의 `verify(port, never()).save(any())` 패턴. strict stubbing 아래에서도 안전하지만, `any()` 의 raw type 경고가 날 수 있어 `any(Post.class)` 권장.

## Required Reading

- `docs/adr/0006-test-strategy.md` — §1·§2·§4·§5·§11.1.
- `docs/adr/0003-clean-architecture-ddd-hexagonal.md` — CQRS·Equality Policy.
- `docs/plans/done/PLAN-0006-A-test-infrastructure-archunit.md` — 본 Plan 의 전제.
- `.claude/skills/clean-architecture.md` — Domain Invariants, CQRS Coupling Boundary, Delete Semantics.
- `src/main/java/com/dunowljj/board/domain/post/Post.java` — 검증 대상 aggregate.
- `src/main/java/com/dunowljj/board/domain/post/PostContent.java` — 검증 대상 VO.
- `src/main/java/com/dunowljj/board/common/error/InvalidPostContentException.java` — 예외 타입.
- `src/main/java/com/dunowljj/board/application/service/PostCommandService.java` — 검증 대상 Service.
- `src/main/java/com/dunowljj/board/application/service/PostQueryService.java` — 검증 대상 Service.
- `src/main/java/com/dunowljj/board/application/port/in/` — Use Case 인터페이스 + Command record 들.
- `src/main/java/com/dunowljj/board/application/port/out/` — Output Port (Load/Save/DeletePort).
- `src/test/java/com/dunowljj/board/architecture/TestStrategyArchitectureTest.java` — 본 Plan 의 테스트가 통과해야 할 forward-defense 규칙.

## Files to Touch

신규 (테스트):

- `src/test/java/com/dunowljj/board/domain/post/PostTest.java` — aggregate 불변식·상태 전이.
- `src/test/java/com/dunowljj/board/domain/post/PostContentTest.java` — VO 동등성·입력 검증.
- `src/test/java/com/dunowljj/board/domain/post/PostFixtures.java` — 정적 빌더.
- `src/test/java/com/dunowljj/board/application/service/PostCommandServiceTest.java` — CQRS Command, ArgumentCaptor.
- `src/test/java/com/dunowljj/board/application/service/PostQueryServiceTest.java` — CQRS Query.

수정: 없음.

## Implementation Hints

### 검증 항목 윤곽 — Post / PostContent

`PostTest`:

- `create_blank_author_throws` — `InvalidPostContentException`.
- `create_sets_audit_fields_with_logical_order` — `createdAt`/`updatedAt` not-null, `updatedAt >= createdAt`.
- `reconstitute_rejects_null_id` / `null_createdAt` / `null_updatedAt` — `IllegalArgumentException`.
- `reconstitute_enforces_same_business_invariants_as_create` — author 공백 시 같은 예외.
- `updateContent_changes_title_body_and_advances_updatedAt` — 상태 전이.

`PostContentTest`:

- `blank_title_throws` / `blank_body_throws`.
- `equals_and_hashCode_are_value_based`.

### 검증 항목 윤곽 — Application

`PostCommandServiceTest`:

- `create` — ArgumentCaptor 로 저장되는 `Post` 의 title/body/author 검증.
- `update` — `loadPort.findById()` 가 기존 `Post` 반환하도록 stub, captor 로 변경된 상태 검증.
- `update_when_not_found_throws_PostNotFoundException` — `loadPort.findById()` 가 빈 `Optional`. `verify(savePort, never()).save(any(Post.class))`.
- `delete_when_row_count_is_zero_throws_PostNotFoundException` — `deletePort.deleteById()` 가 `0`. `loadPort` 호출되지 않음 검증.
- `delete_when_row_count_is_one_returns_normally`.

`PostQueryServiceTest`:

- `get_returns_PostResult` — Port → Result 매핑 필드 단위 검증.
- `get_when_not_found_throws_PostNotFoundException`.
- `list_returns_paged_result` — pagination/ordering Result 매핑.

### 픽스처 골격

```java
package com.dunowljj.board.domain.post;

public final class PostFixtures {
    private PostFixtures() {}

    public static Post aValidPost() {
        return Post.create("title", "body", "author");
    }

    public static Post aReconstitutedPost(long id) {
        var now = java.time.LocalDateTime.now();
        return Post.reconstitute(id, "title", "body", "author", now, now);
    }
}
```

### ArgumentCaptor 패턴 (ADR-0006 §11.1)

```java
@ExtendWith(MockitoExtension.class)
class PostCommandServiceTest {
    @Mock LoadPostPort loadPostPort;
    @Mock SavePostPort savePostPort;
    @Mock DeletePostPort deletePostPort;
    @InjectMocks PostCommandService sut;

    @Test
    @DisplayName("게시글을 등록하면 입력값 그대로 저장된다")
    void create_saves_post_with_expected_state() {
        var captor = ArgumentCaptor.forClass(Post.class);
        when(savePostPort.save(captor.capture()))
            .thenAnswer(inv -> inv.getArgument(0));   // ← 누락 시 NPE

        sut.create(new CreatePostUseCase.CreatePostCommand("t", "b", "a"));

        Post saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("t");
        assertThat(saved.getAuthor()).isEqualTo("a");
    }
}
```

### Given/When/Then 가시화 (ADR-0006 §4)

빈 줄 분리로 setup·행동·검증을 시각적으로 분별. 위 예시 — `var captor = ...` (given), `sut.create(...)` (when), `assertThat(...)` (then).

### 시간 필드 어서트 (Risk #1 대응)

```java
// ✗ flaky — 정확 시각
assertThat(post.getCreatedAt()).isEqualTo(LocalDateTime.now());

// ✓ 본 Plan 의 기준
assertThat(post.getCreatedAt()).isNotNull();
assertThat(post.getUpdatedAt()).isAfterOrEqualTo(post.getCreatedAt());
```

## Execution Notes

<!-- 실행 중 비자명한 결정만 시간순 append. 사소한 구현 디테일은 적지 않는다. -->
