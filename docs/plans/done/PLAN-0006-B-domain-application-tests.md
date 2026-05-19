# PLAN-0006-B: Domain · Application 테스트 작성

PLAN-0006-A 가 마련한 인프라(ArchUnit · `integrationTest` 분리 · `architecture/` 패키지) 위에서, ADR-0006 §1 표의 *상단 두 계층* — Domain 과 Application Service (Command/Query) — 의 테스트를 채운다. Spring 을 띄우지 않는 빠른 단위 테스트 영역이다. Web/Persistence 슬라이스(-C)와 E2E(-D)는 본 Plan 범위 밖.

## Goal

Post 도메인의 *현 코드*에 대해 ADR-0006 §1·§2·§4 가 요구하는 단위 테스트를 작성한다. 구체적으로:

1. `Post`·`PostContent` 의 불변식·생성/재구성/상태 전이를 POJO 테스트로 고정한다.
2. `PostCommandService` 의 Use Case 분기와 *저장 시 도메인 상태*를 ArgumentCaptor 로 검증한다 (ADR-0006 §2, §11.1).
3. `PostQueryService` 의 Use Case 분기와 Port 결과 → `PostListResult` 매핑(특히 `totalPages` 계산)을 검증한다.
4. 후속 Plan/도메인이 재사용할 `PostFixtures` 정적 빌더를 도입한다 (ADR-0006 §6, §2 의 "순수 도메인 fixture builder 공유 허용").

## Scope

신규 테스트 클래스:

- `domain/post/PostTest` — `create`/`reconstitute`/`updateContent` 의 성공·실패 경로, `validateAuthor`, `updatedAt` 갱신.
- `domain/post/PostContentTest` — title/body 불변식, `equals`/`hashCode` 동등성.
- `application/service/PostCommandServiceTest` — `create`/`update`/`delete` 분기, ArgumentCaptor 상태 검증, not-found 시 `savePort` 미호출 검증(§2 의 "호출 검증은 보완").
- `application/service/PostQueryServiceTest` — `getById` not-found, `list` 의 `totalPages` 계산(`size <= 0` 경계 포함), 빈 페이지.
- `domain/post/PostFixtures` (테스트 트리 내 production-mirror 위치, `src/test/java/.../domain/post/`) — `aValidPost()`, `aValidPost(Long id)` 정적 빌더. *순수 도메인*만 의존.

수정:

- 없음. `Post`·`PostContent`·서비스 두 개의 *기존 동작*을 그대로 고정한다.

## Non-goals

- Web 슬라이스(`@WebMvcTest`)·필터 테스트 — PLAN-0006-C.
- Persistence 슬라이스(`@DataJpaTest` + `@Import`) · Mapper 단독 테스트 — PLAN-0006-C.
- E2E (`@SpringBootTest` + MockMvc 골든 플로우) — PLAN-0006-D.
- Clock 주입 리팩터링 — 별도 ADR/Plan (ADR-0006 §5). 본 Plan 은 `LocalDateTime.now()` 직접 호출을 *전제*로 어서트한다 (§Implementation Hints 참고).
- ArchUnit 규칙 추가/수정 — PLAN-0006-A 가 결정.
- 커버리지 도구(JaCoCo 등) 도입 — ADR-0006 §7 에서 수치 목표 거부.
- `Post` 의 동등성 정책 변경 — ADR-0003 §"Equality Policy" 가 식별자 동등성을 미정의로 둠.

## Related ADRs

- ADR-0006 (테스트 전략) — 본 Plan 의 권위. 특히 §1 표(Domain·Application 행), §2(CQRS 분리·ArgumentCaptor 우선·Port 호출 검증의 위치), §4(테스트는 문서다), §5(결정성 — `LocalDateTime.now()` 전제), §6(명명·구조), §11.1(ArgumentCaptor 예시).
- ADR-0003 (Clean/Hexagonal + DDD + CQRS) — Domain·Application 의 의존성 경계. Domain 테스트는 Spring·JPA 의존 금지(§10), Application 테스트는 Output Port mock.
- ADR-0005 (예외/에러 정책) — `InvalidPostContentException`·`PostNotFoundException` 이 도메인/애플리케이션 경계에서 던져지는 형태.

## Acceptance Criteria

- `./gradlew test` 가 신규 테스트 4 클래스를 *모두 실행*하고 통과한다 (PLAN-0006-A 의 `excludeTags 'integration'` 하에서 — 신규 테스트는 모두 빠른 단위 테스트, `@Tag("integration")` 부착 금지).
- `PostCommandServiceTest` 는 `create` / `update` 의 *저장 시 도메인 상태* 를 `ArgumentCaptor` 로 잡아 필드(title/body/author/createdAt/updatedAt) 를 직접 어서트한다. `verify(savePort).save(any())` 만으로 끝나는 테스트는 작성하지 않는다 (ADR-0006 §2).
- `PostCommandServiceTest.delete` 의 not-found 분기는 `deletePort.deleteById` 가 `0` 을 돌려줄 때 `PostNotFoundException` 이 던져지고, *`savePort.save` 가 호출되지 *않음* 을 verify 한다 (ADR-0006 §2 의 "호출 검증을 *보완*으로 사용").
- `PostCommandServiceTest.update` 의 not-found 분기는 `loadPort.findById` 가 빈 Optional 을 돌려줄 때 `PostNotFoundException` 이 던져지고, `savePort.save` 가 호출되지 *않음* 을 verify 한다.
- `PostQueryServiceTest.list` 는 `totalPages` 계산을 다음 케이스로 고정한다 — (a) `size > 0` 의 일반 케이스, (b) `total % size != 0` 의 ceil-division 케이스, (c) `size <= 0` 일 때 `totalPages == 0`, (d) `total == 0` 의 빈 페이지(`PostListResult.posts()` 가 빈 리스트, `totalPages == 0`).
- 모든 테스트 클래스가 *Spring 컨텍스트를 띄우지 않는다*. Domain 테스트는 `org.springframework..`·`org.mockito..`·`jakarta.persistence..` 의존 없음(PLAN-0006-A 의 `domain_tests_are_pojo` ArchUnit 규칙 통과). Application 서비스 테스트는 `@SpringBootTest` 미부착(`application_service_tests_no_springboottest` 통과). `springboottest_is_localized` 위반 없음.
- `@DisplayName` 한국어로, *동작/규칙 문장* 형태로 작성 (ADR-0006 §4·§6). 메서드 이름은 `<동작>_<상태>` 영어, 한 테스트 = 한 어서션 그룹.
- 테스트 패키지가 운영 패키지와 1:1 미러 — `src/test/java/com/dunowljj/board/domain/post/`, `src/test/java/com/dunowljj/board/application/service/` (ADR-0006 §6).
- `PostFixtures` 는 *순수 도메인*만 의존(import 가 `com.dunowljj.board.domain..` 와 `java..` 한정). Mockito·Spring·JPA 의존 금지 — Command/Query 테스트가 공유해도 결합을 만들지 않게(ADR-0006 §2 의 "공유 금지 대상은 mutable setup, mock stubbing, 테스트 간 상태 의존성").
- `./gradlew build` 통과.

## ADR Required

no — ADR-0006 이 권위.

## Risks

1. **`LocalDateTime.now()` 의 비결정성** — `Post.create` 와 `updateContent` 가 직접 호출하므로 어서트 가능한 형태는 *범위* 뿐이다. 정확 시각 동등성 어서트 금지 — `assertThat(post.getCreatedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after)` 패턴으로 작성. 이 한계는 ADR-0006 §5 가 *수용한 trade-off* 이며, Clock 주입은 본 Plan 범위 밖.
2. **`updateContent` 의 `updatedAt > createdAt` 어서트가 nano 단위에서 깨질 수 있다.** 같은 millisecond 내 두 `now()` 호출이 동일 값을 돌려주는 환경(JDK·OS 조합)이 존재. 어서트는 `isAfterOrEqualTo(createdAt)` (등호 허용) 로 작성하고, *별도* 로 `content` 변경이 반영됐는지 검증한다.
3. **Mockito + JUnit 5 통합** — `MockitoExtension` 사용. `@InjectMocks` 의 생성자 주입 동작이 `@RequiredArgsConstructor` 가 만든 *all-args* 생성자와 매칭되는지 확인. 매칭 실패 시 수동 `new PostCommandService(loadPort, savePort, deletePort)` 패턴으로 전환 — 테스트 가독성에 더 친화적이라 *수동 생성을 우선* 채택해도 무방.
4. **`PostQueryService.list` 의 `size <= 0` 경계** — 현 구현은 `size <= 0` 일 때 `totalPages = 0` 으로 처리하지만 `loadPort.findPage(page, size)` 호출 자체는 막지 않는다. 따라서 mock stub 으로 `findPage(any, any) → empty PostPage` 를 돌려줘야 NPE 없이 `totalPages == 0` 이 검증된다. `findPage` 의 `size <= 0` 입력 시 *어댑터 동작* 은 본 Plan 범위 밖(Persistence 어댑터 책임 — PLAN-0006-C 또는 별도 fix).
5. **Fixture 의 위치** — `src/test/java/.../domain/post/PostFixtures.java` 가 `domain/post` 패키지에 있어 production `Post` 의 *package-private* 멤버에 접근 가능하지만, 현재 `Post` 는 모두 public API 만 노출. 권한 누수 방지를 위해 `PostFixtures` 는 *public API 만 사용*. (ArchUnit 의 `domain_tests_are_pojo` 가 fixture 클래스도 스캔 — Spring/Mockito import 들어가면 빨개진다.)
6. **`PostNotFoundException` 의 메시지·필드 어서트** — 메시지 포맷이 변하면 깨지므로 *클래스 타입* 만 어서트 (`assertThatThrownBy(...).isInstanceOf(PostNotFoundException.class)`). 메시지 prose 는 `@DisplayName` 가 담당.

## Required Reading

- `docs/adr/0006-test-strategy.md` — §1 표(Domain·Application·Application Query 행), §2, §4, §5, §6, §11.1. 본 Plan 권위.
- `docs/adr/0003-clean-architecture-ddd-hexagonal.md` — Domain 의 framework-neutral 정책, Application 의 Output Port mock 정책.
- `docs/adr/0005-exception-error-response-policy.md` — `InvalidPostContentException`·`PostNotFoundException` 이 본 계층에서 던져지는 형태.
- `docs/plans/done/PLAN-0006-A-test-infrastructure-archunit.md` — 본 Plan 이 의존하는 인프라(ArchUnit 규칙 11종, `excludeTags 'integration'`, `architecture/` 패키지).
- `.claude/skills/clean-architecture.md` — 레이어·Port·CQRS 경계 정책. `aValidPost` 같은 fixture 의 위치 결정과 mock stubbing 분리의 근거.
- `.claude/skills/plan-lifecycle.md` — 본 Plan 의 형식·archival 규약.
- `src/main/java/com/dunowljj/board/domain/post/Post.java` — `create`/`reconstitute`/`updateContent`/`validateAuthor` 의 *현 동작* (LocalDateTime.now 직접 호출 포함).
- `src/main/java/com/dunowljj/board/domain/post/PostContent.java` — title/body 불변식, `equals`/`hashCode`.
- `src/main/java/com/dunowljj/board/application/service/PostCommandService.java` — `create`/`update`/`delete` 분기. `delete` 가 `deleteById` 의 row-count 0 을 not-found 로 매핑하는 계약 (clean-architecture.md "CQRS Coupling Boundary").
- `src/main/java/com/dunowljj/board/application/service/PostQueryService.java` — `list` 의 `totalPages` 계산식.
- `src/main/java/com/dunowljj/board/application/port/in/CreatePostUseCase.java`, `UpdatePostUseCase.java`, `DeletePostUseCase.java`, `GetPostUseCase.java`, `ListPostsUseCase.java` — Command/Use Case 시그니처와 nested record.
- `src/main/java/com/dunowljj/board/application/port/in/result/PostListResult.java` — Query 결과 DTO.
- `src/main/java/com/dunowljj/board/application/port/out/LoadPostPort.java`, `SavePostPort.java`, `DeletePostPort.java` — mock 대상 Output Port 시그니처.
- `src/main/java/com/dunowljj/board/application/common/PostPage.java` — `findPage` 의 결과 형태(items/totalElements).
- `src/main/java/com/dunowljj/board/common/error/InvalidPostContentException.java`, `PostNotFoundException.java` — assertThatThrownBy 대상 타입.
- `src/test/java/com/dunowljj/board/architecture/TestStrategyArchitectureTest.java` — 본 Plan 이 통과시켜야 하는 forward-defense 규칙 3종(`domain_tests_are_pojo`, `application_service_tests_no_springboottest`, `springboottest_is_localized`).
- `build.gradle` — Mockito·AssertJ·JUnit 5 가 이미 testImplementation 에 있는지(spring-boot-starter-test 가 포함) 확인. 신규 의존성 추가 *불필요* — 추가 시 본 Plan 중단하고 보고.

## Files to Touch

신규:

- `src/test/java/com/dunowljj/board/domain/post/PostFixtures.java` — 정적 fixture 빌더.
- `src/test/java/com/dunowljj/board/domain/post/PostTest.java`
- `src/test/java/com/dunowljj/board/domain/post/PostContentTest.java`
- `src/test/java/com/dunowljj/board/application/service/PostCommandServiceTest.java`
- `src/test/java/com/dunowljj/board/application/service/PostQueryServiceTest.java`

수정: 없음.

## Implementation Hints

### PostFixtures (공유 빌더 — 순수 도메인만 의존)

```java
package com.dunowljj.board.domain.post;

public final class PostFixtures {
    private PostFixtures() {}

    public static Post aValidPost() {
        return Post.create("title", "body", "author");
    }

    public static Post aReconstitutedPost(Long id) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return Post.reconstitute(id, "title", "body", "author", now, now);
    }
}
```

`Post.create` 가 `LocalDateTime.now()` 를 호출하므로 fixture 도 비결정. 시간 어서트는 *호출자* 가 `before`/`after` 범위로 처리.

### PostCommandServiceTest — ArgumentCaptor 우선

ADR-0006 §11.1 패턴 그대로. `@InjectMocks` 대신 *수동 생성자* 권장 — 가독성·리팩터링 안정성.

```java
@ExtendWith(MockitoExtension.class)
class PostCommandServiceTest {
    @Mock LoadPostPort loadPostPort;
    @Mock SavePostPort savePostPort;
    @Mock DeletePostPort deletePostPort;
    PostCommandService sut;

    @BeforeEach
    void setUp() {
        sut = new PostCommandService(loadPostPort, savePostPort, deletePostPort);
    }

    @Test
    @DisplayName("게시글을 등록하면 입력값 그대로 저장 경계로 전달된다")
    void create_passes_post_with_expected_state_to_save_port() {
        var captor = ArgumentCaptor.forClass(Post.class);
        when(savePostPort.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        var before = LocalDateTime.now();
        sut.create(new CreatePostUseCase.CreatePostCommand("t", "b", "a"));
        var after = LocalDateTime.now();

        Post saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("t");
        assertThat(saved.getBody()).isEqualTo("b");
        assertThat(saved.getAuthor()).isEqualTo("a");
        assertThat(saved.getCreatedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    @DisplayName("존재하지 않는 게시글 수정은 예외를 던지고 저장하지 않는다")
    void update_throws_and_does_not_save_when_not_found() {
        when(loadPostPort.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.update(new UpdatePostUseCase.UpdatePostCommand(1L, "t", "b")))
            .isInstanceOf(PostNotFoundException.class);
        verify(savePostPort, never()).save(any());
    }
    // delete: rows == 0 → throws + savePort never called
    // delete: rows == 1 → no throw, no save
}
```

### PostQueryServiceTest — `totalPages` 경계 우선

```java
@Test
@DisplayName("size 가 0 이하이면 totalPages 는 0 이다")
void list_returns_zero_total_pages_when_size_is_non_positive() {
    when(loadPostPort.findPage(0, 0)).thenReturn(new PostPage(List.of(), 0L));
    PostListResult result = sut.list(0, 0);
    assertThat(result.totalPages()).isZero();
}

// total=10, size=3 → totalPages=4 (ceil-division)
// total=10, size=5 → totalPages=2 (exact)
// total=0, size=10 → totalPages=0
// getById not-found → throws PostNotFoundException
```

### Domain 테스트 — POJO

```java
class PostTest {
    @Test
    @DisplayName("작성자가 공백이면 게시글을 생성할 수 없다")
    void create_throws_when_author_is_blank() {
        assertThatThrownBy(() -> Post.create("t", "b", "  "))
            .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("내용을 갱신하면 updatedAt 이 createdAt 이상으로 진행한다")
    void updateContent_advances_updatedAt() {
        Post post = PostFixtures.aValidPost();
        LocalDateTime created = post.getCreatedAt();
        post.updateContent("t2", "b2");
        assertThat(post.getTitle()).isEqualTo("t2");
        assertThat(post.getBody()).isEqualTo("b2");
        assertThat(post.getUpdatedAt()).isAfterOrEqualTo(created);
    }
    // reconstitute: id null → IllegalArgumentException
    // reconstitute: createdAt/updatedAt null → IllegalArgumentException
    // reconstitute: author blank → InvalidPostContentException
}

class PostContentTest {
    // title null/blank → InvalidPostContentException
    // body null → InvalidPostContentException ("body" 빈 문자열은 허용 — 현 구현 그대로)
    // equals/hashCode: 같은 (title, body) → equal
    // equals/hashCode: 다른 title 또는 body → not equal
}
```

### 작성 한계 — 테스트하지 않는 것 (ADR-0006 §8)

- Lombok 이 만든 코드(`@RequiredArgsConstructor` 의 생성자 자체) 테스트 금지.
- `Post` 의 단순 getter 검증을 위한 *전용* 테스트 금지 — Command 테스트의 ArgumentCaptor 어서트가 간접 검증.
- `verify(port).save(any())` 만 외치는 테스트 금지(§2 — *상태 검증을 우선*, 호출 검증은 부재 증명에서만 단독 의미).

## Execution Notes

<!-- 실행 중 비자명한 결정만 시간순 append. 사소한 구현 디테일은 적지 않는다. -->

- 2026-05-08: 중복 정책 재검토. **Flow A(위임 인지: PostTest는 위임 살아있음 증거 1~2개, PostContentTest가 풀스펙)** 검토 → "테스트는 문서다" 관점에서 PostTest 가 Post.create 의 외부 계약을 부분만 노출하게 되어 독자가 PostContentTest 까지 읽어야 그림이 완성됨 → 거부. **Flow B(계약 우선: 둘 다 풀스펙, 의도된 중복)** 채택. 근거: 두 클래스가 독립 명세 단위이며 서로 다른 독자를 가짐.
- 2026-05-08: `PostContent` 의 `record` 마이그레이션 + instanceof 기반 equals 로 전환하는 안 검토 → 이상적이지만 production 코드 수정이 본 Plan Scope L26("수정: 없음") 위반 → 본 PR에서 보류. 별도 작은 PR 로 분리하기로 결정.
- 2026-05-08: equals 검증을 `EqualsVerifier.verify()` 단독으로 가는 안 검토 → "테스트는 문서다" 관점에서 동등성 *의미*(어느 필드가 동등성 기준인지)가 코드에서 안 읽힘 → 거부. **손어서트(의미 문서화) + EqualsVerifier(표준 계약 견고성) 병행** 채택. 손코드 `getClass()` 비교 때문에 `.usingGetClass()` 명시 필요 — record 마이그레이션 후 제거.
- 2026-05-08: Plan promotion(in-progress → approved) 을 별도 커밋으로 분리하는 안 검토 → 추적 안 된 untracked 드래프트라 promotion 만의 단독 커밋이 의미 없음 → 거부. promotion + 구현을 단일 커밋으로 합침.
- 2026-05-09: 기존 PR #10 (docs-only PLAN-0006-B 드래프트 PR, `docs/plan-0006-b-domain-application-tests` 브랜치) 발견. 본 세션의 단일 커밋 결정과 충돌 + 옛 파일명(`PLAN-0006-B-post-domain-application-tests.md`) 사용 → close (superseded by #11).
- 2026-05-09: PostCommandServiceTest `update` 의 author 어서트가 `"author"` 리터럴을 사용 → fixture 기본값 변경 시 무관한 깨짐 위험 → `existing.getAuthor()` 캡처 방식으로 교체. fixture 결합 제거.
- 2026-05-19: PLAN-0009 (ADR-0009) 에서 *instanceof + final 부분* 만 회수하고, *record 전환은 현재 미채택*. 사유: `final class + instanceof equals + EqualsVerifier` 로 컨벤션 목적은 충분히 달성. record 도입은 accessor 변경 (`getTitle()` / `getBody()` → `title()` / `body()`) 과 VO 표현 방식 기본값 결정까지 동반해 현 단계 이득 대비 비용이 큼. 후속 VO 가 늘어나 반복 비용이 실제로 커질 때 재검토.
