# PLAN-0006-D: E2E 골든 플로우 테스트 (`@SpringBootTest`)

PLAN-0006-A 가 마련한 인프라(`integrationTest` task 분리·`@Tag("integration")`·ArchUnit 11종) 와 PLAN-0006-B/C 가 채운 Domain·Application·Web slice·Persistence slice 위에서, ADR-0006 §1 표의 *End-to-End* 행을 채운다. 도메인당 1–2 개의 골든 플로우(§2 "E2E는 희소하게")로 *어셈블리*를 검증한다 — 라우팅 + 검증 + 도메인 예외 매핑 + `ProblemDetail` + `TraceIdFilter` 실제 필터 체인 + 실제 영속성. ADR-0006 §5 가 "PLAN-0006 에서 도메인별 결정" 으로 위임한 *E2E 트랜잭션 cleanup 정책* 도 본 Plan 에서 명시 결정.

## Goal

`com.dunowljj.board.e2e.*` 패키지에 1 개 E2E 통합테스트 클래스를 작성한다. 구체적으로:

1. `@SpringBootTest` + `@AutoConfigureMockMvc` — full context. `TraceIdFilter` (`FilterRegistrationBean`), `GlobalExceptionHandler`, `PostPersistenceAdapter` (H2) 모두 실제 빈으로 동작.
2. `@Tag("integration")` 부착 → `integrationTest` task 실행 대상 (PLAN-0006-A 인프라). 빠른 `test` task 에서 자동 제외.
3. Post 도메인의 골든 플로우 2 개 — (a) CRUD 정상 플로우 (Create → Get → Update → List → Delete → Get-404), (b) 오류 + Trace 헤더 플로우 (검증 실패 `ProblemDetail` + `X-Trace-Id` 응답 헤더 echo · 자동 생성).
4. ADR-0006 §5 의 3 옵션 중 하나를 *명시 결정* — *옵션 (c) 자체 cleanup* 채택. 사유 Plan 본문 기록.

## Scope

### Included — E2E 테스트 클래스 1 개

`src/test/java/com/dunowljj/board/e2e/PostE2EIT.java` — `<Aggregate>E2EIT` 형식 (ADR-0006 §6 의 `IT` 접미사). `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Tag("integration")`. 두 `@Test` 메서드.

#### Flow 1 — CRUD 골든 플로우 (단일 `@Test`)

하나의 시나리오를 하나의 테스트로 묶는다 — *플로우 자체가 약속*이므로 분할 시 의미 손실. Given/When/Then 빈 줄 구획으로 가독성 확보.

- `POST /api/posts` → 201, 응답 본문 `PostResponse`. `id` 추출.
- `GET /api/posts/{id}` → 200, `author` 일치 (영속화 확인 — 이전 요청이 *커밋된 상태로* 다음 요청에 보임을 의미).
- `PUT /api/posts/{id}` → 200, `title`/`body` 갱신 반영 (절대값 비교 금지 — Risks #3).
- `GET /api/posts` (`page=0&size=10`) → 200, `totalElements == 1`, `posts[0].id == id`.
- `DELETE /api/posts/{id}` → 204.
- `GET /api/posts/{id}` → 404, `code=POST_NOT_FOUND`.
- `X-Trace-Id` 헤더 검증은 *대표 응답* 한정 — 최초 `POST` (성공 경로 대표) 와 최종 `GET` 404 (오류 경로 대표). 모든 요청 어서트는 노이즈 (필터-wide invariant 라 두 케이스로 충분).

#### Flow 2 — 오류 + Trace echo / 자동 생성 (단일 `@Test`, 두 어서션 그룹)

- `POST /api/posts` (`body={"title":"","body":"","author":""}`, 헤더 `X-Trace-Id: e2e-trace-001`) → 400, `code=VALIDATION_FAILED`, 응답 헤더 `X-Trace-Id: e2e-trace-001` *echo*.
- `GET /api/posts/99999` (헤더 미공급) → 404, `code=POST_NOT_FOUND`, 응답 헤더 `X-Trace-Id` 가 *UUID 형식* (TraceIdFilter 자동 생성 경로).

두 어서션은 *Trace 헤더의 두 분기*(echo/자동 생성) 라는 동일 약속의 두 면이라 한 테스트로 묶음.

#### 트랜잭션 cleanup 정책 — *명시 결정: 옵션 (c) 자체 cleanup*

ADR-0006 §5 가 `@SpringBootTest` 의 cleanup 을 3 옵션으로 열어두고 "PLAN-0006 에서 도메인별 결정" 으로 위임. 본 Plan 의 결정:

- **(a) `@Transactional` — 거부.** 클래스/메서드에 `@Transactional` 부착 시 모든 MockMvc 요청이 *같은 테스트 트랜잭션* 안에서 돌아 실제 COMMIT 이 발생하지 않음. ADR-0006 §5 의 "비동기·multi-thread 경로에선 부정확" 의 일반화 — *영속성 가시성을 가로질러* 검증해야 하는 E2E 본질과 충돌. CRUD 플로우의 `POST` → `GET` 가 *커밋된 데이터*를 읽는 것이 핵심 검증 포인트.
- **(b) `@Sql` — 거부.** 본 도메인은 단일 테이블이라 `@Sql` 파일 추가 가치 음(陰). H2 한정 SQL 작성도 ADR-0006 §2 의 "H2 한정 동작 의존 금지" 와 마찰.
- **(c) 자체 cleanup — *채택*.** `@AfterEach` 가 `JdbcTemplate.update("delete from posts")` 로 테이블 정리. JDBC auto-commit 이라 Spring 트랜잭션 관리 자체가 불필요 — 가장 적은 가정.

  **거부한 대안**: `@AfterEach @Transactional` + `EntityManager.executeUpdate()` — Spring TestContext 의 `TransactionalTestExecutionListener` 는 *테스트 메서드 기준*으로 트랜잭션을 감싸므로, 테스트 메서드가 비-`@Transactional` 이면 `@AfterEach` 도 트랜잭션 밖에서 실행됨. `@Transactional` 어노테이션은 *프록시 호출* 시 적용되는데 JUnit 이 라이프사이클 메서드를 직접 호출하므로 어노테이션이 무력화됨. `executeUpdate()` 는 active TX 필요 → `TransactionRequiredException` 또는 silent no-op. `TransactionTemplate` 으로 감싸는 안도 가능하지만 JdbcTemplate 한 줄 대비 가치 음.

  **table name 결정**: `posts`. `PostJpaEntity` 의 `@Table(name="posts")` 확인 필요 — 다를 경우 Implementation Hints 수정.

### Not Included

- Validation 케이스 망라 — slice (`PostControllerTest`) 가 이미 검증. E2E 는 *하나의 대표 케이스* (빈 author/title/body) 만 — *어셈블리*가 동작함만 확인.
- 도메인 예외 케이스 망라 — slice 가 검증. E2E 는 `POST_NOT_FOUND` 1 건만.
- `ProblemDetail` 스키마의 모든 필드 키 어서트 — slice 가 검증. E2E 는 `code` + 응답 헤더 `X-Trace-Id` 만 어서트.
- **MDC 내용 검증** — MDC 는 thread-local 이고 `TraceIdFilter` 의 `finally` 에서 정리되어 E2E 시점에 *관찰 불가*. `ListAppender` 등 추가 인프라 도입은 가치 대비 비용 음(陰) — `TraceIdFilter` 의 *단독* 테스트(별도 Plan) 로 검증해야 정확. *응답 헤더* `X-Trace-Id` 검증으로 필터가 *체인을 통과했음* 만 확인.
- `TraceIdFilter` 의 query sanitization 검증 — 필터 단독 단위 테스트 영역 (PLAN-0006-C Non-goals 와 동일).
- `TraceIdFilter` 단독 단위 테스트 (`MockFilterChain` + MDC put/remove 직접 검증) — 별도 Plan. ADR-0006 §2 가 "필터 테스트는 분리 — slice 자동 포함 여부에 의존하지 않는다" 를 명시한 자리.
- `TestRestTemplate` / `RestAssured` 도입 — ADR-0006 §1 표가 MockMvc 명시 (§2 가 `TestRestTemplate` 을 E2E "전용"이라 했지만 §1 표가 *구체 도구로* MockMvc 채택). WebEnvironment.MOCK (기본) 유지.
- Testcontainers / 실 DB — ADR-0006 §2 가 *목표*로 결정, 별도 Plan.
- 다중 도메인 — 현재 Post 단일.
- Spring Security 인증/인가 — ADR-0006 §9 보류.
- 운영 코드 변경 — Plan-lifecycle Scope 규율.

## Related ADRs

- ADR-0006 (테스트 전략) — 본 Plan 권위. 특히 §1 표(End-to-End 행 — `@SpringBootTest` + MockMvc + full + H2), §2 ("E2E는 희소하게"·"필터 테스트 분리"·"H2 한정 동작 회피"), §5 (`@SpringBootTest` cleanup 옵션 — 본 Plan 에서 (c) 채택), §6 (`IT` 접미사·패키지 미러), §10 (`integrationTest` task 카테고리), §11.3 ArchUnit 의 `springboottest_is_localized` (`..e2e..` 또는 `BoardServiceApplicationTests` 한정).
- ADR-0005 (예외/에러 응답 정책) — E2E 어셈블리가 검증할 `ProblemDetail` 스키마(`code` 필드).
- ADR-0003 (Clean/Hexagonal + DDD + CQRS) — Driving·Driven 어댑터가 실제로 합쳐졌을 때 ADR 경계가 유지되는지의 *어셈블리* 검증.

## Acceptance Criteria

- `./gradlew test` 는 기본 task 시간 영향 없음 — E2E 는 `@Tag("integration")` 부착으로 `test` 에서 제외 (PLAN-0006-A 의 `excludeTags 'integration'`).
- `./gradlew integrationTest` 가 신규 `PostE2EIT` 클래스를 *발견·실행·통과*. 기존 `BoardServiceApplicationTests.contextLoads`, `IntegrationSmokeTest` 도 함께 green.
- `./gradlew check` 가 `test` + `integrationTest` 두 task 를 모두 호출하고 통과.
- `PostE2EIT` 는 `com.dunowljj.board.e2e` 패키지에 위치 — `springboottest_is_localized` ArchUnit 규칙 통과.
- `PostE2EIT` 는 `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Tag("integration")` 부착. `@WebMvcTest` / `@DataJpaTest` 사용 *금지*. `@AutoConfigureMockMvc(addFilters = false)` 사용 *금지* (Risks #1).
- 두 `@Test` 메서드 — `crud_golden_flow_completes_create_to_delete_with_404`, `error_flow_echoes_or_generates_trace_id`. `@DisplayName` 한국어, *동작/규칙 문장* (ADR-0006 §4·§6).
- `X-Trace-Id` 헤더 검증은 *대표 응답* 한정 — CRUD 플로우의 최초 `POST` 와 최종 `GET` 404, 오류 플로우의 두 케이스(supplied echo + 자동 생성). 4 어서션. 모든 요청 어서트는 노이즈.
- 트랜잭션 cleanup: `@AfterEach` 가 `JdbcTemplate.update("delete from posts")` 실행. 클래스/플로우/cleanup 메서드 모두 `@Transactional` 부착 *금지* (JDBC auto-commit 이라 불필요). 사유 주석 1줄.
- 기존 `test` / `integrationTest` 의 모든 테스트 + 본 Plan 의 신규 2 E2E green. 전체 `check` 통과. (테스트 개수 절대값은 다른 PR 의 영향으로 변동하므로 어서트 대상 아님.)
- ArchUnit 11 규칙 통과 유지. 신규 ArchUnit 규칙 추가 *없음*.
- 신규 의존성 추가 *없음* — `spring-boot-starter-test` 가 MockMvc·JsonPath·Hamcrest 를 이미 끌어옴 (확인 완료).
- 운영 코드 수정 *없음*. 발견 사항은 Execution Notes 기록 후 별도 fix Plan.
- 한 테스트 = 한 어서션 그룹 (ADR-0006 §4). CRUD 는 *플로우 자체가 단일 약속*, 오류 플로우의 echo/자동 생성은 *Trace 헤더 분기 한 쌍*.

## ADR Required

no — ADR-0006 §1·§2·§5·§6·§10·§11.3 가 모든 결정의 권위. 본 Plan 은 §5 가 "PLAN-0006 에서 결정" 으로 위임한 *E2E cleanup 정책*을 옵션 (c) 로 *적용*. 옵션 (c) 선택은 시스템 결정이 아니라 ADR-0006 §5 가 열어둔 3 선택지 중 1 을 사유와 함께 채택한 것 — Plan 본문 기록으로 충분. 다른 도메인이 추가될 때 동일 정책이 반복되거나 다른 도메인 특성상 (a)/(b)/(c) 가 갈리는 일이 생기면 그 시점에 ADR 격상 검토.

## Risks

1. **`@AutoConfigureMockMvc` 의 필터 적용.** 기본 동작은 *모든 등록된 필터*(`FilterRegistrationBean` 포함) 적용. `addFilters = false` 명시 시 빠짐. 본 Plan 은 *명시 false 금지* — `X-Trace-Id` 검증이 핵심.
2. **`@AfterEach` cleanup 의 트랜잭션 경계.** 본 Plan 은 `JdbcTemplate.update("delete from posts")` 채택 — JDBC auto-commit. Spring TestContext 의 `TransactionalTestExecutionListener` 가 *테스트 메서드 기준*으로만 동작하므로 `@AfterEach @Transactional` 은 *프록시 미경유* 라 무력화 (테스트 인스턴스는 AOP 프록시 대상 아님). 검증: 두 `@Test` 가 *어떤 순서로 실행되어도* `count()==0` 으로 시작해야 — `@TestMethodOrder` 의존 금지. 첫 테스트가 cleanup 실패 시 두 번째 테스트가 stale row 로 시작해 어서트 실패 → cleanup 동작 자체가 함께 검증됨.
3. **`updatedAt` 절대값 비교 금지.** PLAN-0006-C 와 동일 — `LocalDateTime.now()` 의 환경 의존. *대소 관계* (`updatedAt > createdAt`) 또는 *값이 갱신됨* (`!= 초기값`) 만 어서트. 시각 패턴 어서트 금지.
4. **응답 본문의 `X-Trace-Id` 위치.** `TraceIdFilter` 는 *응답 헤더*에 셋(`response.setHeader(HEADER, traceId)`). `ProblemDetail` body 안에 trace id 가 들어가는지는 `GlobalExceptionHandler` 동작에 의존. 본 Plan 은 *응답 헤더*만 어서트 — body 의 traceId 필드 어서트는 slice 또는 별도 Plan.
5. **ApplicationContext 공유.** Spring Boot 는 동일 구성의 컨텍스트를 캐시. `BoardServiceApplicationTests`/`IntegrationSmokeTest` 가 기본 `@SpringBootTest` 면 `PostE2EIT` 와 컨텍스트 공유 가능 — 셋 다 stateless·cleanup 보장이라 누수 없음. 본 Plan 은 컨텍스트 분리(`@DirtiesContext`) *금지* — 빌드 시간만 늘림.
6. **운영 코드 가정 위반 발견 시.** 본 Plan 실행 중 어셈블리에서 production 결함 발견(예: `X-Trace-Id` 가 ProblemDetail 응답에 *세팅 안 됨* — `GlobalExceptionHandler` 가 별도 `ResponseEntity` 를 만들어 필터 헤더가 덮이는 경로)이 드러나면 — *Risks Execution Notes 기록* + fix Plan 분리. 본 Plan 에서 production 수정 금지.

### Pre-resolved

- **MockMvc 채택, `TestRestTemplate`/`RestAssured` 거부.** ADR-0006 §1 표 명시.
- **`WebEnvironment.MOCK` (기본) 채택, `RANDOM_PORT` 거부.** 사유: 실제 HTTP socket 은 검증 가치 음 — DispatcherServlet·Filter·Handler 까지 도달 여부가 *어셈블리*의 핵심이고 MockMvc 가 이를 통과. Socket 자체는 Spring Framework 책임.
- **패키지 위치 `com.dunowljj.board.e2e` 강제.** `TestStrategyArchitectureTest.springboottest_is_localized` 가 빌드 시점에 강제. 다른 위치 시 ArchUnit 빨개짐.
- **클래스명 `PostE2EIT`.** ADR-0006 §6 의 `<ProductionClass>IT` — E2E 는 단일 production 클래스가 아니라 *도메인 어셈블리*이므로 `<Aggregate>E2EIT` 변형 채택.
- **cleanup 메커니즘 `JdbcTemplate.update(...)`.** 거부한 대안: `@AfterEach @Transactional` + `EntityManager.executeUpdate()` — Spring TestContext 의 `TransactionalTestExecutionListener` 가 *테스트 메서드* 기준으로 트랜잭션을 감싸고, `@AfterEach` 의 `@Transactional` 은 JUnit 이 라이프사이클을 *프록시 미경유* 로 호출하므로 무력화. `TransactionRequiredException` 또는 silent no-op 위험. `TransactionTemplate` 도 가능하지만 `JdbcTemplate` 한 줄 대비 가치 음. 출처: Spring TestContext transaction docs.
- **`X-Trace-Id` 헤더 어서트는 대표 응답 한정.** 거부한 대안: 모든 MockMvc 요청마다 `header().exists("X-Trace-Id")` — 필터-wide invariant 라 반복 어서트가 노이즈만 증가. 4 어서션(CRUD: POST + 404; 오류 플로우: echo + 자동 생성)으로 *분기 망*은 커버됨.
- **테스트 개수 절대값 비-AC.** 거부한 대안: "기존 N tests + 신규 M tests 통과" — 다른 PR 의 영향으로 N 변동. AC 는 "기존 전체 + 신규 2 green" 으로 구조 어서트만.

## Required Reading

- `docs/adr/0006-test-strategy.md` — 본 Plan 권위. 특히 §1 (End-to-End 행), §2 ("E2E는 희소하게"·"필터 테스트 분리"·"H2 한정 동작 회피"), §5 (`@SpringBootTest` cleanup 옵션 — 본 Plan 결정), §6 (`IT` 접미사), §10 (`integrationTest` task), §11.3 (`springboottest_is_localized`).
- `docs/adr/0005-exception-error-response-policy.md` — `ProblemDetail` 스키마(`code` 필드).
- `docs/adr/0003-clean-architecture-ddd-hexagonal.md` — 어셈블리 경계.
- `docs/plans/done/PLAN-0006-A-test-infrastructure-archunit.md` — `integrationTest` task · `@Tag("integration")` · `springboottest_is_localized` 규칙.
- `docs/plans/done/PLAN-0006-B-domain-application-tests.md` — `@DisplayName` 한국어 컨벤션·테스트 명명.
- `docs/plans/done/PLAN-0006-C-web-persistence-tests.md` — slice 측 검증 항목(망라 어서트 금지 근거), `TraceIdFilter` 슬라이스 미포함 결정, ProblemDetail `type` 필드 누락 production 갭.
- `docs/plans/done/PLAN-0005-C-observability-logging.md` — `TraceIdFilter` 동작 명세 (`X-Trace-Id` 헤더·MDC 키·정리·`FilterRegistrationBean` 등록·order=-101).
- `docs/plans/done/PLAN-0005-A-error-model-skeleton.md`, `PLAN-0005-B-input-validation.md` — `ProblemDetail` · `code` 값 출처.
- `.claude/skills/plan-lifecycle.md` — 본 Plan 형식·archival.
- `.claude/skills/clean-architecture.md` — 어셈블리에서 유지되어야 하는 layer/port 경계.
- `CLAUDE.md` — §3 (ADR), §4 (Plan), §5 (Pipeline), §6 (Skills).
- `build.gradle` — `test` / `integrationTest` task 분기, `excludeTags 'integration'` / `includeTags 'integration'`. 신규 의존성 불필요 확인 (`spring-boot-starter-test` 가 MockMvc·JsonPath·Hamcrest 포함).
- `src/main/resources/application.yml` — H2 인메모리(`jdbc:h2:mem:board`, `ddl-auto: create-drop`). `observability.query.value-allowlist` 가 `TraceIdFilter` 설정.
- `src/main/java/com/dunowljj/board/BoardServiceApplication.java` — `@SpringBootApplication` 위치(컨텍스트 부트 진입점).
- `src/main/java/com/dunowljj/board/adapter/in/web/PostController.java` — 5 엔드포인트 시그니처.
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/request/CreatePostRequest.java`, `UpdatePostRequest.java` — 요청 형태. 빈 `title`/`author` → `@NotBlank` 위반 케이스 도출 (`body` 는 `@NotNull @Size` 라 빈 문자열 통과 — 어서트는 `code=VALIDATION_FAILED` 한정으로 충분).
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/response/PostResponse.java`, `PostListResponse.java` — 응답 형태(`id`, `posts[]`, `totalElements`).
- `src/main/java/com/dunowljj/board/adapter/in/web/exception/GlobalExceptionHandler.java` — `ProblemDetail` 분기. `X-Trace-Id` 헤더가 *응답 헤더에 남는지* (필터 chain 외부에서 새 ResponseEntity 가 생성되어 헤더가 덮이지 않는지) 확인 — Risks #6.
- `src/main/java/com/dunowljj/board/adapter/in/web/observability/TraceIdFilter.java`, `TraceIdFilterConfig.java` — 필터 등록 방식(`FilterRegistrationBean`), `X-Trace-Id` 헤더 셋 시점, 헤더 echo / 자동 생성 분기 로직(`isValid` → echo, 그 외 → `UUID.randomUUID()`).
- `src/main/java/com/dunowljj/board/common/error/ErrorCode.java` — `POST_NOT_FOUND`, `VALIDATION_FAILED` 등.
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostJpaEntity.java` — cleanup raw SQL `delete from posts` 의 *테이블명* 출처 (`@Table(name = "posts")`).
- `src/test/java/com/dunowljj/board/BoardServiceApplicationTests.java`, `IntegrationSmokeTest.java` — 기존 `@Tag("integration")` 테스트 두 건. 컨텍스트 공유 가능성 (Risks #5) · 컨벤션 참고.
- `src/test/java/com/dunowljj/board/adapter/in/web/PostControllerTest.java` — slice 검증 항목 (E2E 가 *재검증하지 않을* 항목 식별 근거).
- `src/test/java/com/dunowljj/board/adapter/out/persistence/post/PostPersistenceAdapterTest.java` — slice 검증 항목 (E2E 가 *재검증하지 않을* 항목 식별 근거).
- `src/test/java/com/dunowljj/board/architecture/TestStrategyArchitectureTest.java` — `springboottest_is_localized` 가 강제하는 패키지 위치.

## Files to Touch

신규:
- `src/test/java/com/dunowljj/board/e2e/PostE2EIT.java` — `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Tag("integration")`. 두 `@Test` 메서드.

수정: 없음. *운영 코드 변경 금지* (Plan-lifecycle Scope 규율).

## Implementation Hints

### 골격

```java
package com.dunowljj.board.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class PostE2EIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    // ADR-0006 §5 옵션 (c) — JDBC auto-commit. @AfterEach @Transactional 은
    // 테스트 인스턴스가 AOP 프록시 대상이 아니라 무력화되므로 의도적으로 사용 안 함.
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from posts");
    }

    @Test
    @DisplayName("게시글을 등록·조회·수정·목록·삭제하면 마지막 조회에서 404 를 돌려준다")
    void crud_golden_flow_completes_create_to_delete_with_404() throws Exception {
        // Given
        String createBody = """
            {"title":"hello","body":"world","author":"alice"}
            """;

        // When: POST → 201
        String createdJson = mockMvc.perform(post("/api/posts")
                .contentType(APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("hello"))
            .andExpect(jsonPath("$.author").value("alice"))
            .andExpect(header().exists("X-Trace-Id"))
            .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(createdJson);
        long id = created.get("id").asLong();
        String createdAt = created.get("createdAt").asText();

        // GET → 200, 영속 확인 (X-Trace-Id 어서트는 대표 응답에서만 — Scope 결정)
        mockMvc.perform(get("/api/posts/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.author").value("alice"));

        // PUT → 200, 갱신 반영, createdAt 보존
        mockMvc.perform(put("/api/posts/" + id)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"title":"hello-v2","body":"world-v2"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("hello-v2"))
            .andExpect(jsonPath("$.createdAt").value(createdAt));

        // GET list → 1건
        mockMvc.perform(get("/api/posts").param("page", "0").param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.posts[0].id").value(id));

        // DELETE → 204
        mockMvc.perform(delete("/api/posts/" + id))
            .andExpect(status().isNoContent());

        // GET → 404 POST_NOT_FOUND (오류 경로 대표 — X-Trace-Id 어서트)
        mockMvc.perform(get("/api/posts/" + id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
            .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @DisplayName("검증 오류 응답은 supplied X-Trace-Id 를 echo 하고 미공급 시 자동 생성한다")
    void error_flow_echoes_or_generates_trace_id() throws Exception {
        // 검증 실패 + supplied 헤더 echo
        mockMvc.perform(post("/api/posts")
                .header("X-Trace-Id", "e2e-trace-001")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"title":"","body":"","author":""}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(header().string("X-Trace-Id", "e2e-trace-001"));

        // not-found + 미공급 시 UUID 자동 생성
        mockMvc.perform(get("/api/posts/99999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
            .andExpect(header().string("X-Trace-Id",
                    matchesPattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")));
    }
}
```

### 주의

- `objectMapper.readTree(...)` 로 응답 파싱. `JsonPath` 라이브러리 직접 호출 대신 — slice 테스트와 컨벤션 일치, 추가 import 최소화.
- `@AfterEach` 에 `@Transactional` 부착 *금지* — 라이프사이클 메서드의 어노테이션은 무력화됨 (Risks #2). 클래스/플로우 메서드 부착도 금지 (CRUD 플로우의 commit 가시성 깨짐).
- `header().exists(...)` 와 `header().string(...)` 두 어서션 모두 `MockMvcResultMatchers`.
- `matchesPattern(...)` 는 Hamcrest. `spring-boot-starter-test` 가 끌어옴.
- `PostJpaEntity` 의 `@Table(name = "posts")` 확인 — cleanup 의 raw SQL `delete from posts` 가 이 테이블명에 의존. 다를 경우 cleanup SQL 조정 (확인 완료: `posts`).

### 작성 한계 (ADR-0006 §8 + 본 Plan Non-goals)

- 모든 ProblemDetail 필드 어서트 금지 — `code` + `X-Trace-Id` 만.
- 모든 도메인 예외 케이스 망라 금지 — `POST_NOT_FOUND` 1 건만.
- MDC 내용 어서트 금지 — *응답 헤더* 만.
- `@DirtiesContext` 부착 금지 — 빌드 시간 페널티만 발생.
- `@TestMethodOrder` 사용 금지 — 두 `@Test` 가 독립적이어야 함.

## Execution Notes

<!-- 실행 중 비자명한 결정만 시간순 append. 사소한 구현 디테일은 적지 않는다. -->
