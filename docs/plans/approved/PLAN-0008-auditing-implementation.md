# PLAN-0008: Auditing 구현 + 도메인 timestamp 제거

ADR-0008 (감사 데이터 정책) 의 구현 단위. PLAN-0007 의 도메인 시간 인자 결정을 *dormant* 로 전환하면서 audit 책임을 Entity-level listener 로 이전. Post 도메인이 audit metadata 의 *존재 자체를 모름*. `SavePostPort` 제거 + `CreatePostPort`/`UpdatePostPort` 분리.

## Goal

ADR-0008 §1–§8 + 하위 절(§3.1/§4.1/§5.1/§8.1) 의 *직접 구현*. 구체적으로:

1. **JPA Auditing 활성화** — `TimeConfig` 에 `@EnableJpaAuditing` + `DateTimeProvider` 빈. 기존 `Clock` 빈 재사용 (진실원 단일).
2. **`Post` 도메인 timestamp 완전 제거** — `createdAt`/`updatedAt` 필드/getter/메서드 인자 모두 삭제. PLAN-0007 의 invariant 4 종 삭제.
3. **`PostJpaEntity` audit listener 부착** — `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate` + `@LastModifiedDate`. update 경로의 *부분 변경* 을 위한 `update(String title, String body)` 메서드 도입.
4. **`SavePostPort` 제거 → `CreatePostPort` + `UpdatePostPort` 분리**. `UpdatePostPort` 구현 = *load-mutate-save + flush 보장* (수단: `saveAndFlush`).
5. **신규 record** — `AuditedPost` (`port/out/result/`), `AuditedPostResult` (`port/in/result/`). `port.in.result` 가 `port.out.result` 미의존 (§5.1).
6. **use case 반환 타입 변경** — `Post` → `AuditedPostResult`. `PostController` 까지 영향.
7. **production 정렬 tie-breaker** — `findAllByOrderByCreatedAtDescIdDesc`.
8. **테스트 인프라** — `MutableClock` + `TestAuditConfig` (`@TestConfiguration`, `@Primary MutableClock` 초기값 `FIXED_NOW`). `@DataJpaTest` slice 에 명시 import.
9. 모든 테스트 마이그레이션. `./gradlew check` 통과 + ArchUnit 11 규칙 유지.

## Scope

### Included — Production

**신규 파일 (4)**:
- `src/main/java/com/dunowljj/board/application/port/out/result/AuditedPost.java` — `record AuditedPost(Post post, LocalDateTime createdAt, LocalDateTime updatedAt) {}`
- `src/main/java/com/dunowljj/board/application/port/in/result/AuditedPostResult.java` — record. *`AuditedPost` 를 import 하지 않음* (§5.1). 헬퍼 시그니처 (선택): `from(Post post, LocalDateTime createdAt, LocalDateTime updatedAt)`.
- `src/main/java/com/dunowljj/board/application/port/out/CreatePostPort.java` — `AuditedPost create(Post post);` (post.id == null 보장).
- `src/main/java/com/dunowljj/board/application/port/out/UpdatePostPort.java` — `AuditedPost update(Post post);` (post.id != null 보장).

> **메서드명 결정** — `save(Post)` 가 아닌 `create(Post)` / `update(Post)`. 사유: 두 port 가 같은 시그니처면 단일 adapter 가 동시 구현할 때 *Java 컴파일 자체 불가* (동일 메서드 하나만 가능). ADR-0008 §4 의 *단일 adapter 4 port 응집* 결정과 *시그니처 충돌* 양립 가능성은 메서드명 분리. 메서드명도 *port 의도* (create / update) 와 자연 정합.

**삭제 파일 (1)**:
- `src/main/java/com/dunowljj/board/application/port/out/SavePostPort.java`.

**수정 파일 (15)**:
- **`Post.java`** — `createdAt`/`updatedAt` 필드 + getter 삭제. `create(String, String, String)` / `updateContent(String, String)` / `reconstitute(Long, String, String, String)` 시그니처 변경 — timestamp 인자 제거. PLAN-0007 의 invariant 4 종 (now non-null × 2, 역행 금지, 경계) 제거.
- **`PostJpaEntity.java`** — `@EntityListeners(AuditingEntityListener.class)` 부착. `@CreatedDate` (이미 `updatable=false`) + `@LastModifiedDate`. 신규 메서드 `update(String title, String body)` — *load-mutate-save* 경로의 부분 변경 책임. setter 우후죽순 노출 회피 — entity 가 *자기 변경 책임* 보유.
- **`PostJpaRepository.java`** — `findAllByOrderByCreatedAtDesc` → `findAllByOrderByCreatedAtDescIdDesc`. tie-breaker (Consequences 결정).
- **`PostMapper.java`** — `toDomain(PostJpaEntity)` 가 `AuditedPost` 반환 (`Post.reconstitute(...)` 호출 + entity 의 timestamp 합성). `toEntity(Post)` 는 *신규 entity 생성* 전용 — timestamp 인자 없이 `new PostJpaEntity(null, title, body, author)` (listener 가 채울 자리, 생성자 시그니처 단순화).
- **`PostPersistenceAdapter.java`** — `SavePostPort` implements 제거. `CreatePostPort` + `UpdatePostPort` 동시 implements. `LoadPostPort.findById/findPage` 반환 타입 갱신.
  - `CreatePostPort.create`: `PostMapper.toEntity(post)` + `repository.save(entity)` (persist) + `AuditedPost` 합성.
  - `UpdatePostPort.update`: *load-mutate-save* — `repository.findById` → `existing.update(title, body)` → **`repository.saveAndFlush(existing)`** (§4.1 flush 보장).
- **`LoadPostPort.java`** — `Optional<Post>` → `Optional<AuditedPost>`. `PostPage` → `AuditedPostPage` (또는 `PostPage` 의 items 타입 변경 — 아래 결정).
- **`PostPage.java`** (`application/common/`) — `List<Post>` → `List<AuditedPost>` 로 *items 타입만* 변경. 클래스명 유지 (record 이름 변경 비용 회피).
- **`CreatePostUseCase.java`** — `Post create(...)` → `AuditedPostResult create(...)`.
- **`GetPostUseCase.java`** — `Post getById(...)` → `AuditedPostResult getById(...)`.
- **`UpdatePostUseCase.java`** — `Post update(...)` → `AuditedPostResult update(...)`.
- **`ListPostsUseCase.java`** — 반환 타입 그대로 (`PostListResult`). `PostListResult` 내부 items 타입 변경.
- **`PostListResult.java`** — `List<Post>` → `List<AuditedPostResult>`.
- **`PostCommandService.java`** — `Clock` 필드 *제거* (ADR-0007 dormant). `SavePostPort` 의존 *제거*, `CreatePostPort` + `UpdatePostPort` 의존 추가. `create()` 가 `Post.create(title, body, author)` → `createPostPort.create(post)` → `AuditedPostResult` 변환. `update()` 가 `loadPostPort.findById(id)` → `AuditedPost.post()` 의 Post 그대로 사용 → `post.updateContent(title, body)` → `updatePostPort.update(post)` → `AuditedPostResult` 변환.
- **`PostQueryService.java`** — `getById` / `list` 가 `AuditedPostResult` 반환하도록 갱신. `LoadPostPort` 반환 `AuditedPost` 를 `AuditedPostResult` 로 변환 (service 의 합성 책임, §5.1).
- **`PostController.java`** — use case 반환 타입 변경에 따라 `PostResponse.from(...)` 호출 시그니처 갱신. body 매핑 무영향.
- **`PostResponse.java`** — `from(Post)` → `from(AuditedPostResult)`. 필드 그대로.
- **`PostListResponse.java`** — items 매핑이 `result.posts().stream().map(PostResponse::from)` 형태 유지. `PostResponse::from` 의 *시그니처* 가 `from(AuditedPostResult)` 로 변경되므로 매핑 호출 자체는 그대로, 인자 타입만 변경 — `List<AuditedPostResult>` 를 stream 으로 받아 `PostResponse` list 생성.
- **`TimeConfig.java`** — `@EnableJpaAuditing(dateTimeProviderRef = "auditDateTimeProvider")` 부착. 신규 `@Bean DateTimeProvider auditDateTimeProvider(Clock clock)` 가 `() -> Optional.of(LocalDateTime.now(clock))` 반환. `Clock` 빈 결정 그대로.

### Included — Test 인프라 신규

- `src/test/java/com/dunowljj/board/config/MutableClock.java` — `Clock` 확장. `setTo(LocalDateTime)` + `advance(Duration)` 노출. 초기값 생성자 또는 정적 팩토리 `startingAt(LocalDateTime)`. 단일 thread 가정 (테스트 환경) — 동시성 안전 *미보장* 명시 javadoc.
- `src/test/java/com/dunowljj/board/config/TestAuditConfig.java` — `@TestConfiguration`. `@Bean @Primary MutableClock auditClock()` 가 `MutableClock.startingAt(PostFixtures.FIXED_NOW)` 반환. `@Primary` 가 production `Clock.systemDefaultZone()` 빈을 override.

### Included — Test 마이그레이션

- **`PostFixtures.java`**:
  - `FIXED_CLOCK` / `FIXED_NOW` / `fixedClock()` / `fixedClockAt()` — *유지* (ADR-0008 §8).
  - `aValidPost()` — `Post.create("title", "body", "author")` 로 시그니처 단축.
  - `aReconstitutedPost(Long id)` — `Post.reconstitute(id, "title", "body", "author")` 로 timestamp 인자 제거. *FIXED_NOW 디폴트 의미는 도메인 측에서 사라지므로 helper 의미도 단순 fixture 로*.
  - `aReconstitutedPost(Long id, LocalDateTime createdAt, LocalDateTime updatedAt)` — *제거*. 더 이상 도메인이 timestamp 받지 않음. 호출자가 *audit fixture* 필요하면 `AuditedPost(post, createdAt, updatedAt)` 직접 합성.
  - 신규 helper (선택): `anAuditedPost(Long id, LocalDateTime createdAt, LocalDateTime updatedAt)` — `Post.reconstitute(...)` + audit 합성. PLAN-0008 실행 디테일.
- **`PostTest.java`** — 시그니처 변경 호출 자리 갱신. **PLAN-0007 의 신규 invariant 4 종 (now non-null × 2, 역행 금지, 경계) 모두 삭제** — audit 영역으로 이전 (§3). `updateContent_throws_when_title_is_null` 같은 *content invariant* 는 유지.
- **`PostCommandServiceTest.java`** — `Clock` 인자 제거. `CreatePostPort` + `UpdatePostPort` mock 으로 교체 (기존 `SavePostPort` 대신). mock 반환 `AuditedPost` 합성. service 결과 `AuditedPostResult` 어서트.
- **`PostQueryServiceTest.java`** — `LoadPostPort` mock 반환을 `Optional<AuditedPost>` / `PostPage<AuditedPost>` 로 갱신. service 결과 `AuditedPostResult` 어서트.
- **`PostPersistenceAdapterTest.java`** — `@Import({PostPersistenceAdapter.class, TimeConfig.class, TestAuditConfig.class})`. 기본 어서트 — listener 가 채운 timestamp == `FIXED_NOW` (MutableClock 초기값).
  - **기존 fixture 마이그레이션** — `new PostJpaEntity(null, "old", "b", "a", older, older)` 같은 *timestamp 직접 셋* 방식이 PostJpaEntity 생성자 시그니처 변경으로 *깨짐*. 대안: `MutableClock.setTo(...)` + `createPostPort.create(Post.create(...))` 또는 `repository.save(new PostJpaEntity(null, title, body, author))` 패턴. 각 INSERT 전에 `mutableClock.setTo(시점)` 호출 → audit listener 가 *그 시점의 Clock 값* 으로 createdAt 채움 → 정렬/페이징 setup 의 *시점 명시성* 유지. PLAN-0006-C 의 "직접 entity timestamp 셋" 결정이 audit 도입과 함께 *Clock setTo* 로 자연 마이그레이션.
    ```java
    mutableClock.setTo(LocalDateTime.of(2026, 1, 1, 0, 0));
    createPostPort.create(Post.create("old", "b", "a"));
    mutableClock.setTo(LocalDateTime.of(2026, 1, 2, 0, 0));
    createPostPort.create(Post.create("mid", "b", "a"));
    mutableClock.setTo(LocalDateTime.of(2026, 1, 3, 0, 0));
    createPostPort.create(Post.create("new", "b", "a"));
    ```
  - 신규 어서트 2 케이스 (§8.1):
    - `update_with_same_content_keeps_updatedAt` — `mutableClock.advance(...)` → 동일 title/body update → `updatedAt == FIXED_NOW` (불변, no-op).
    - `update_with_different_content_advances_updatedAt` — `mutableClock.advance(...)` → 다른 title/body update → `updatedAt == 새 시점`.
- **`PostControllerTest.java`** — `@MockitoBean` 으로 mock 한 use case 반환 타입 `AuditedPostResult` 로 변경. response body 매핑은 그대로 (`PostResponse` 필드 동일).
- **`PostE2EIT.java`** — 어셈블리 한정. response body 의 `createdAt`/`updatedAt` 어서트 *변경 없음* (notNullValue 또는 약한 패턴 유지). audit 동작 자체는 slice 가 검증.

### Not Touched

- ArchUnit 규칙 (`HexagonalArchitectureTest`, `TestStrategyArchitectureTest`) — 통과 유지. 신규 규칙 추가 없음.
- `GlobalExceptionHandler.enrich(...)` 의 `LocalDateTime.now()` 잔존 — ADR-0008 범위 밖. 후속 ADR/Plan.
- `BoardServiceApplication.java` — `@SpringBootApplication` 자체. `TimeConfig` 의 `@EnableJpaAuditing` 이 *boot 단계*에 활성화.
- `application.yml` — 변경 없음.
- `build.gradle` — 신규 의존성 0. `spring-boot-starter-data-jpa` 가 이미 Auditing 지원.

## Non-goals

- **`deletedAt` (soft delete)** — 별도 ADR/Plan.
- **`editedAt` / `publishedAt`** — 비즈니스 timestamp 재도입 시 별도 ADR (ADR-0007 dormant 재활성화).
- **`@CreatedBy` / `@LastModifiedBy`** — Spring Security ADR 슬롯.
- **equals 통일 (instanceof pattern matching)** — 별도 ADR.
- **`Audited<T>` 일반화 record** — 두 번째 도메인 추가 시점에 재검토.
- **`GlobalExceptionHandler` 의 `LocalDateTime.now()` 제거** — ADR-0008 범위 밖.
- **Testcontainers 전환** — ADR-0006 §2 의 별도 Plan.
- **`PostPage` → `AuditedPostPage` 리네임** — record 명 변경 비용 회피. items 타입만 갱신.
- **`Post.reconstitute(...)` 의 audit 인자 *부분 유지***— ADR-0008 §3 가 *완전 제거* 결정. 인자 일부 유지 대안 거부.

## Related ADRs

- **ADR-0008** (감사 데이터 정책) — 본 Plan 권위. §1–§8 + §3.1/§4.1/§5.1/§8.1 직접 구현.
- **ADR-0007** (Clock 주입 정책) — *dormant* 상태로 전환. `Clock` 빈 (§1) + `PostFixtures.fixedClock()`/`fixedClockAt()` (§8) *그대로 사용*. 도메인 시간 인자 결정 (§2/§3) 의 적용 대상 0.
- **ADR-0006** (테스트 전략) §5 — 결정성 + slice 책임. `@DataJpaTest` slice 가 audit 빈 명시 import 정합.
- **ADR-0003** (Clean/Hexagonal + DDD + CQRS) — 도메인 framework 무지. `AuditedPost` / `AuditedPostResult` 가 application layer 타입이라 도메인 경계 유지. `CreatePostPort` + `UpdatePostPort` 분리가 CQRS 정신 정합.

## Files to Inspect

- `docs/adr/0008-auditing-policy.md` — 본 Plan 권위. 특히 §1 (Auditing 빈), §2 (책임 자리), §3 (도메인 제거) + §3.1 (시맨틱), §4 (port 분리) + §4.1 (flush invariant), §5 (use case 반환) + §5.1 (매핑 책임), §6 (진실원 단일), §7 (ADR-0007 dormant), §8 (테스트 fixture) + §8.1 (MutableClock + no-op).
- `docs/adr/0007-clock-injection-policy.md` — *dormant* 처리 대상. `Clock` 빈 + fixture 헬퍼 그대로 사용.
- `docs/adr/0006-test-strategy.md` §5 / §11.2 — `@DataJpaTest` 패턴.
- `docs/adr/0003-clean-architecture-ddd-hexagonal.md` — port/result layer 경계.
- `docs/plans/done/PLAN-0007-clock-injection.md` — 직전 작업. 본 Plan 이 일부 되돌림 (도메인 시간 인자).
- `.claude/skills/plan-lifecycle.md` — Plan 형식 / archival.
- `.claude/skills/clean-architecture.md` — port/result DTO 결정.
- `CLAUDE.md` — §3/§4/§5/§9.
- `src/main/java/com/dunowljj/board/domain/post/Post.java` — *변경 대상*. PLAN-0007 의 `LocalDateTime now` 인자 받는 형태.
- `src/main/java/com/dunowljj/board/domain/post/PostContent.java` — 무변경 (참고).
- `src/main/java/com/dunowljj/board/application/service/PostCommandService.java` — *변경 대상*. Clock 필드 + SavePostPort 의존.
- `src/main/java/com/dunowljj/board/application/service/PostQueryService.java` — *변경 대상*. LoadPostPort 사용.
- `src/main/java/com/dunowljj/board/application/port/in/CreatePostUseCase.java`, `GetPostUseCase.java`, `UpdatePostUseCase.java`, `ListPostsUseCase.java` — *변경 대상*. 반환 타입 변경.
- `src/main/java/com/dunowljj/board/application/port/in/result/PostListResult.java` — *변경 대상*. items 타입.
- `src/main/java/com/dunowljj/board/application/port/out/LoadPostPort.java` — *변경 대상*. 반환 타입.
- `src/main/java/com/dunowljj/board/application/port/out/SavePostPort.java` — *삭제 대상*.
- `src/main/java/com/dunowljj/board/application/port/out/DeletePostPort.java` — 무변경.
- `src/main/java/com/dunowljj/board/application/common/PostPage.java` — *변경 대상*. items 타입.
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostJpaEntity.java` — *변경 대상*. audit listener + setter/update.
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostJpaRepository.java` — *변경 대상*. tie-breaker.
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostMapper.java` — *변경 대상*. `AuditedPost` 합성.
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostPersistenceAdapter.java` — *변경 대상*. `CreatePostPort`+`UpdatePostPort` 구현.
- `src/main/java/com/dunowljj/board/adapter/in/web/PostController.java` — *변경 대상*. use case 결과 타입.
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/response/PostResponse.java`, `PostListResponse.java` — *변경 대상*. from() 시그니처.
- `src/main/java/com/dunowljj/board/config/TimeConfig.java` — *변경 대상*. `@EnableJpaAuditing` + `DateTimeProvider`.
- `src/test/java/com/dunowljj/board/domain/post/PostFixtures.java` — *변경 대상*. helper 시그니처.
- `src/test/java/com/dunowljj/board/domain/post/PostTest.java` — *변경 대상*. invariant 4 종 제거 + 호출 시그니처.
- `src/test/java/com/dunowljj/board/application/service/PostCommandServiceTest.java` — *변경 대상*. mock + 어서트.
- `src/test/java/com/dunowljj/board/application/service/PostQueryServiceTest.java` — *변경 대상*. mock 반환 타입.
- `src/test/java/com/dunowljj/board/adapter/out/persistence/post/PostPersistenceAdapterTest.java` — *변경 대상*. slice import + audit 어서트 + no-op 2 케이스.
- `src/test/java/com/dunowljj/board/adapter/in/web/PostControllerTest.java` — *변경 대상*. mock 반환 타입.
- `src/test/java/com/dunowljj/board/e2e/PostE2EIT.java` — 어셈블리 한정 변경 (응답 형태 그대로).
- `src/test/java/com/dunowljj/board/architecture/HexagonalArchitectureTest.java`, `TestStrategyArchitectureTest.java` — *통과 유지*.

## Files to Touch

**신규 (6)**:
- `src/main/java/com/dunowljj/board/application/port/out/result/AuditedPost.java`
- `src/main/java/com/dunowljj/board/application/port/in/result/AuditedPostResult.java`
- `src/main/java/com/dunowljj/board/application/port/out/CreatePostPort.java`
- `src/main/java/com/dunowljj/board/application/port/out/UpdatePostPort.java`
- `src/test/java/com/dunowljj/board/config/MutableClock.java`
- `src/test/java/com/dunowljj/board/config/TestAuditConfig.java`

**삭제 (1)**:
- `src/main/java/com/dunowljj/board/application/port/out/SavePostPort.java`

**수정 (Production, 15)**:
- `Post.java`, `PostJpaEntity.java`, `PostJpaRepository.java`, `PostMapper.java`, `PostPersistenceAdapter.java`
- `LoadPostPort.java`, `PostPage.java`
- `CreatePostUseCase.java`, `GetPostUseCase.java`, `UpdatePostUseCase.java`, `ListPostsUseCase.java`, `PostListResult.java`
- `PostCommandService.java`, `PostQueryService.java`
- `PostController.java`, `PostResponse.java`, `PostListResponse.java`
- `TimeConfig.java`

**수정 (Test, 6)**:
- `PostFixtures.java`, `PostTest.java`, `PostCommandServiceTest.java`, `PostQueryServiceTest.java`, `PostPersistenceAdapterTest.java`, `PostControllerTest.java`

## Implementation Steps

순서는 *의존성* 기준의 *작업 흐름 분해*. 본 PR 은 *단일 commit* 머지 (선례 PLAN-0006-C/D/0007). Step 2 후부터 Step 마지막 전까지 *컴파일 깨진 상태 정상* — Step 마지막의 `./gradlew check` 가 *최종 통과 시점*.

1. **신규 record 생성** — `AuditedPost`, `AuditedPostResult`. 컴파일 영향 없음 (다른 파일에서 아직 사용 안 함).
2. **`MutableClock` + `TestAuditConfig` 신규** — `setTo`/`advance` 메서드. `@TestConfiguration` 에 `@Primary` 빈. 컴파일 영향 없음.
3. **`TimeConfig` 갱신** — `@EnableJpaAuditing(dateTimeProviderRef = "auditDateTimeProvider")` + `DateTimeProvider` 빈. `Clock` 빈 유지.
4. **`PostJpaEntity` 갱신** — `@EntityListeners` + `@CreatedDate`/`@LastModifiedDate`. 신규 `update(String title, String body)` 메서드. createdAt 의 `updatable=false` 유지.
5. **`PostJpaRepository` tie-breaker** — `findAllByOrderByCreatedAtDescIdDesc(Pageable)`. 기존 메서드 제거.
6. **`Post` 도메인 시그니처 변경** — `createdAt`/`updatedAt` 필드 + getter 제거. `create`/`updateContent`/`reconstitute` timestamp 인자 제거. invariant 4 종 (now non-null × 2, 역행 금지, 경계) 제거. **이 단계 후 컴파일 대규모 깨짐** — 호출자 모두 시그니처 불일치.
7. **`PostMapper` 갱신** — `toDomain(entity)` 가 `AuditedPost` 반환 (`Post.reconstitute(...)` + entity audit 합성). `toEntity(post)` 가 *신규 entity* 만 생성 (timestamp 인자 없이).
8. **`SavePostPort` 삭제 + `CreatePostPort`/`UpdatePostPort` 신규** — port 인터페이스 시그니처 (반환 `AuditedPost`).
9. **`LoadPostPort` 반환 타입 갱신** — `Optional<AuditedPost>`, `PostPage` 의 items 타입 갱신.
10. **`PostPage` items 타입 갱신** — `List<AuditedPost>`.
11. **`PostPersistenceAdapter` 갱신** — `CreatePostPort` + `UpdatePostPort` implements. `LoadPostPort` 구현 갱신. `CreatePostPort.create`: persist. `UpdatePostPort.update`: load-mutate-save + **`repository.saveAndFlush(existing)`** (§4.1 flush 보장).
12. **use case 인터페이스 갱신** — `CreatePostUseCase`/`GetPostUseCase`/`UpdatePostUseCase` 반환 `AuditedPostResult`. `PostListResult` items 타입 갱신.
13. **`PostCommandService` 갱신** — `Clock` 필드 제거. `SavePostPort` 의존 제거, `CreatePostPort`+`UpdatePostPort` 의존 추가. `create`: `Post.create(title, body, author)` → `createPostPort.create(post)` → `AuditedPostResult` 변환. `update`: `loadPostPort.findById(id)` 의 `AuditedPost` 의 post 사용 → `post.updateContent(title, body)` → `updatePostPort.update(post)` → `AuditedPostResult` 변환.
14. **`PostQueryService` 갱신** — `LoadPostPort` 의 `AuditedPost` 결과를 `AuditedPostResult` 로 변환 (service 의 합성 책임, §5.1).
15. **`PostResponse`/`PostListResponse` 갱신** — `from(AuditedPostResult)`. items 매핑 갱신.
16. **`PostController` 갱신** — use case 결과 변경에 따른 `PostResponse.from(...)` 호출 갱신.
17. **`PostFixtures` 갱신** — `aValidPost()` / `aReconstitutedPost(Long)` 시그니처 갱신. 2-인자 overload 제거. (선택) `anAuditedPost(...)` 헬퍼 신규.
18. **`PostTest` 마이그레이션** — invariant 4 종 (now null × 2, 역행 금지, 경계) 테스트 *삭제*. `Post.create`/`updateContent`/`reconstitute` 호출 시그니처 갱신. content invariant 테스트는 유지.
19. **`PostCommandServiceTest` 마이그레이션** — `Clock` 인자 제거. `SavePostPort` mock → `CreatePostPort`+`UpdatePostPort` mock. mock 반환 `AuditedPost`. service 결과 `AuditedPostResult` 어서트.
20. **`PostQueryServiceTest` 마이그레이션** — `LoadPostPort` mock 반환 `AuditedPost`. service 결과 어서트.
21. **`PostPersistenceAdapterTest` 마이그레이션** — `@Import({PostPersistenceAdapter.class, TimeConfig.class, TestAuditConfig.class})`. 기본 어서트 — `MutableClock` 초기값 `FIXED_NOW` 로 listener 채운 timestamp 어서트. **신규 어서트 2 케이스 (§8.1)**:
    - `update_with_same_content_keeps_updatedAt` — `mutableClock.advance(...)` → 동일 값 update → updatedAt 불변
    - `update_with_different_content_advances_updatedAt` — `mutableClock.advance(...)` → 다른 값 update → updatedAt 갱신
22. **`PostControllerTest` 마이그레이션** — mock 반환 `AuditedPostResult`. response body 어서트 그대로.
23. **`PostE2EIT` 마이그레이션** — assembly 한정. response body 어서트 (`createdAt`/`updatedAt` notNullValue 등) 그대로. mock 또는 fixture 의 audit 흐름 자연.
24. **`./gradlew check`** — `test` + `integrationTest` 모두 green. ArchUnit 11 규칙 통과 유지.

## Acceptance Criteria

- `Post` 도메인에 `createdAt`/`updatedAt` 필드/getter 존재 *없음* — `grep -n "createdAt\\|updatedAt" src/main/java/com/dunowljj/board/domain/post/Post.java` 결과 0.
- `Post.create(String, String, String)`, `Post.updateContent(String, String)`, `Post.reconstitute(Long, String, String, String)` 시그니처. timestamp 인자 0 개.
- PLAN-0007 의 도메인 invariant 4 종 테스트 제거 — `PostTest` 에 `create_throws_when_now_is_null` / `updateContent_throws_when_now_is_null` / `updateContent_throws_when_now_is_before_*` / `updateContent_accepts_when_now_equals_*` 4 종 부재.
- `SavePostPort` 파일 존재 *없음*.
- `CreatePostPort` + `UpdatePostPort` 파일 존재. 각각 `AuditedPost save(Post post)` 시그니처.
- `AuditedPost` 파일 위치 `application/port/out/result/`. `AuditedPostResult` 파일 위치 `application/port/in/result/`. `AuditedPostResult` 가 `AuditedPost` 를 *import 하지 않음* — `grep "AuditedPost" src/main/java/com/dunowljj/board/application/port/in/result/AuditedPostResult.java` 결과 클래스 자체 정의만, AuditedPost import 0.
- `PostJpaEntity` 가 `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate` + `@LastModifiedDate` 부착. `update(String title, String body)` 메서드 보유.
- `PostJpaRepository` 가 `findAllByOrderByCreatedAtDescIdDesc(Pageable)` 메서드 보유. 기존 `findAllByOrderByCreatedAtDesc` 없음.
- `PostPersistenceAdapter` 가 `CreatePostPort` + `UpdatePostPort` 동시 implements. `UpdatePostPort.update` 구현이 `repository.findById` + `existing.update(...)` + `repository.saveAndFlush(...)` 패턴.
- `PostCommandService` 에 `Clock` 필드 없음. `CreatePostPort`/`UpdatePostPort` 의존.
- `TimeConfig` 가 `@EnableJpaAuditing` 부착, `DateTimeProvider` 빈 보유. `Clock` 빈 유지.
- `MutableClock` + `TestAuditConfig` 신규. `TestAuditConfig` 가 `@Primary MutableClock` 빈 (`startingAt(FIXED_NOW)`) 제공.
- `PostPersistenceAdapterTest` 의 `@Import` 에 `TimeConfig.class` + `TestAuditConfig.class` 포함. *동일 값 update no-op* + *다른 값 update advances* 2 케이스 어서트 존재.
- `./gradlew check` BUILD SUCCESSFUL — `test` + `integrationTest` 모두 green.
- ArchUnit 11 규칙 통과 유지.
- 신규 의존성 *없음*. `build.gradle` 변경 *없음*.
- `@DisplayName` 한국어, 한 테스트 = 한 어서션 그룹 (ADR-0006 §4).

## ADR Required

no — ADR-0008 이 권위. 본 Plan 은 §1–§8 + §3.1/§4.1/§5.1/§8.1 의 *직접 구현*. 새로운 시스템 결정 없음. 다음 두 결정은 ADR-0008 본문 *이미 명시*:
- `@Column(updatable=false)` 유지 + `@CreatedDate` 조합 (§2 적용).
- `UpdatePostPort` 의 flush 수단 = `saveAndFlush` (§4.1 의 "수단 PLAN-0008 선택" 위임 결정).

`PostJpaEntity.update(String title, String body)` 메서드 도입 결정 — setter 우후죽순 노출 대 entity 의 자기 변경 책임 중 후자 채택. *DDD 의 자연 패턴* 이라 ADR 격상 불요. PLAN 본문 결정으로 충분.

## Risks

1. **`PostJpaEntity.update(...)` 메서드 도입 vs setter 노출** — *결정 적용*. ADR-0008 §4.1 의 load-mutate-save 흐름이 *setter 우후죽순* 으로 가지 않게 entity 가 *자기 변경 책임* 보유. DDD 의 자연 패턴 — Aggregate 의 캡슐화 정신. *대안* (setter 노출) 거부.
2. **`@EnableJpaAuditing` 컴포넌트 스캔 범위** — `BoardServiceApplication` 이 `com.dunowljj.board` root 라 `..config..` 자동 포함. 확인 완료.
3. **`TestAuditConfig` 의 `@Primary` 가 production Clock 빈 override** — slice scope (`@Import`) 안에서만 영향. E2E (`PostE2EIT`) 가 `TestAuditConfig` import 안 하면 production Clock 그대로. *명시 import* 필요한 자리만 영향 — 정합.
4. **`MutableClock` 의 thread-safety** — 단위/integration 테스트가 단일 thread 라 *동시성 안전 미보장 명시* javadoc 으로 충분. `MutableClock` 의 instant 필드는 `volatile` 부착 권장 (memory visibility 보장 — 단순 1 줄). `setTo`/`advance` 사용 호출자에 대한 synchronization 책임은 *호출자 측*.
5. **`PostMapper.toDomain` 의 `Post.reconstitute(...)` 호출** — Post 도메인이 timestamp 인자 안 받으므로 호출 시그니처가 `Post.reconstitute(id, title, body, author)`. audit timestamp 는 *Mapper 가 별도로 합성*. mapper 책임 증가 (entity 의 두 필드를 *AuditedPost 의 두 인자*로). 정합.
6. **`PostPersistenceAdapter` 가 두 port 인터페이스 동시 implements** — 단일 adapter 가 `CreatePostPort`, `UpdatePostPort`, `LoadPostPort`, `DeletePostPort` 4 개 implements. *책임 응집* — 같은 영속 모델 (`PostJpaEntity`) 을 다루는 자리. *대안* (어댑터 분리 — `PostCreateAdapter`, `PostUpdateAdapter` 등) 거부: 4 개 빈 + 동일 repository 의존 중복 → 결정 적용. PLAN-0008 의 *명시 결정*: 단일 adapter 가 4 port implements. 향후 *권한/소유권 분리* 같은 cross-cutting 책임 등장 시 별도 결정.
7. **`PostListResult.items` 타입 변경 → controller body 매핑** — `PostListResponse` 의 `posts` 항목이 `List<PostResponse>` 형식. items 매핑이 `PostResponse::from(AuditedPostResult)` 로 변경. 응답 JSON 키/값 *변경 없음* (PostResponse 필드 동일). API 호환성 무영향.
8. **`Post.reconstitute(...)` 의 audit 인자 제거 → fixture 영향** — `PostFixtures.aReconstitutedPost(Long, LocalDateTime, LocalDateTime)` 2-인자 overload 제거. 호출자 (다른 테스트) 에서 *audit fixture* 가 필요하면 `AuditedPost(post, ...)` 직접 합성. 호출 부담 ↑이지만 *시그니처 명료성* ↑.
9. **`@LastModifiedDate` 의 dirty check 동작** — ADR-0008 §3.1 의 *동일 값 PUT no-op* 시맨틱. `UpdatePostPort.update` 가 `existing.update(...)` 호출 시 *값이 동일* 하면 entity 의 setter 가 동일 값 set → Hibernate dirty check 가 *변경 없음* 판정 → `@PreUpdate` listener 미발화 → `updatedAt` 불변. `saveAndFlush` 호출해도 UPDATE 미발사 (dirty 없음). 정합 — §8.1 의 어서트 2 케이스가 이 동작 검증.
10. **`PostE2EIT` 의 audit Clock override 여부** — `@SpringBootTest` 는 full context. production `Clock` (systemDefaultZone) 사용 — listener 가 *실제 시각* 으로 timestamp 채움. E2E 응답 어서트가 `notNullValue()` / `matchesPattern` 등 약한 어서트라 *시각 결정성 불요*. `TestAuditConfig` 명시 import *불요* (E2E 가 audit 자체 동작이 아닌 *어셈블리* 검증).

### Pre-resolved

- **`saveAndFlush` 채택** — ADR-0008 §4.1 의 "flush 수단 PLAN-0008 선택" 위임을 *Spring Data JPA 표준 메서드* 로 적용. `repository.flush()` / `entityManager.flush()` 거부 사유 = 호출 명료성 (한 메서드로 save + flush 한 자리).
- **`PostJpaEntity.update(...)` 메서드** — setter 우후죽순 회피. entity 의 자기 변경 책임. ADR 격상 불요.
- **`PostPersistenceAdapter` 단일 adapter 4 port implements** — 책임 응집. 어댑터 분리 거부.
- **`PostPage` 이름 유지** — record 명 변경 (Java 컴파일러는 무리 없지만 git diff 부담) 거부. items 타입만 변경.
- **`MutableClock` 위치 `src/test/java/com/dunowljj/board/config/`** — 테스트 전용. production 패키지 오염 회피.

## Implementation Hints

### `Post` 도메인 (Step 6) — 시그니처 변경 후

```java
public class Post {
    private Long id;
    private PostContent content;
    private String author;

    private Post(Long id, PostContent content, String author) { ... }

    public static Post create(String title, String body, String author) {
        validateAuthor(author);
        return new Post(null, new PostContent(title, body), author);
    }

    public static Post reconstitute(Long id, String title, String body, String author) {
        if (id == null) throw new IllegalArgumentException("Id must not be null");
        validateAuthor(author);
        return new Post(id, new PostContent(title, body), author);
    }

    public void updateContent(String title, String body) {
        this.content = new PostContent(title, body);
    }
    // getter: id, content, title, body, author (createdAt/updatedAt 없음)
}
```

### `AuditedPost` (Step 1)

```java
package com.dunowljj.board.application.port.out.result;

import com.dunowljj.board.domain.post.Post;
import java.time.LocalDateTime;

public record AuditedPost(Post post, LocalDateTime createdAt, LocalDateTime updatedAt) {}
```

### `AuditedPostResult` (Step 1) — `AuditedPost` 미의존

```java
package com.dunowljj.board.application.port.in.result;

import com.dunowljj.board.domain.post.Post;
import java.time.LocalDateTime;

public record AuditedPostResult(
        Long id, String title, String body, String author,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static AuditedPostResult from(Post post, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new AuditedPostResult(
                post.getId(), post.getTitle(), post.getBody(), post.getAuthor(),
                createdAt, updatedAt
        );
    }
}
```

### `PostJpaEntity.update(...)` (Step 4)

```java
@Entity
@Table(name = "posts")
@EntityListeners(AuditingEntityListener.class)
public class PostJpaEntity {
    // ...
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public PostJpaEntity(Long id, String title, String body, String author) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.author = author;
        // createdAt/updatedAt — listener 가 채움
    }

    public void update(String title, String body) {
        this.title = title;
        this.body = body;
    }
}
```

### `PostPersistenceAdapter` — `UpdatePostPort.update` (Step 11)

```java
@Override
public AuditedPost update(Post post) {  // UpdatePostPort.update (id != null 보장)
    PostJpaEntity existing = repository.findById(post.getId())
            .orElseThrow(() -> new PostNotFoundException(post.getId()));
    existing.update(post.getTitle(), post.getBody());
    PostJpaEntity saved = repository.saveAndFlush(existing);  // flush 보장 invariant
    return new AuditedPost(PostMapper.toDomain(saved).post(), saved.getCreatedAt(), saved.getUpdatedAt());
}
```

### `TimeConfig` 갱신 (Step 3)

```java
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditDateTimeProvider")
public class TimeConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public DateTimeProvider auditDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
```

### `MutableClock` (Step 2)

```java
package com.dunowljj.board.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 테스트 전용. 단일 thread 가정 — 동시성 안전 미보장.
 */
public class MutableClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    public static MutableClock startingAt(LocalDateTime localNow) {
        ZoneId zone = ZoneId.systemDefault();
        return new MutableClock(localNow.atZone(zone).toInstant(), zone);
    }

    private MutableClock(Instant instant, ZoneId zone) { ... }

    public void setTo(LocalDateTime localNow) { this.instant = localNow.atZone(zone).toInstant(); }
    public void advance(Duration d) { this.instant = this.instant.plus(d); }

    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId zone) { return new MutableClock(instant, zone); }
    @Override public Instant instant() { return instant; }
}
```

### `TestAuditConfig` (Step 2)

```java
package com.dunowljj.board.config;

import com.dunowljj.board.domain.post.PostFixtures;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestAuditConfig {

    @Bean
    @Primary
    public MutableClock auditClock() {
        return MutableClock.startingAt(PostFixtures.FIXED_NOW);
    }
}
```

**반환 타입 `MutableClock` 명시** — 호출자가 `@Autowired MutableClock` 으로 받아 `setTo`/`advance` 사용. `Clock` 반환 + 캐스트 대안 거부 (명시성 ↓).

### 테스트 시점 조작 패턴 (Step 21)

```java
@DataJpaTest
@Import({PostPersistenceAdapter.class, TimeConfig.class, TestAuditConfig.class})
class PostPersistenceAdapterTest {

    @Autowired PostPersistenceAdapter adapter;
    @Autowired MutableClock clock;

    @Test
    @DisplayName("동일 내용 update 시 updatedAt 이 변경되지 않는다 (no-op)")
    void update_with_same_content_keeps_updatedAt() {
        // create at FIXED_NOW
        AuditedPost created = adapter.create(Post.create("t", "b", "a"));
        LocalDateTime t1 = created.updatedAt();
        // advance clock
        clock.advance(Duration.ofMinutes(1));
        // update with same content
        AuditedPost updated = adapter.update(Post.reconstitute(created.post().getId(), "t", "b", "a"));
        // updatedAt 불변
        assertThat(updated.updatedAt()).isEqualTo(t1);
    }

    @Test
    @DisplayName("다른 내용 update 시 updatedAt 이 새 시점으로 진행한다")
    void update_with_different_content_advances_updatedAt() {
        AuditedPost created = adapter.create(Post.create("t", "b", "a"));
        clock.advance(Duration.ofMinutes(1));
        LocalDateTime t2 = LocalDateTime.now(clock);
        AuditedPost updated = adapter.update(Post.reconstitute(created.post().getId(), "t2", "b2", "a"));
        assertThat(updated.updatedAt()).isEqualTo(t2);
    }
}
```

(slice test 라 `adapter.create(...)` / `adapter.update(...)` 직접 호출. `PostPersistenceAdapter` 가 `CreatePostPort` + `UpdatePostPort` 양쪽 implements 하므로 두 메서드 모두 보유.)

## Execution Notes

<!-- 실행 중 비자명한 결정만 시간순 append. 사소한 구현 디테일은 적지 않는다. -->

- 2026-05-18 — `PostPersistenceAdapterTest` 에 `@BeforeEach clock.setTo(FIXED_NOW)` 추가. 사유: `MutableClock` 빈이 Spring context 단일 인스턴스라 *테스트 메서드 간 상태 공유* — `@DataJpaTest` 의 DB rollback 이 bean 상태는 안 되돌림. 이전 테스트의 `advance`/`setTo` 가 누수되면 *순서 의존성* 발생 (JUnit 5 메서드 순서는 deterministic but unspecified) → 순서가 우연히 통과해도 *ADR-0006 §5 결정성 원칙* 위반. ADR-0008 §8 의 *MutableClock 초기값 FIXED_NOW* 의식이 *시작 시점만 보장* 하고 *테스트 간 보존* 은 명시 안 된 자리 — `@BeforeEach reset` 으로 명시. *대안* (`MutableClock.reset()` 메서드 도입) 거부: 초기값 의식이 MutableClock 까지 흘러 응집도 ↓.
- 2026-05-18 — Plan Scope (Step 17) 의 `PostFixtures.anAuditedPost(...)` helper 도입을 *기각*. 사유: AuditedPost 는 도메인 객체 아닌 application port out result 타입이라 `PostFixtures` (`domain.post` 패키지) 가 `application.port.out.result.AuditedPost` 를 import 하는 게 *domain fixture 경계 흐림*. 또 application service 테스트에서 *timestamp 가 어서트 의미* 라 `new AuditedPost(post, createdAt, updatedAt)` 직접 호출이 *명시성* ↑ — fixture 로 timestamp 숨기는 게 오히려 의미 약화. `PostCommandServiceTest` 의 captor 패턴 (`thenAnswer(inv -> new AuditedPost(inv.getArgument(0), ...))`) 은 *SUT 가 mutate 한 동일 Post 인스턴스 반환 의도* 라 fixture 부적합. **결정**: `PostFixtures.anAuditedPost` + `AuditedPost` import 모두 제거. *AuditedPostFixtures 별도 분리* 도 *현 활용 패턴 0* 이라 YAGNI — 두 번째 도메인 추가 시 (Comment 등 audit fixture 반복) 도입 검토.
