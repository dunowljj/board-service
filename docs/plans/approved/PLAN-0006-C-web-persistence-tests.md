# PLAN-0006-C: Web · Persistence 슬라이스 테스트 작성

PLAN-0006-A 가 마련한 인프라(`integrationTest` 분리 · `@Tag("integration")` · ArchUnit 11종)와 PLAN-0006-B 가 채운 Domain·Application 단위 테스트 위에서, ADR-0006 §1 표의 *Driving Adapter (Web)* 와 *Driven Adapter (Persistence)* 행을 채운다. 슬라이스 테스트만 다루고 E2E (`@SpringBootTest` + MockMvc 골든 플로우) 는 PLAN-0006-D 로 분리.

## Goal

Post 도메인의 *현 코드*에 대해 ADR-0006 §1·§2·§11.2 가 요구하는 슬라이스 테스트를 작성한다. 구체적으로:

1. `PostController` 의 HTTP 계약(라우팅·상태 코드·요청/응답 바디 형태·`@Valid` 검증 오류·경로 변환 오류) 을 `@WebMvcTest` 슬라이스에서 고정한다. Input Port 는 mock.
2. `PostPersistenceAdapter` 의 Output Port 계약(`save` round-trip, `findById`, `findPage` 페이징·정렬·`totalElements`, `deleteById` 의 row-count 0/1) 을 `@DataJpaTest` + `@Import` 슬라이스에서 고정한다. H2 inmemory.
3. `PostMapper` 단독 테스트의 *필요성을 판정*하고, 필요 없으면 명시적으로 작성하지 않는다 — round-trip 테스트가 *구별 가능한* 변환만 다루므로(ADR-0006 §2 의 "round-trip 으로 구별 가능한가" 판정).
4. `TraceIdFilter` 가 Web 슬라이스에 어떻게 노출되는지를 *명시 결정* — slice 자동 포함 가정 금지(ADR-0006 §2 의 "필터 테스트는 slice 자동 포함 여부에 *의존하지 않는다*" 정책).

## Scope

### Web 슬라이스 — 신규 테스트 클래스 1개

- `adapter/in/web/PostControllerTest` — `@WebMvcTest(PostController.class)`. 다음 슬라이스 책임을 *모두 한 클래스* 로 묶는다 (ADR-0006 §6: 운영 클래스와 1:1 미러).
  - 라우팅·상태 코드: `POST /api/posts` → 201, `GET /api/posts/{id}` → 200, `PUT /api/posts/{id}` → 200, `DELETE /api/posts/{id}` → 204, `GET /api/posts` → 200.
  - 요청/응답 바디 형태: `PostResponse` / `PostListResponse` JSON 필드 키·타입·페이지네이션 메타(`page`/`size`/`totalElements`/`totalPages`).
  - 입력 검증 오류: `CreatePostRequest`/`UpdatePostRequest` 의 `@NotBlank`·`@Size` 위반 → `MethodArgumentNotValidException` → `GlobalExceptionHandler` → `ProblemDetail` `code=VALIDATION_FAILED`, `errors[].field/reason` 형태(ADR-0005 §2, PLAN-0005-B 산출물).
  - 경로/쿼리 파라미터 검증: `list` 의 `@Min(0)` page, `@Min(1) @Max(100)` size 범위 위반 → `HandlerMethodValidationException` → `ProblemDetail` `code=VALIDATION_FAILED`.
  - JSON 파싱 실패: malformed JSON 본문 → `ProblemDetail` `code=MALFORMED_REQUEST` (400).
  - 도메인 예외 → HTTP 매핑: `getById` mock 이 `PostNotFoundException` 던질 때 → 404, `code=POST_NOT_FOUND`. `update` mock 이 `InvalidPostContentException` 던질 때 → 400, `code=INVALID_POST_CONTENT`.
  - **`GlobalExceptionHandler` 명시 적재** — `@WebMvcTest` 가 `@RestControllerAdvice` 빈을 자동 포함하지만, 슬라이스 범위 변경에 견고하도록 `@Import(GlobalExceptionHandler.class)` *및* 필요 시 `ErrorCategoryHttpStatusMapper` 명시. (Risks #2 참고.)
  - **`TraceIdFilter` 정책: 본 슬라이스는 자동 포함을 *가정하지 않음*.** `MockMvc` 기본값(필터 자동 적용 안 함) 그대로. `X-Trace-Id` 응답 헤더·MDC 검증은 본 Plan 범위 *밖* (필터 단위 테스트 또는 E2E 영역). 명시적으로 Non-goals 에 둠 — 현재 위치에서는 *Web 계약* 만 검증.

### Persistence 슬라이스 — 신규 테스트 클래스 1개

- `adapter/out/persistence/post/PostPersistenceAdapterTest` — `@DataJpaTest` + `@Import(PostPersistenceAdapter.class)`. ADR-0006 §11.2 패턴.
  - `save` round-trip: 신규 `Post.create(...)` 저장 → 반환 객체에 id 부여, `createdAt`/`updatedAt` 보존. 이후 `findById` 로 다시 읽어 동일 필드(`title`/`body`/`author`) 복원 확인.
  - `save` 갱신 경로: 기존 id 가 있는 도메인 객체(`reconstitute`로 만든 fixture) 저장 → 동일 id 로 업데이트되어 row 수가 유지됨(`postJpaRepository.count()` 검증).
  - `findById` not-found: 존재하지 않는 id → `Optional.empty()`.
  - `findPage` 페이징·정렬: 여러 row 삽입 후 `findPage(0, size)` → `items` 가 `createdAt DESC` 순, `totalElements` 일치. 두 번째 페이지(`findPage(1, size)`) 도 검증.
  - `findPage` 빈 페이지: 데이터 0건 → `items` 빈 리스트, `totalElements == 0`.
  - `deleteById` row-count: 존재하는 id → 1, 존재하지 않는 id → 0 (ADR-0006 §11.2 예시. CommandService not-found 신호의 *어댑터 측 약속*).
  - **트랜잭션 격리**: `@DataJpaTest` 가 트랜잭션 롤백을 기본 적용하므로(ADR-0006 §5) 테스트 간 상태 누수 없음. 별도 cleanup 코드 작성 금지.

### Mapper 단독 테스트 — *작성하지 않음* (명시 결정)

`PostMapper.toDomain` / `toEntity` 는 정책 없는 1:1 필드 복사다. 입력 정규화 없음, VO 조립/해체 없음, enum 변환 없음, null/default 분기 없음, soft delete 없음, auditing 없음, version 없음. ADR-0006 §2·§8 의 "round-trip 으로 구별 가능한가" 판정에서 모든 분기가 `PostPersistenceAdapterTest` 의 save round-trip 으로 *구별 가능*. 따라서 단독 테스트 작성 가치 음(陰). 본 Plan 의 Acceptance Criteria 가 "Mapper 단독 테스트는 작성하지 않는다" 를 *결정*으로 못 박는다.

### 신규 ArchUnit 규칙 — *작성하지 않음*

ADR-0006 §3.2 가 "prescriptive 한 규칙(예: Web 테스트는 반드시 `@WebMvcTest` 사용)은 제외" 라고 명시. `webmvctest_is_localized` 같은 forward-defense 도 정당한 예외(필터 단위 테스트, mapper 단위 테스트)를 잡아 너무 경직됨. 본 Plan 은 ArchUnit 변경 없음. 만약 작성 중 *현재 ArchUnit 규칙*(`springboottest_is_localized`, `domain_tests_are_pojo`, `application_service_tests_no_springboottest`) 이 슬라이스 테스트 추가로 빨개지면 — 작성자가 슬라이스 테스트를 *잘못 배치한 것*. PLAN-0006-A 의 규칙은 그대로 통과해야 한다.

## Non-goals

- E2E (`@SpringBootTest` + MockMvc 골든 플로우, `BoardServiceApplicationIT` 등) — PLAN-0006-D.
- `TraceIdFilter` 단독/통합 테스트 — 별도 Plan 또는 PLAN-0006-D 의 E2E 범위. 본 Plan 은 *MockMvc 기본 필터 미적용* 전제로 작성.
- Mapper (`PostMapper`) 단독 테스트 — Scope 의 판정 결정에 따라 *작성하지 않음*.
- 신규 ArchUnit 규칙 추가 — Scope 참고.
- `PostPersistenceAdapter` 의 PostgreSQL/Testcontainers 검증 — ADR-0006 §2 가 "Testcontainers 는 목표, H2 는 임시 다리" 로 결정. 전환은 별도 Plan(ADR-0006 Open Questions). 본 Plan 은 H2 한정 동작에 의존하지 않게 작성하되 H2 위에서 통과만 보장.
- `PostJpaRepository.deletePostById` 의 `@Modifying` + `clearAutomatically`/`flushAutomatically` 동작 검증 — 어댑터 round-trip 으로 충분(`deleteById` 의 row-count 가 보장). JPQL 자체 단위 테스트 작성 금지.
- 컨트롤러 외 Web 영역(`adapter/in/web/error/`, `adapter/in/web/observability/`) 의 단독 테스트 — `ErrorCategoryHttpStatusMapper` 는 enum switch 로 자명, `TraceIdFilter` 는 위 명시 제외.
- Spring Security 도입(인증/인가/CSRF) — ADR-0006 §9 가 보류 결정.
- Clock 주입 리팩터링 — ADR-0006 §5, 별도 ADR/Plan.

## Related ADRs

- ADR-0006 (테스트 전략) — 본 Plan 의 권위. 특히 §1 표(Web slice·Persistence slice·Mapper 행), §2(슬라이스 격리·`@WebMvcTest`·`@DataJpaTest` 정책·필터 테스트 분리), §3.2(`TestStrategyArchitectureTest` 가 슬라이스 테스트를 *간접 강제*), §10(슬라이스는 빠른 `test` task), §11.2(`@DataJpaTest` + `@Import` 패턴), §11.3(ArchUnit 변경 없음 근거).
- ADR-0005 (예외/에러 응답 정책) — Web 슬라이스가 검증할 `ProblemDetail` 스키마(code, errors, timestamp, instance), `MALFORMED_REQUEST`/`VALIDATION_FAILED`/도메인 예외 → HTTP 매핑.
- ADR-0003 (Clean/Hexagonal + DDD + CQRS) — 슬라이스 테스트의 의존 경계(Driving = Input Port mock, Driven = output port 구현체 직접).

## Acceptance Criteria

- `./gradlew test` 가 신규 테스트 2 클래스(`PostControllerTest`, `PostPersistenceAdapterTest`) 를 모두 실행하고 통과한다 (PLAN-0006-A 의 `excludeTags 'integration'` 하에서 — 두 슬라이스 테스트 모두 `@Tag("integration")` *부착 금지*. 슬라이스 테스트는 ADR-0006 §10 표에서 빠른 `test` task 카테고리).
- `PostControllerTest` 는 `@WebMvcTest(PostController.class)` 로 슬라이스 명시. `@SpringBootTest` 부착 금지 — `springboottest_is_localized` ArchUnit 규칙 통과.
- `PostControllerTest` 는 5개 엔드포인트(`create`/`getById`/`update`/`delete`/`list`) 의 *성공 경로* 와 *오류 경로*(검증 실패, 도메인 예외 매핑, malformed JSON) 를 모두 어서트한다. Input Port (`CreatePostUseCase` 등 5종) 는 `@MockitoBean` 으로 mock (Spring Boot 4.0 이 `@MockBean` 제거 — Risks Pre-resolved 참고).
- `PostControllerTest` 의 검증 오류 어서트는 `ProblemDetail` 의 *키 존재성·값* 을 명시: `status`/`type`/`title`/`detail`/`instance`/`code`/`timestamp`/`errors` (`errors` 는 `[{field, reason}]` 형태). `code` 는 `VALIDATION_FAILED` 또는 `MALFORMED_REQUEST` 또는 `POST_NOT_FOUND`/`INVALID_POST_CONTENT` (시나리오별).
- `PostControllerTest` 의 `list` 쿼리 파라미터 검증은 `page=-1` (음수) 와 `size=101` (max 초과) 두 케이스 *모두* 어서트 — `HandlerMethodValidationException` 경로가 살아있음을 고정.
- `PostPersistenceAdapterTest` 는 `@DataJpaTest` + `@Import(PostPersistenceAdapter.class)` 슬라이스 명시. `@SpringBootTest` 부착 금지.
- `PostPersistenceAdapterTest` 는 6개 시나리오(save 신규/save 갱신/findById not-found/findPage 페이징·정렬/findPage 빈/deleteById row-count 0·1) 를 모두 어서트한다. `findPage` 정렬 검증은 `createdAt` 의 *순서* 만 어서트(절대값 어서트 금지 — `LocalDateTime.now()` 비결정).
- `PostPersistenceAdapterTest` 는 H2 한정 동작(`IDENTITY` 시퀀스 동작 가정, H2 전용 함수, JSON 컬럼 등)에 *의존하지 않게* 작성한다 (ADR-0006 §2). id 자동 생성은 `assertThat(saved.getId()).isNotNull()` 로 약하게 어서트, 시퀀스 시작값/증가값 어서트 금지.
- 두 슬라이스 테스트 모두 *Spring Boot 의 다른 자동구성*(`spring-boot-h2console`, `spring-boot-starter-validation` 의 추가 빈 등)에 의존하지 않는다 — 슬라이스가 명시적으로 끌어오는 빈만 사용.
- `Mapper` 단독 테스트는 작성되지 않는다. 작성된 경우 *Plan 위반* — Reviewer 가 거부.
- `@DisplayName` 한국어, *동작/규칙 문장* 형태 (ADR-0006 §4·§6). 메서드명은 `<동작>_<상태>` 영어. 한 테스트 = 한 어서션 그룹.
- 테스트 패키지가 운영 패키지와 1:1 미러 — `src/test/java/com/dunowljj/board/adapter/in/web/`, `src/test/java/com/dunowljj/board/adapter/out/persistence/post/`.
- PLAN-0006-A 의 ArchUnit 규칙 11종(production 8 + test strategy 3) 모두 통과 유지. 신규 ArchUnit 규칙 추가 없음.
- `./gradlew build` 통과. 신규 의존성 추가 없음 — `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `h2` 가 이미 build.gradle 에 있으므로 충분(검증 완료).

## ADR Required

no — ADR-0006 이 권위. 본 Plan 은 §1 표·§2·§11.2 의 *직접 구현*. 새로운 시스템 결정은 다음 둘 — (a) Mapper 단독 테스트를 작성하지 않는다, (b) `TraceIdFilter` 를 본 슬라이스에 자동 포함시키지 않는다 — 모두 ADR-0006 본문(§2·§8) 에 *이미 결정* 되어 있어 본 Plan 은 이를 *적용*만 한다. ADR 신규 작성 불필요.

## Risks

1. **`@DataJpaTest` 의 `@Entity` 스캔 범위.** `@DataJpaTest` 는 base package 부터 `@Entity` 를 스캔하므로 `PostJpaEntity` 가 자동 등록됨. `BoardServiceApplication.java` 가 `com.dunowljj.board` 루트에 있어 안전(확인 완료).
2. **H2 의 SQL 방언 차이 — `@Modifying` JPQL DELETE.** `PostJpaRepository.deletePostById` 는 JPQL 이고 H2 가 정상 처리. PostgreSQL 전환 시 영속성 컨텍스트 동기화 동작이 다를 수 있어 *향후* 별도 검증 필요. 본 Plan 은 H2 위에서 row-count 0/1 만 어서트(ADR-0006 §2 의 "H2 한정 동작 의존 금지" 전제).
3. **검증 오류 응답의 `ProblemDetail.timestamp` 필드는 `LocalDateTime.now().toString()`.** 어서트 시 절대값 비교 금지. *키 존재성* + *ISO 형식* 만 어서트(`assertThat(json.read("$.timestamp")).asString().matches("\\d{4}-.*")` 등 약한 패턴).
4. **`PostListResponse` 의 직렬화 — record 의 JSON 키 순서.** Jackson 은 record component 순서 보장하지만 어서트 시 *순서* 보다 *키-값 쌍* 으로 어서트(`jsonPath("$.posts[0].title")`). 정렬 의존 금지.
5. **MockMvc + `@WebMvcTest` 의 `MediaType.APPLICATION_PROBLEM_JSON`.** `GlobalExceptionHandler` 는 `ResponseEntity<ProblemDetail>` 반환 → Spring 이 자동으로 `application/problem+json` Content-Type 적용. 어서트 시 두 가지 가능성 — `application/json` 또는 `application/problem+json`. 작성 시 *실제 응답* 확인 후 어서트 작성. 미스매치 발견 시 production 의 `produces` 명시 누락 가능성 — 별도 fix Plan.
6. **현 production 코드의 가정 위반 발견 시 처리.** 본 Plan 작성 중 controller 가 Input Port 를 *우회*하거나 (예: `PostJpaRepository` 직접 호출) `GlobalExceptionHandler` 가 *예상 매핑을 누락*한 분기를 발견하면 — *Risks 에 기록*하고 fix Plan 으로 분리. 본 Plan 에서 production 수정 금지(Plan-lifecycle Scope 규율).

### Pre-resolved (Plan 시점 공식문서 확인 완료)

다음 항목들은 Risks 가 아니라 *결정사항*. Implementation Hints 가 채택:

- **`@MockitoBean` 사용** — Spring Boot 4.0 이 `@MockBean` 완전 제거. `@MockitoBean` 으로 1:1 대체(필드 레벨 사용 케이스 한정). 출처: Spring Boot 4.0 Migration Guide.
- **`@Import(GlobalExceptionHandler.class)` 미사용** — `@WebMvcTest` 가 `@ControllerAdvice`/`@RestControllerAdvice` 빈을 자동 포함. `ErrorCategoryHttpStatusMapper` 가 static utility (`final class` + `static method`, 빈 아님)이라 추가 와이어링 없음. 출처: WebMvcTest API 문서.
- **`HandlerMethodValidationException` 경로 유효** — Spring Framework 7 (Spring Boot 4 코어)에 그대로 존재. `GlobalExceptionHandler` 가 이미 `MethodArgumentNotValidException` (RequestBody 검증) + `HandlerMethodValidationException` (메서드 파라미터 검증) 두 분기 override (코드 확인). 출처: Spring 7 API.

## Required Reading

- `docs/adr/0006-test-strategy.md` — 본 Plan 권위. 특히 §1 표(Web slice·Persistence slice·Mapper 행), §2(슬라이스 격리·필터 테스트 분리·H2 한정 동작 회피·Mapper 판정 질문), §3.2(테스트 의식 ArchUnit 규칙 — 본 Plan 이 통과 유지), §10(슬라이스는 빠른 `test`), §11.2(`@DataJpaTest` + `@Import` 예시).
- `docs/adr/0005-exception-error-response-policy.md` — `ProblemDetail` 스키마(code/timestamp/errors/instance), `MALFORMED_REQUEST`/`VALIDATION_FAILED`/도메인 예외 → HTTP 상태 매핑 정책.
- `docs/adr/0003-clean-architecture-ddd-hexagonal.md` — Driving Adapter 가 Input Port 를 통해서만 호출, Driven Adapter 가 Output Port 를 구현. 슬라이스 테스트의 mock 경계 근거.
- `docs/plans/done/PLAN-0006-A-test-infrastructure-archunit.md` — 본 Plan 이 의존하는 인프라(`integrationTest` task 분리, `@Tag("integration")` 정책, ArchUnit 규칙 11종 — 특히 `springboottest_is_localized`).
- `docs/plans/done/PLAN-0006-B-domain-application-tests.md` — 본 Plan 이 따를 *형식·DisplayName 컨벤션·Execution Notes 스타일*. 동시기 산출물.
- `docs/plans/done/PLAN-0005-A-error-model-skeleton.md`, `docs/plans/done/PLAN-0005-B-input-validation.md`, `docs/plans/done/PLAN-0005-C-observability-logging.md` — 본 Plan 이 검증할 *기존 행동* 의 출처(`GlobalExceptionHandler` 동작, `@Valid` 도입, `TraceIdFilter` 등록).
- `.claude/skills/plan-lifecycle.md` — 본 Plan 의 형식·archival 규약.
- `.claude/skills/clean-architecture.md` — Web/Persistence 어댑터의 책임·Port 결과 DTO 레이아웃·CQRS Coupling Boundary(`deleteById` row-count 0 → not-found 신호 근거).
- `CLAUDE.md` — §3 (ADR), §4 (Plan), §5 (Pipeline), §6 (Skills) — 본 Plan 의 운영 규약.
- `src/main/java/com/dunowljj/board/adapter/in/web/PostController.java` — 본 Plan 의 Web 슬라이스 검증 대상. 5개 엔드포인트 시그니처와 검증 어노테이션.
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/request/CreatePostRequest.java`, `UpdatePostRequest.java` — `@NotBlank`/`@NotNull`/`@Size` 위반 케이스 도출.
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/response/PostResponse.java`, `PostListResponse.java` — JSON 응답 형태.
- `src/main/java/com/dunowljj/board/adapter/in/web/exception/GlobalExceptionHandler.java` — 본 Plan 이 검증할 *행동* 의 권위 코드. `BusinessException` / `MethodArgumentNotValidException` / `HandlerMethodValidationException` / `handleExceptionInternal` / `Exception` 5 분기.
- `src/main/java/com/dunowljj/board/adapter/in/web/error/ErrorCategoryHttpStatusMapper.java` — `ErrorCategory` → `HttpStatus` 매핑.
- `src/main/java/com/dunowljj/board/adapter/in/web/observability/TraceIdFilter.java`, `TraceIdFilterConfig.java` — *Non-goals* 결정 근거(슬라이스 자동 포함 여부 미가정).
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostPersistenceAdapter.java` — 본 Plan 의 Persistence 슬라이스 검증 대상.
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostJpaEntity.java`, `PostJpaRepository.java`, `PostMapper.java` — 어댑터 round-trip 의 구성요소. Mapper 단독 테스트 작성 안 함을 결정한 근거(1:1 복사 확인).
- `src/main/java/com/dunowljj/board/application/port/in/CreatePostUseCase.java`, `GetPostUseCase.java`, `UpdatePostUseCase.java`, `DeletePostUseCase.java`, `ListPostsUseCase.java` — Web 슬라이스가 mock 할 Input Port 시그니처와 nested Command record.
- `src/main/java/com/dunowljj/board/application/port/in/result/PostListResult.java` — `list` 응답 매핑 형태.
- `src/main/java/com/dunowljj/board/application/common/PostPage.java` — Persistence 슬라이스 `findPage` 반환 형태.
- `src/main/java/com/dunowljj/board/common/error/ErrorCode.java`, `BusinessException.java`, `PostNotFoundException.java`, `InvalidPostContentException.java`, `ErrorCategory.java` — Web 슬라이스 어서트 대상 `code` 값.
- `src/main/java/com/dunowljj/board/domain/post/Post.java`, `PostContent.java` — 어댑터 테스트가 사용할 도메인 fixture 구성. PLAN-0006-B 의 `PostFixtures` 재사용 가능성 검토 — *순수 도메인 한정* 이라 Persistence 슬라이스에서도 import 가능(ArchUnit `domain_tests_are_pojo` 가 fixture 도 스캔하지만 fixture 가 Spring/JPA 의존 없으면 통과).
- `src/test/java/com/dunowljj/board/domain/post/PostFixtures.java` — 재사용 빌더. `aValidPost()`, `aReconstitutedPost(Long id)` 의 시그니처 확인.
- `src/test/java/com/dunowljj/board/architecture/HexagonalArchitectureTest.java`, `TestStrategyArchitectureTest.java` — 본 Plan 이 통과 유지해야 하는 ArchUnit 규칙 11종.
- `build.gradle` — `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `h2`, `spring-boot-starter-test` 가 `testImplementation` 또는 `runtimeOnly` 에 이미 있는지 확인(확인 완료 — 추가 의존성 불필요). 신규 의존성 필요 시 본 Plan 중단.
- `src/main/resources/application.yml` — H2 inmemory 설정(`jdbc:h2:mem:board`, `ddl-auto: create-drop`). Persistence 슬라이스가 사용할 환경.
- `src/main/java/com/dunowljj/board/BoardServiceApplication.java` — `@SpringBootApplication` 위치(base package 스캔 범위).

## Files to Touch

신규:

- `src/test/java/com/dunowljj/board/adapter/in/web/PostControllerTest.java` — `@WebMvcTest(PostController.class)` 슬라이스. 5개 엔드포인트 × (성공 + 검증 오류 + 도메인 예외 매핑) ≈ 15–20 테스트 메서드.
- `src/test/java/com/dunowljj/board/adapter/out/persistence/post/PostPersistenceAdapterTest.java` — `@DataJpaTest` + `@Import(PostPersistenceAdapter.class)` 슬라이스. 6 시나리오 ≈ 8–10 테스트 메서드.

수정: 없음. *Production 코드 변경 금지* — Plan-lifecycle Scope 규율. Risks #9 의 가정 위반 발견 시 별도 fix Plan.

## Implementation Hints

### Web 슬라이스 — `@WebMvcTest` 골격

```java
package com.dunowljj.board.adapter.in.web;

import com.dunowljj.board.adapter.in.web.exception.GlobalExceptionHandler;
import com.dunowljj.board.application.port.in.*;
// ...

@WebMvcTest(PostController.class)   // @ControllerAdvice 자동 포함 — @Import 불필요
class PostControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean CreatePostUseCase createPostUseCase;
    @MockitoBean GetPostUseCase getPostUseCase;
    @MockitoBean UpdatePostUseCase updatePostUseCase;
    @MockitoBean DeletePostUseCase deletePostUseCase;
    @MockitoBean ListPostsUseCase listPostsUseCase;

    @Test
    @DisplayName("게시글을 등록하면 201 과 PostResponse 본문을 돌려준다")
    void create_returns_201_with_response_body() throws Exception {
        Post fixture = PostFixtures.aReconstitutedPost(1L);
        when(createPostUseCase.create(any())).thenReturn(fixture);

        mockMvc.perform(post("/api/posts")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"title":"t","body":"b","author":"a"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.title").value("title"))
            .andExpect(jsonPath("$.author").value("author"));
    }

    @Test
    @DisplayName("작성자가 비어 있으면 400 과 VALIDATION_FAILED 를 돌려준다")
    void create_returns_400_when_author_blank() throws Exception {
        mockMvc.perform(post("/api/posts")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"title":"t","body":"b","author":""}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[*].field").value(hasItem("author")));
    }

    @Test
    @DisplayName("존재하지 않는 게시글을 조회하면 404 와 POST_NOT_FOUND 를 돌려준다")
    void getById_returns_404_when_not_found() throws Exception {
        when(getPostUseCase.getById(99L)).thenThrow(new PostNotFoundException(99L));
        mockMvc.perform(get("/api/posts/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    // 추가 — list size=101 → 400 VALIDATION_FAILED
    //         malformed JSON → 400 MALFORMED_REQUEST
    //         delete 성공 → 204
    //         list 성공 → 200 + PostListResponse 형태
}
```

JSON 어서트는 `jsonPath` 우선. 전체 객체 어서트가 필요하면 `objectMapper.readValue(...)` 후 record 동등성. *순서 의존 금지*.

### Persistence 슬라이스 — `@DataJpaTest` 골격

```java
package com.dunowljj.board.adapter.out.persistence.post;

@DataJpaTest
@Import(PostPersistenceAdapter.class)
class PostPersistenceAdapterTest {

    @Autowired PostPersistenceAdapter adapter;
    @Autowired PostJpaRepository repository;   // setup 검증 보조 (count, save 직접 등)

    @Test
    @DisplayName("새 게시글을 저장하면 id 가 부여되고 다시 조회할 수 있다")
    void save_assigns_id_and_findById_returns_same_post() {
        Post saved = adapter.save(Post.create("t", "b", "a"));
        assertThat(saved.getId()).isNotNull();

        Optional<Post> found = adapter.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("t");
        assertThat(found.get().getAuthor()).isEqualTo("a");
    }

    @Test
    @DisplayName("존재하지 않는 ID 를 삭제하면 row-count 0 을 돌려준다")
    void deleteById_returns_zero_when_not_found() {
        assertThat(adapter.deleteById(9_999L)).isZero();
    }

    @Test
    @DisplayName("findPage 는 createdAt 내림차순으로 페이지를 돌려준다")
    void findPage_returns_items_ordered_by_createdAt_desc() {
        LocalDateTime older = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 1, 2, 0, 0);
        repository.save(new PostJpaEntity(null, "old", "b", "a", older, older));
        repository.save(new PostJpaEntity(null, "new", "b", "a", newer, newer));

        PostPage page = adapter.findPage(0, 10);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).extracting(Post::getTitle)
            .containsExactly("new", "old");
    }

    // 추가 — save 갱신(같은 id round-trip) → count 유지
    //         findById not-found → empty
    //         findPage 빈 → items empty, totalElements 0
    //         deleteById 존재 → 1
    //         findPage 페이지 2 → 두 번째 페이지 items 검증
}
```

**정렬 검증의 setup 은 `PostJpaRepository` 를 통해 `PostJpaEntity` 를 직접 저장** — `adapter.save(Post.create(...))` 경로는 `LocalDateTime.now()` 를 호출해 같은 ms 충돌 위험이 있고 `Thread.sleep` 같은 비결정·시간 결합을 강요한다. setup 은 *데이터를 테이블에 넣는 것*이 목적이고, 검증 대상은 `findPage` 의 정렬·페이지네이션이므로 두 경로를 분리해도 의미 손실 없음. `PostJpaEntity` 의 생성자 시그니처(`(id, title, body, author, createdAt, updatedAt)`) 는 Implementer 가 작성 직전 확인 — `@AllArgsConstructor` 인지 빌더인지에 따라 호출이 달라짐.

### 작성 한계 — 테스트하지 않는 것 (ADR-0006 §8)

- Mapper 단독 테스트 — Scope 결정.
- Lombok 생성자/getter 테스트 — `@RequiredArgsConstructor` 산출물.
- `PostJpaRepository` JPQL 자체 테스트 — 어댑터 round-trip 으로 충분.
- `TraceIdFilter` 슬라이스 검증 — 본 Plan 범위 밖.
- `BoardServiceApplicationTests.contextLoads` 와 중복되는 *full context* 검증 — E2E 영역.

## Execution Notes

<!-- 실행 중 비자명한 결정만 시간순 append. 사소한 구현 디테일은 적지 않는다. -->

- 2026-05-12 — Spring Boot 4.0.2 가 슬라이스 어노테이션을 모듈별 패키지로 재배치. `@WebMvcTest` 는 `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, `@DataJpaTest` 는 `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`. 기존 `org.springframework.boot.test.autoconfigure.web.servlet` / `...orm.jpa` 경로는 사라짐. PLAN-0006-D 도 동일 import 사용 필요.
- 2026-05-12 — `PostPersistenceAdapterTest.deleteById_returns_one_when_row_exists` 에서 row-count 1 확인 이후 `adapter.findById(id)` 가 *여전히 Optional.present* 였음. `PostJpaRepository.deletePostById` 의 `@Modifying` 이 `clearAutomatically=false` 기본값이라 영속성 컨텍스트 1차 캐시가 비워지지 않은 결과. Plan Non-goals 가 "`@Modifying` + `clearAutomatically` 동작 검증 — 어댑터 round-trip 으로 충분(row-count 가 보장)" 으로 *명시 제외* 하므로 후속 `findById` 어서트를 제거하고 row-count 어서트만 유지. JPA 1차 캐시 동작 자체는 production 결함이 아니라 *서비스 호출자가 새 트랜잭션에서 조회한다*는 호출 패턴 전제(`PostCommandService.delete` 는 `@Transactional` 종료 후 1차 캐시 폐기). 별도 production 수정 불요.
- 2026-05-14 — Codex 리뷰 4건(P2×3, P3×1) 반영. (a) PostControllerTest 의 `create`/`update` 가 `any()` 만 어서트해 컨트롤러가 입력을 잘못 옮겨도 못 잡던 약점 → `ArgumentCaptor` 로 `CreatePostCommand.title/body/author`, `UpdatePostCommand.id/title/body` 필드 검증. (b) PostPersistenceAdapterTest `save_updates_in_place_when_id_present` 가 어댑터 반환값과 count 만 봐 어댑터가 입력을 그대로 돌려주기만 해도 통과하던 약점 → `adapter.findById(id)` 로 재조회 후 title/body/createdAt/updatedAt 갱신 검증. JPA merge 가 관리 엔티티의 상태를 갱신하므로 같은 세션의 L1 캐시 조회만으로도 *save 누락* 결함을 감지 가능. EntityManager flush/clear 는 본 검증 범위에 불필요(어댑터 *공개 표면*만으로 충분, 우회 SQL 발행 검증 필요 없음). (c) ProblemDetail 스키마 helper(`expectProblemDetailBase`) 도입, 모든 오류 테스트에 status/title/instance/code/timestamp 동일 어서트. (d) `GET /api/posts/not-a-number` → 400 + MALFORMED_REQUEST 경로 변환 오류 테스트 추가.
- 2026-05-16 — `PostPersistenceAdapterTest` 의 `save_assigns_id_and_findById_returns_same_post` / `save_updates_in_place_when_id_present` 에 `em.flush() + em.clear()` 추가. 두 테스트 모두 `adapter.save` 직후 `adapter.findById` 가 JPA 1차 캐시 hit 으로 끝나 *DB round-trip 을 사실상 검증하지 못함* 을 식별. flush+clear 로 후속 `findById` 가 실제 SELECT 를 발행하도록 강제. **2026-05-14 노트의 판단 정정** — "EntityManager flush/clear 는 본 검증 범위에 불필요" 는 *save 누락 결함 감지* 만 본 좁은 판단이었음. 영속성 어댑터 슬라이스의 핵심 책임은 round-trip 자체이므로 강제가 정확. **초기 직관 정정** — 검토 초기에 "어댑터 테스트가 JPA 에 의존하는 것은 부자연스럽다" 는 이유로 `EntityManager` 도입을 보류했으나, `@DataJpaTest` 가 Hibernate · Spring Data JPA · 1차 캐시 의미론을 *이미* 슬라이스에 끌어오고 있어 "JPA 독립" 은 환상. 슬라이스 안에서 어댑터를 검증한다는 것은 그 JPA 기반을 명시적으로 사용한다는 것과 같다. `TestEntityManager` 가 아니라 JPA 표준 `@PersistenceContext EntityManager` 채택 — 현재 사용이 flush/clear 두 줄에 한정되므로 `persistFlushFind` 같은 콤보가 다수 등장하기 전까지 추가 추상화 도입은 보류.
- 2026-05-14 — **Production 갭 발견 — ADR-0005 위반.** ADR-0005 §4 (L102·116·134) 가 ProblemDetail 응답에 `type: "about:blank"` 명시하나 실제 응답에 `type` 필드가 누락(Spring Jackson 이 기본값일 때 응답에서 omit). Plan Risks #6 (production 수정 금지) 에 따라 본 PR 에서 미수정. helper 에서 `type` 어서트 제외하고 사유 주석. **별도 fix Plan 필요** — ProblemDetail 직렬화가 `type` 을 always-emit 하도록 production 수정(예: enrich() 에서 `setType(URI.create("about:blank"))` 명시, 또는 Jackson 설정 변경). 수정 후 helper 에 `$.type` 어서트 복원.
