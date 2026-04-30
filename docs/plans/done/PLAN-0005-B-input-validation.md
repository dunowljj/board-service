# PLAN-0005-B: 입력 검증 표준화 (Bean Validation 도입)

<!-- 상층: 승인 게이트 — 방향/범위/완료 기준 -->

## Goal

ADR-0005 §5의 *형식·구조 (Pure form)* 검증을 Bean Validation(Jakarta Validation API + Hibernate Validator)으로 도입한다. 본 Plan이 직접 검증을 *추가*하는 대상은 **`@RequestBody` DTO** (모든 필드)와 **`@RequestParam`** (`page`, `size`)이다. **`@PathVariable Long id`** 는 본 Plan 범위 외 — 타입 변환 실패는 `MALFORMED_REQUEST`로 이미 처리되고(PLAN-0005-A), 숫자 범위 검증(`@Positive` 등)은 의도적으로 도입하지 않는다(음수/0이면 `PostNotFoundException` 404로 자연 처리). 검증 실패 시 ProblemDetail body에 `VALIDATION_FAILED` 코드 + `errors[]` 커스텀 property로 응답한다.

부수 효과로 PLAN-0005-A의 P1 회귀(`/api/posts?page=-1` → 500)가 자연 해결된다 (`@Min(0) int page` → `HandlerMethodValidationException` → 400 VALIDATION_FAILED).

## Scope

PLAN-0005-A처럼 두 phase로 구성한다. **리뷰 포커스가 다르므로 별개 커밋으로 분리**하되, **두 phase는 같은 PR 안에서 함께 머지**한다 (Phase 1 단독 머지 시 핸들러는 새 응답 모양을 알지만 어노테이션이 없어 실효 변화 없음 — 회귀는 없으나 코드가 절반만 의미 있는 상태가 되므로 비분리 머지가 자연스러움).

### Phase 1 — 검증 인프라 (dependency / ErrorCode / 핸들러)

- `build.gradle`에 `spring-boot-starter-validation` 의존성 추가.
- `common/error/ErrorCode`에 `VALIDATION_FAILED` 항목 추가 (`INVALID_INPUT` 카테고리, defaultMessage `"입력 형식이 올바르지 않습니다"`).
- `GlobalExceptionHandler`:
  - `handleMethodArgumentNotValid` override (`@RequestBody` + `@Valid` 실패 시 호출) → ProblemDetail body에 `code=VALIDATION_FAILED` + `errors[]` 채움.
  - `handleHandlerMethodValidationException` override (Spring 6.1+의 `@RequestParam` 검증 실패 시 호출) → 동일 처리. **`HandlerMethodValidationException`을 명시적으로 catch한다** — 그렇지 않으면 부모 클래스가 ProblemDetail body만 만들어 `handleExceptionInternal`로 흘려보내고, PLAN-0005-A의 4xx → `MALFORMED_REQUEST` 분기에 잘못 매칭된다 (Bean Validation 실패가 일반 framework 4xx로 묶여 `code` 정확도 손실).
  - 두 핸들러는 공통 헬퍼 `enrichValidationFailed(pd, errors[])` 사용. 기존 `handleExceptionInternal`의 4xx → `MALFORMED_REQUEST` 분기는 *override한 두 핸들러가 직접 반환하므로 통과하지 않음*.

**핸들러별 응답 코드 dispatch (정리)**:

| 트리거 | 핸들러 | 응답 `code` |
|---|---|---|
| `BusinessException` 서브타입 throw | `@ExceptionHandler(BusinessException.class)` (PLAN-0005-A) | 해당 `ErrorCode` (예: `POST_NOT_FOUND`, `INVALID_POST_CONTENT`) |
| `@RequestBody @Valid` 실패 | `handleMethodArgumentNotValid` override (Phase 1 신설) | `VALIDATION_FAILED` |
| `@RequestParam @Min` 등 실패 | `handleHandlerMethodValidationException` override (Phase 1 신설) | `VALIDATION_FAILED` |
| 기타 framework 4xx (malformed JSON, 타입 불일치, missing param, 405, 415 등) | 부모 `ResponseEntityExceptionHandler` → `handleExceptionInternal` (PLAN-0005-A) | `MALFORMED_REQUEST` |
| 미처리 예외 (NPE, DB 장애 등) | `@ExceptionHandler(Exception.class)` 안전망 (PLAN-0005-A) | `INTERNAL_ERROR` (500) |

즉 **모든 4xx를 `MALFORMED_REQUEST`로 묶지 않는다** — Bean Validation 실패는 별도 `VALIDATION_FAILED`로 분리되어 클라이언트가 *form-level 위반*을 코드만 보고 식별 가능. 이게 Phase 1 핸들러 신설의 핵심 가치.
- `errors[]` 포맷: `[{"field": "title", "reason": "must not be blank"}, ...]` — `MethodArgumentNotValidException`의 field errors와 `HandlerMethodValidationException`의 parameter validation results를 같은 `{field, reason}` 모양으로 normalize한다. `HandlerMethodValidationException`의 결과 추출 API는 Spring 버전에 따라 다르므로 이 상층 Scope에서는 특정 메서드명을 고정하지 않는다.

### Phase 2 — 어노테이션 부착 (DTO + 컨트롤러 파라미터)

- `CreatePostRequest`:
  - `title`: `@NotBlank` + `@Size(max=200)` (limits는 변경 가능 — Risks 1 참조)
  - `body`: `@NotNull` + `@Size(max=10000)` (도메인은 blank 허용; null만 금지)
  - `author`: `@NotBlank` + `@Size(max=50)`
- `UpdatePostRequest`:
  - `title`: `@NotBlank` + `@Size(max=200)`
  - `body`: `@NotNull` + `@Size(max=10000)`
- `PostController`:
  - **클래스 레벨 `@Validated` 부착하지 않음.** Spring MVC 6.1+의 *built-in* method validation에 의존 (컨트롤러 메서드 파라미터에 직접 `@Min`/`@Max` 등을 부착하면 `HandlerMethodValidationException`을 던짐). `@Validated`를 붙이면 AOP 기반 method validation이 우선해 `ConstraintViolationException`을 던지며 핸들러 매칭이 어긋난다.
  - **서비스/유스케이스 레이어에도 `@Validated`를 부착하지 않는다.** 서비스 진입 시점의 입력 정합성은 도메인 객체(VO)의 생성자 검증으로 이미 커버된다 — `PostContent` 생성자가 title/body의 not-blank/null을 강제하고, `Post.validateAuthor`가 author를 강제(PLAN-0005-A에서 `InvalidPostContentException` 변환 완료). 이 구조는 ADR-0005 §5("도메인 invariant 진실원은 도메인")와 일관 — Bean Validation은 *전송 계층 보호용 보조 게이트*에만 둔다.
  - `create`, `update`: `@Valid @RequestBody`.
  - `list`: `@RequestParam @Min(0) int page`, `@RequestParam @Min(1) @Max(100) int size`.
  - `@PathVariable Long id`는 본 Plan에서 검증 안 함 (음수/0이면 자연스럽게 PostNotFoundException 404로 처리됨; 추가 검증의 ROI 없음).

### 양 Phase 공통

- 본 Plan은 자동 테스트를 작성하지 않는다 — 후속 test-strategy Plan에서 처리. PLAN-0005-A의 일관성 유지.
- 동작 검증은 로컬 `bootRun` + curl (PLAN-0005-A AC9 패턴 그대로).

## Non-goals

- 도메인 invariant 변경 — PLAN-0005-A에서 이미 처리.
- 인증/인가 검증 — 인증 ADR.
- 비즈니스 규칙 검증 (예: 게시글 수 상한, 작성자 권한) — 도메인 invariant이므로 도메인 레이어 책임.
- 검증 메시지 i18n / 커스텀 메시지 번들 — 기본 메시지 사용. i18n은 후속.
- 자동 테스트 작성 — test-strategy Plan.
- 도메인 검증과 DTO 검증의 *통합* — ADR-0005 §5대로 두 계층은 의도된 다층 구조. DTO가 fail-fast하므로 일부 도메인 검증은 실제 트리거되지 않게 되지만 도메인은 다른 진입점(배치, 이벤트)을 위해 유지.
- `WebException` 계층 신설 — Spring MVC가 이미 표현하는 입력 검증/요청 파싱 실패를 별도 예외로 감싸지 않는다. 웹 어댑터의 책임은 프레임워크 예외를 응답 계약(`VALIDATION_FAILED` / `MALFORMED_REQUEST`)으로 매핑하는 것이다. 커스텀 웹 정책 실패가 실제로 생길 때만 별도 ADR/Plan에서 검토한다.

## Related ADRs

- ADR-0005 §4 (응답 스키마 — `errors[]` 커스텀 property), §5 (검증 책임 분리), §8 (PLAN-0005-B 상세) — 본 Plan의 직접 근거.
- ADR-0003: Hexagonal — DTO 검증은 `adapter/in/web` 책임, 도메인 검증과 분리.
- ADR-0004: 골격 우선 — 본 Plan은 *form-level 검증* 골격까지. Validation 그룹/조건부 검증 같은 디테일은 후속.

## Acceptance Criteria

1. `build.gradle`에 `spring-boot-starter-validation` 의존성이 있다 (`./gradlew dependencies | grep validation`).
2. `ErrorCode`에 `VALIDATION_FAILED` 항목이 추가됐다 (`INVALID_INPUT` 카테고리). per-code javadoc은 `BusinessException`을 통한 throw가 아닌 *어댑터 핸들러 전용*임을 명시.
3. `GlobalExceptionHandler`는 `handleMethodArgumentNotValid` override + `handleHandlerMethodValidationException` override를 갖는다. 두 핸들러는 응답 body로 ProblemDetail + `code=VALIDATION_FAILED` + `errors[]` 커스텀 property를 반환한다.
4. `CreatePostRequest` / `UpdatePostRequest`에 Bean Validation 어노테이션이 부착됐다.
5. `PostController` 클래스에는 `@Validated`가 없다. `create`/`update` 메서드의 `@RequestBody`에는 `@Valid`, `list`의 `page`/`size`에는 `@Min`/`@Max`가 부착됐다.
6. 기존 외부 API 동작 회귀 없음 + 새 검증 시나리오 추가 (PLAN-0005-A의 4 시나리오 + 신규 2 = 총 6 시나리오, 모두 ProblemDetail):
   - PLAN-0005-A의 4 시나리오 그대로 통과 (회귀 없음).
   - **신규**: `POST /api/posts` body=`{"title":"","body":"x","author":"a"}` → 400, `code=VALIDATION_FAILED`, `errors=[{"field":"title","reason":"must not be blank"}]`.
   - **신규**: `GET /api/posts?page=-1` → 400, `code=VALIDATION_FAILED`, `errors=[{"field":"page","reason":"must be greater than or equal to 0"}]` (PLAN-0005-A의 P1 회귀 해결 검증).
7. 본 Plan은 자동 테스트를 작성하지 않는다. 기존 `BoardServiceApplicationTests`는 회귀 없이 통과한다 (`./gradlew test`). AC6의 시나리오는 implementer가 로컬 실행으로 확인하고 PR description에 결과 명시.
8. 두 phase는 별개 커밋으로 분리된다 (`feat: ... (PLAN-0005-B Phase 1)`, `feat: ... (PLAN-0005-B Phase 2)`).

## ADR Required

**no** — ADR-0005가 §4·§5·§8에서 본 Plan의 정책 근거를 모두 정의함. 본 Plan은 그 정책을 구현할 뿐.

## Risks

1. **DTO `@Size` 한계값의 결정 근거 부재** — 본 Plan은 `title=200`, `body=10000`, `author=50` 같은 한계값을 *전송 계층 보호용 임의값*으로 둔다. 진짜 도메인 한계가 있다면 도메인 모델(`PostContent`)에 invariant로 표현해야 한다 (ADR-0005 §5). 한계값 변경 시엔 *DTO 어노테이션만* 바꾸고, 도메인 측 변경이 필요하면 그건 별도 Plan.

2. **DTO 검증과 도메인 검증의 우선순위 — 같은 입력에 대한 응답 코드 분기** — `title=""` 요청은 *DTO 검증*(`@NotBlank`)이 먼저 reject하므로 `VALIDATION_FAILED` 응답이 된다. 만약 DTO 검증을 우회하는 진입점(직접 도메인 호출)이 생기면 *도메인 검증*(`InvalidPostContentException`)으로 `INVALID_POST_CONTENT` 응답이 된다. 같은 사용자 실수에 대해 코드가 두 종류로 나타날 수 있다는 점은 의도된 설계 (ADR-0005 §5: "의미와 응답 형태가 다르므로 중복으로 보지 않는다"). PR description / API 문서에 이 점을 명시해 클라이언트가 두 코드 모두 핸들링하게 한다.

3. **`HandlerMethodValidationException` 처리의 Spring 버전 의존성** — Spring 6.1+에서 `@RequestParam`/`@PathVariable` 검증 실패가 `HandlerMethodValidationException`으로 통합됐다. 현재 프로젝트는 Spring Boot 4.0.2 / Spring Framework 7 (보호된 클래스패스에서 `spring-web/7.0.3` 확인). 두 가지 함정:
   - **클래스 레벨 `@Validated` 충돌**: 컨트롤러 클래스에 `@Validated`가 있으면 AOP 기반 method validation이 우선해 `ConstraintViolationException`을 던지며 built-in 동작이 우회된다. **컨트롤러에 `@Validated` 부착 금지** (Implementation Hints 참조).
   - **API 시그니처 변동**: `HandlerMethodValidationException`의 결과 추출 메서드(`getAllValidationResults` / `getParameterValidationResults` / `visitResults`)가 Spring 6.1 → 6.2+ → 7 사이에 변경됐다. Implementer는 IDE 자동완성으로 로컬 버전 호환 메서드 선택. 컴파일 통과 + 응답 body의 `errors[]`가 채워지는지 로컬 curl 검증.
   - 그래도 매칭이 안 되면 원인을 먼저 확인한다. `ConstraintViolationException` fallback 핸들러는 로컬 검증에서 실제로 AOP 기반 method validation 경로가 남아 있음이 확인될 때만 추가한다.

4. **`handleExceptionInternal`의 4xx→`MALFORMED_REQUEST` 분기와의 중복** — Phase 1의 `handleMethodArgumentNotValid`/`handleHandlerMethodValidationException` override가 *직접 반환*하면 `handleExceptionInternal`로 흐르지 않는다 (Spring 내부 호출 흐름 기준). 만약 override가 부모 클래스 호출로 처리하면 두 번 enrich되어 잘못된 코드(`MALFORMED_REQUEST`)로 덮일 위험. **반드시 직접 반환하는 형태**로 구현하고 코드 리뷰로 확인.

5. **P1 회귀 해결의 의존성** — PLAN-0005-A의 P1 회귀 fix는 본 Plan이 머지되어야 비로소 해결된다. 그 사이 시간 동안 `?page=-1` 같은 요청은 5xx로 응답된다. 본 Plan을 PLAN-0005-A 직후에 빠르게 머지하는 것이 운영적으로 권장.

6. **Lombok 등 build.gradle 변경 충돌** — 의존성 한 줄 추가만 하지만, 이미 `spring-boot-starter-data-jpa`가 transitive로 일부 validation 관련 클래스를 끌고 올 수 있다. 명시적으로 `spring-boot-starter-validation`을 추가해 의존성 의도를 분명히 하고, 충돌 시 `./gradlew dependencies`로 진단.

---

<!-- 하층: 실행 재량 — 코드베이스 충돌 시 갱신 가능 -->

## Required Reading

- `docs/adr/0003-clean-architecture-ddd-hexagonal.md`
- `docs/adr/0004-foundation-first-policy.md`
- `docs/adr/0005-exception-error-response-policy.md` (특히 §4 응답 스키마, §5 검증 책임 분리, §8 PLAN-0005-B 상세)
- `docs/plans/done/PLAN-0005-A-error-model-skeleton.md` (특히 §Implementation Hints의 GlobalExceptionHandler 절 — 본 Plan이 그 위에 override 추가)
- `CLAUDE.md`
- `.claude/skills/api-standards.md`
- 현재 상태 파악용:
  - `src/main/java/com/dunowljj/board/adapter/in/web/PostController.java`
  - `src/main/java/com/dunowljj/board/adapter/in/web/dto/request/CreatePostRequest.java`
  - `src/main/java/com/dunowljj/board/adapter/in/web/dto/request/UpdatePostRequest.java`
  - `src/main/java/com/dunowljj/board/adapter/in/web/exception/GlobalExceptionHandler.java`
  - `src/main/java/com/dunowljj/board/common/error/ErrorCode.java`
  - `build.gradle`

## Files to Touch

### Phase 1 — 검증 인프라

#### MODIFY
- `build.gradle` — `spring-boot-starter-validation` 추가
- `src/main/java/com/dunowljj/board/common/error/ErrorCode.java` — `VALIDATION_FAILED` enum 항목 추가
- `src/main/java/com/dunowljj/board/adapter/in/web/exception/GlobalExceptionHandler.java` — `handleMethodArgumentNotValid` + `handleHandlerMethodValidationException` override 추가, errors[] 변환 헬퍼 추가

#### Tests
> Tests: 본 Plan 범위 외 (Non-goals 참조). 후속 test-strategy Plan에서 작성.

### Phase 2 — 어노테이션 부착

#### MODIFY
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/request/CreatePostRequest.java` — `@NotBlank` / `@NotNull` / `@Size` 부착
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/request/UpdatePostRequest.java` — 동일
- `src/main/java/com/dunowljj/board/adapter/in/web/PostController.java` — 클래스 레벨 `@Validated`는 추가하지 않고, `create`/`update`에 `@Valid`, `list`의 `page`/`size`에 `@Min`/`@Max`

#### Tests
> Tests: 본 Plan 범위 외. 후속 test-strategy Plan에서 작성.

## Implementation Hints

> 구조 골격 수준만 적는다. 의사코드 금지.

### `build.gradle` (의존성 추가)
- 기존 `spring-boot-starter-webmvc`, `data-jpa` 다음 줄에:
  ```
  implementation 'org.springframework.boot:spring-boot-starter-validation'
  ```

### `common/error/ErrorCode.java` (항목 추가)
- `MALFORMED_REQUEST` 다음, `INTERNAL_ERROR` 앞에:
  ```java
  /**
   * Bean Validation failures (DTO @Valid + @RequestParam/@PathVariable @Min etc.)
   * Emitted by GlobalExceptionHandler.handleMethodArgumentNotValid /
   * handleHandlerMethodValidationException. Response carries `errors[]` custom
   * property listing field-level violations. Not throwable via BusinessException.
   */
  VALIDATION_FAILED("VALIDATION_FAILED", ErrorCategory.INVALID_INPUT, "입력 형식이 올바르지 않습니다"),
  ```

### `adapter/in/web/exception/GlobalExceptionHandler.java` (override 추가)
- 두 메서드 override:
  ```java
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
          MethodArgumentNotValidException ex, HttpHeaders headers,
          HttpStatusCode status, WebRequest request) {
      // build ProblemDetail with VALIDATION_FAILED + errors[]
      // return new ResponseEntity directly (do NOT call super)
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
          HandlerMethodValidationException ex, HttpHeaders headers,
          HttpStatusCode status, WebRequest request) {
      // same shape, errors[] normalized from version-compatible validation results API
  }
  ```
- 공통 헬퍼:
  ```java
  private ResponseEntity<Object> validationFailed(List<FieldError> fieldErrors, String path) {
      ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
              ErrorCode.VALIDATION_FAILED.defaultMessage());
      List<Map<String, String>> errors = fieldErrors.stream()
              .map(fe -> Map.of("field", fe.getField(),
                                "reason", fe.getDefaultMessage()))
              .toList();
      pd.setProperty("errors", errors);
      enrich(pd, path, ErrorCode.VALIDATION_FAILED);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
  }
  ```
- `HandlerMethodValidationException`에서 field-level errors[]를 추출할 때는 **로컬 Spring 버전의 public API를 확인 후 사용**한다. Spring 6.1과 6.2+ / Spring Framework 7 사이에 `getAllValidationResults()` ↔ `getParameterValidationResults()` ↔ `visitResults(...)` 등 메서드 시그니처가 변경됐다. Implementer는 IDE 자동완성 또는 `./gradlew dependencies | grep spring-web`으로 버전 확인 후 호환되는 메서드 선택. 추출 결과는 `List<{field, reason}>` 모양으로 normalize.
- 기존 `handleExceptionInternal`은 변경하지 않음 — 두 override가 직접 반환하므로 흐르지 않음.

### `adapter/in/web/dto/request/CreatePostRequest.java`
- record 시그니처에 어노테이션 부착 (record는 component에 어노테이션 부착 가능):
  ```java
  public record CreatePostRequest(
          @NotBlank @Size(max = 200) String title,
          @NotNull @Size(max = 10000) String body,
          @NotBlank @Size(max = 50) String author) {}
  ```

### `adapter/in/web/dto/request/UpdatePostRequest.java`
- 동일 패턴 (`title`, `body`만).

### `adapter/in/web/PostController.java`
- **클래스 레벨 `@Validated` 부착 금지** — Spring 6.1+ built-in method validation을 쓰기 위함. 자세한 이유는 §Phase 2 Scope 참조.
- 서비스/유스케이스 레이어에도 `@Validated` 추가 안 함. 도메인 VO 검증(PLAN-0005-A)이 service 진입 정합성을 담당.
- `@RequestBody` 앞에 `@Valid` 추가 (`create`, `update`).
- `list`:
  ```java
  public ResponseEntity<PostListResponse> list(
          @RequestParam(defaultValue = "0") @Min(0) int page,
          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
      ...
  }
  ```

### 커밋 메시지 (참고)
- Phase 1: `feat(error): add VALIDATION_FAILED ErrorCode and bean-validation handlers (PLAN-0005-B Phase 1)`
- Phase 2: `feat(web): apply Bean Validation annotations to PostController DTOs and params (PLAN-0005-B Phase 2)`
