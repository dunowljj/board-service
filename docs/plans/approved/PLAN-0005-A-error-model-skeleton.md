# PLAN-0005-A: 에러 모델 표준화 — 골격 신설 + 도메인 마이그레이션

<!-- 상층: 승인 게이트 — 방향/범위/완료 기준 -->

## Goal

ADR-0005가 정의한 예외/에러 응답 정책의 **골격 부분**(에러 모델 + 핸들러 + 도메인 마이그레이션)을 도입한다. 입력 검증(Bean Validation, PLAN-0005-B)과 관측성/로깅(PLAN-0005-C)은 본 Plan 범위 밖이며, 본 Plan이 만든 골격 위에 후속으로 쌓인다.

핵심 결과: **(1) `BusinessException` 단일 4xx 추상 클래스, (2) `ErrorCode` 응답 계약 카탈로그(4xx + `INTERNAL_ERROR`), (3) `ErrorCategory → HttpStatus` 어댑터 측 매퍼, (4) 도메인의 `IllegalArgumentException`을 도메인 전용 `BusinessException` 서브타입으로 치환** — 이후 도메인이 늘어도 `ErrorCode` enum 1줄 + Exception 서브클래스 1개로 끝난다.

## Scope

본 Plan은 두 phase로 구성된다. **리뷰 포커스가 다르므로 별개 커밋으로 분리**한다 — Phase 1은 새 추상화·경계 설계(BusinessException 계약, ErrorCategory enum, 매퍼 격리, ErrorResponse 스키마)에 결함이 잡히는 자리고, Phase 2는 throw-site 치환 정확도(어느 도메인 가드를 어떤 서브타입으로, context 키, import 경로)에 결함이 잡히는 자리다. 다른 시야로 봐야 한다. **단, 두 phase는 독립 배포 단위가 아니다**: Phase 1만 머지하면 안전망이 기존 `PostNotFoundException`/`IllegalArgumentException`을 500으로 잡아 외부 4xx 계약이 깨지고(Risk 5), Phase 2만 머지하면 `BusinessException` 부재로 컴파일 실패한다. 따라서 **두 커밋은 반드시 같은 PR 안에서 함께 머지**된다.

### Phase 1 — 에러 모델 골격 신설 (추가만, 기존 동작 변경 없음)

- `common/error/` 패키지 신설 (framework-neutral shared kernel; `package-info.java`로 의미·금지 항목 명시)
- `ErrorCategory` enum: `NOT_FOUND`, `INVALID_INPUT`, `CONFLICT`, `FORBIDDEN`, `INTERNAL`
- `ErrorCode` enum: `code` / `category` / `defaultMessage` 3필드. 초기 항목: `POST_NOT_FOUND` (NOT_FOUND), `INVALID_POST_CONTENT` (INVALID_INPUT), `MALFORMED_REQUEST` (INVALID_INPUT — Spring MVC 프레임워크 4xx fallback: malformed JSON, 타입 불일치, missing param 등), `INTERNAL_ERROR` (INTERNAL). `VALIDATION_FAILED`는 PLAN-0005-B로 미룸.
- `BusinessException` abstract: `ErrorCode` + `Map<String, Object> context`. **불변식**: 생성자에서 `ErrorCode.category()`가 `INTERNAL`이면 `IllegalArgumentException` 즉시 throw (도메인이 5xx 코드를 직접 던지지 못하게 컴파일/실행 시점에서 차단).
- `adapter/in/web/error/ErrorCategoryHttpStatusMapper` — `ErrorCategory → HttpStatus` 매핑. Spring `HttpStatus` 의존이 이 클래스에만 갇힘.
- 응답 body는 **RFC 9457 `ProblemDetail`** (Spring 6 네이티브) 직접 사용. 표준 필드(`type`/`title`/`status`/`detail`/`instance`) + 커스텀 property 두 개(`code` SCREAMING_SNAKE_CASE, `timestamp` ISO-8601). 기존 자기 정의 `ErrorResponse` record 삭제. `errors[]`는 PLAN-0005-B에서 커스텀 property로 추가.
- `GlobalExceptionHandler` 재작성: `extends ResponseEntityExceptionHandler`로 Spring MVC 프레임워크 4xx 예외(malformed JSON, 타입 불일치, missing param, method not allowed 등)의 framework-resolved status + ProblemDetail body를 보존하고 우리 `code`/`timestamp` 커스텀 property로 enrich (`handleExceptionInternal` override). 그 외에 `@ExceptionHandler(BusinessException.class)` 1개 + `@ExceptionHandler(Exception.class)` 안전망 1개. 기존 `PostNotFoundException` 전용 핸들러와 `IllegalArgumentException` 핸들러 제거.

### Phase 2 — 도메인 마이그레이션 (기존 코드 수정)

- `common/exception/PostNotFoundException` → `common/error/PostNotFoundException`로 이동. `BusinessException` 상속, `ErrorCode.POST_NOT_FOUND` 보유, context에 `postId` 포함. 구 패키지(`common/exception/`) 삭제.
- `common/error/InvalidPostContentException` 신설. `ErrorCode.INVALID_POST_CONTENT` 보유.
- `domain/post/PostContent` 의 `IllegalArgumentException` → `InvalidPostContentException`로 치환 (title null/blank, body null).
- `domain/post/Post.validateAuthor` 의 `IllegalArgumentException` → `InvalidPostContentException`로 치환 (author null/blank).
- `Post.reconstitute`의 id/createdAt/updatedAt null 가드는 **그대로 `IllegalArgumentException` 유지** — 사용자 입력 검증이 아니라 영속성 계층의 사전 조건(데이터 무결성)이므로 4xx로 노출되면 의미가 왜곡됨. 본 가드들이 발생하면 새 안전망을 거쳐 500/`INTERNAL_ERROR`로 응답되며 이는 의미상 정확함. (Risks 참조)
- `application/service/Post*Service` 의 import 경로 갱신 (`common.exception` → `common.error`).

### 양 Phase 공통

- 본 Plan은 자동 테스트를 작성하지 않는다 — 테스트 전략(프레임워크/패키지 구조/계층별 정책)은 별도 후속 Plan(test-strategy)에서 정의하고, 본 Plan이 만든 코드도 그 Plan에서 우선 커버한다. PLAN-0004 done 노트의 "테스트 전략은 별도 Plan에서 정의" 결정과 일관.
- 본 Plan은 코드만 변경한다. ADR/Plan 문서 변경 없음.

## Non-goals

다음은 명시적으로 본 Plan 범위가 아니다 (ADR-0005가 의도한 변경 주기 분리에 따름):

- Bean Validation 도입 (`@Valid`, `@NotBlank`, `@Size` 등) — **PLAN-0005-B**
- `VALIDATION_FAILED` ErrorCode 항목과 `errors[]` 응답 필드 — **PLAN-0005-B**
- TraceId 필터, `X-Trace-Id` 헤더, MDC 주입 — **PLAN-0005-C**
- 구조화 JSON 로깅, 로그 레벨/포맷 정책 구현 — **PLAN-0005-C**
- `BusinessException.context`를 `ErrorContext` 전용 타입으로 강제 — ADR-0005 §3대로 초기엔 규약 + 코드 리뷰 통제
- 도메인별 `ErrorCode` enum 분리 (`PostErrorCode`, `CommentErrorCode` 등) — 도메인이 늘어난 뒤 별도 ADR
- `SystemException`/5xx 분류 도입 — ADR-0005 §1대로 분류 정책이 갈리는 시점에 별도 ADR
- 인증/인가, 보안/남용 4xx의 metric/alert 정책 — 인증 ADR
- Phase 1 골격 자체의 후속 수정 (Phase 2 도중 발견 시): ADR-0005 §8대로 본 Plan 안에서 처리하지 않고 **별도 후속 Plan으로 분기**
- 단위/통합 테스트 자동화 — **별도 후속 Plan(test-strategy)** 에서 프레임워크 선정, 패키지 구조, 계층별 정책 정의 후 본 Plan이 만든 코드에도 적용. 본 Plan 단계에서는 동작 검증을 로컬 실행 + 코드 리뷰로 갈음.

## Related ADRs

- ADR-0003: Clean/Hexagonal Architecture + DDD + CQRS — `common/error`가 framework-neutral shared kernel로 위치 정당화
- ADR-0004: 초기 단계 정책 — 골격 우선, 디테일 점진적 보강 (본 Plan은 ADR-0005 정책 중 *골격*만 구현)
- ADR-0005: 예외 / 에러 응답 정책 — 본 Plan의 직접 근거

## Acceptance Criteria

상층 검증(승인 시 사람이 확인):

1. `common/error/` 패키지에 `ErrorCategory`, `ErrorCode`, `BusinessException`, `package-info.java`, `PostNotFoundException`, `InvalidPostContentException`이 존재한다.
2. `common/error/package-info.java`에 "framework-neutral shared kernel" 의미와 금지 항목(Spring Web/JPA/Servlet 타입 import 금지)이 javadoc으로 기록돼 있다.
3. `common/exception/` 패키지가 삭제됐다.
4. `BusinessException`은 abstract이며, 생성자에서 `ErrorCode`가 `INTERNAL` 카테고리면 즉시 `IllegalArgumentException`을 던진다. (불변식 검증은 코드 리뷰 — 자동 테스트는 별도 test-strategy Plan)
5. `ErrorCategoryHttpStatusMapper`는 `adapter/in/web/error/`에 위치하며, 이 클래스만 `org.springframework.http.HttpStatus`를 import한다. `common/error/` 패키지에는 `org.springframework.http.HttpStatus` import가 없어야 한다 (`grep -r "org.springframework.http.HttpStatus" src/main/java/com/dunowljj/board/common/error/` 결과 0줄). `ErrorCategory → HttpStatus` 변환 책임은 매퍼에만 둔다 (다른 어댑터 클래스가 `ResponseEntity.status(HttpStatus.X)` 형태로 HttpStatus를 *사용*하는 것은 별개로 허용 — 본 criterion의 대상은 *변환 결정 로직*의 격리).
6. `GlobalExceptionHandler`는 `ResponseEntityExceptionHandler`를 상속하며, `BusinessException` 1개 + `Exception` 1개의 `@ExceptionHandler` + `handleExceptionInternal` override로 구성된다. 기존 `PostNotFoundException` 전용 핸들러와 `IllegalArgumentException` 핸들러는 삭제됐다.
7. 응답 body는 RFC 9457 `ProblemDetail`(Spring 6 native)을 사용한다. 표준 필드(`type`/`title`/`status`/`detail`/`instance`) 외에 커스텀 property 두 개(`code` SCREAMING_SNAKE_CASE, `timestamp` ISO-8601)가 모든 응답에 채워진다. 기존 자기 정의 `ErrorResponse` record는 삭제됐다. `ErrorCategory`는 응답에 노출하지 않는다 (HTTP status와 1:1이라 중복).
8. 도메인 (`Post`, `PostContent`)의 사용자 입력 검증 경로(title/body/author)는 더 이상 `IllegalArgumentException`을 던지지 않고 `InvalidPostContentException`을 던진다. `Post.reconstitute`의 영속성 사전 조건 가드(id/createdAt/updatedAt)는 변경 없이 `IllegalArgumentException` 유지.
9. 기존 외부 API 동작 회귀 없음 (4 시나리오, 모두 ProblemDetail 본문):
   - `GET /api/posts/{id}` (없는 id) → 404, body의 `code`는 `POST_NOT_FOUND`, `instance`는 요청 path
   - `POST /api/posts` (title 누락 — 도메인 invariant 위반) → 400, body의 `code`는 `INVALID_POST_CONTENT`
   - `POST /api/posts` (malformed JSON 또는 타입 불일치 — 프레임워크 4xx) → 400, body의 `code`는 `MALFORMED_REQUEST`
   - 임의의 `RuntimeException` 발생 → 500, body의 `code`는 `INTERNAL_ERROR`
10. 본 Plan은 자동 테스트를 작성하지 않는다 (Non-goals 참조). 기존 `BoardServiceApplicationTests`(Spring context boot)는 회귀 없이 통과해야 한다 (`./gradlew test`). Acceptance Criteria 9의 외부 API 동작 회귀 없음은 implementer가 로컬 실행으로 확인하고 PR description에 결과(curl 출력)를 명시한다.
11. 두 phase는 별개 커밋으로 분리된다 (`feat: ... (PLAN-0005-A Phase 1)`, `refactor: ... (PLAN-0005-A Phase 2)`).

## ADR Required

**no** — ADR-0005가 이미 존재하며, 본 Plan은 ADR-0005의 정책을 구현할 뿐 새 정책을 만들지 않는다.

## Risks

1. **`Post.reconstitute` 가드의 의미 변환 여지** — 본 Plan은 reconstitute의 id/createdAt/updatedAt null 가드를 `IllegalArgumentException`으로 유지한다 (영속성 사전 조건이라). 그러나 만약 향후 누군가 PostContent를 reconstitute에서 직접 사용하면서 invalid title/body 데이터가 DB에 있다면, `InvalidPostContentException`(4xx)이 발생하지만 이는 본질적으로 데이터 무결성 문제(5xx)다. 본 Plan은 이 case를 다루지 않으며, 발견되면 별도 Plan에서 영속성 계층 데이터 검증 정책으로 다룬다. (ADR-0005 §6의 "처리 정책이 갈리는 시점에 분해" 원칙)

2. **GlobalExceptionHandler 안전망의 비대칭 우선순위** — `@ExceptionHandler(BusinessException.class)`와 `@ExceptionHandler(Exception.class)`가 같은 클래스에 있을 때 Spring은 가장 구체적인 핸들러를 우선한다. `BusinessException extends RuntimeException extends Exception`이므로 `BusinessException` 핸들러가 먼저 매치되어야 한다. 본 Plan 단계에서는 로컬 실행(curl로 Acceptance Criteria 9 시나리오)으로 확인하고, 자동 테스트는 후속 test-strategy Plan에서 회귀 보증.

3. **`common/exception/` → `common/error/` 이동 시 import 경로 누락** — IDE 리팩터로 옮기더라도 텍스트 grep으로 잔존 `common.exception` import가 없는지 최종 확인한다. 누락되면 컴파일 실패로 즉시 드러나므로 위험은 낮음.

4. **`ErrorCode.INTERNAL_ERROR`를 카탈로그에 두는 것의 오용 위험** — 도메인/유스케이스 코드가 직접 `INTERNAL_ERROR`를 throw할 수 있는 경로는 `BusinessException` 생성자의 카테고리 가드(Acceptance Criteria 4)로 차단된다. 직접 `ProblemDetail`에 `INTERNAL_ERROR`를 넣어 발행할 수 있는 곳은 `GlobalExceptionHandler` 안전망뿐임을 코드 리뷰로 확인.

5. **Phase 1 단독 머지 시 일시적 회귀** — Phase 1은 *추가만이 아니라* `GlobalExceptionHandler` 재작성을 포함한다. 이 시점 `PostNotFoundException`은 아직 `RuntimeException`을 직접 상속이라 `BusinessException` 핸들러에 매치되지 않고 안전망(`Exception`)으로 떨어져 **404 → 500 회귀**가 발생하고, 사용자 입력 검증의 `IllegalArgumentException`도 더 이상 400 매핑이 없어 **400 → 500 회귀**가 발생한다. 따라서 두 phase는 **반드시 같은 PR 안에서 함께 머지**되며, 별개 PR로 나눠 순차 배포하지 않는다. PR 디스크립션에 이 제약을 명시.

6. **Lombok 사용 여부** — 기존 코드 스타일(`PostController`, `PostCommandService`)이 Lombok `@RequiredArgsConstructor`를 쓰지만, 새 모델 클래스(`BusinessException`, `ErrorCategoryHttpStatusMapper`)는 Lombok 없이도 충분히 간결하다. 본 Plan은 새 클래스에 Lombok을 도입하지 않는다 (사용 최소화).

---

<!-- 하층: 실행 재량 — 코드베이스 충돌 시 갱신 가능 -->

## Required Reading

실행/리뷰 전 에이전트가 읽어야 하는 모든 경로:

- `docs/adr/0003-clean-architecture-ddd-hexagonal.md`
- `docs/adr/0004-foundation-first-policy.md`
- `docs/adr/0005-exception-error-response-policy.md`
- `CLAUDE.md`
- `.claude/skills/clean-architecture.md`
- `.claude/skills/api-standards.md`
- 현재 상태 파악용 (변경 안 하더라도):
  - `src/main/java/com/dunowljj/board/common/exception/PostNotFoundException.java`
  - `src/main/java/com/dunowljj/board/adapter/in/web/exception/GlobalExceptionHandler.java`
  - `src/main/java/com/dunowljj/board/adapter/in/web/dto/response/ErrorResponse.java`
  - `src/main/java/com/dunowljj/board/domain/post/Post.java`
  - `src/main/java/com/dunowljj/board/domain/post/PostContent.java`
  - `src/main/java/com/dunowljj/board/application/service/PostCommandService.java`
  - `src/main/java/com/dunowljj/board/application/service/PostQueryService.java`
  - `src/main/java/com/dunowljj/board/adapter/in/web/PostController.java`
  - `docs/plans/done/PLAN-0004-crud-board-service.md` (이전 패턴/명명 참고)

## Files to Touch

### Phase 1 — 신설 (NEW) + 어댑터/응답 수정 (MODIFY)

#### NEW
- `src/main/java/com/dunowljj/board/common/error/package-info.java`
- `src/main/java/com/dunowljj/board/common/error/ErrorCategory.java`
- `src/main/java/com/dunowljj/board/common/error/ErrorCode.java`
- `src/main/java/com/dunowljj/board/common/error/BusinessException.java`
- `src/main/java/com/dunowljj/board/adapter/in/web/error/ErrorCategoryHttpStatusMapper.java`

#### MODIFY
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/response/ErrorResponse.java` — **삭제** (RFC 9457 `ProblemDetail`로 대체)
- `src/main/java/com/dunowljj/board/adapter/in/web/exception/GlobalExceptionHandler.java` — `BusinessException` 1핸들러 + 안전망 1핸들러로 압축

> Tests: 본 Plan 범위 외 (Non-goals 참조). 후속 test-strategy Plan에서 작성.

### Phase 2 — 도메인 마이그레이션

#### NEW (또는 이동)
- `src/main/java/com/dunowljj/board/common/error/PostNotFoundException.java` (구 `common/exception/PostNotFoundException`을 이동 + `BusinessException` 상속)
- `src/main/java/com/dunowljj/board/common/error/InvalidPostContentException.java`

#### MODIFY
- `src/main/java/com/dunowljj/board/domain/post/PostContent.java` — title/body 가드의 throw 타입을 `InvalidPostContentException`으로
- `src/main/java/com/dunowljj/board/domain/post/Post.java` — `validateAuthor`의 throw 타입을 `InvalidPostContentException`으로 (`reconstitute`의 id/createdAt/updatedAt 가드는 그대로 둠)
- `src/main/java/com/dunowljj/board/application/service/PostCommandService.java` — import 경로 갱신
- `src/main/java/com/dunowljj/board/application/service/PostQueryService.java` — import 경로 갱신

#### DELETE
- `src/main/java/com/dunowljj/board/common/exception/PostNotFoundException.java`
- (위 파일 삭제 후 `common/exception/` 디렉터리가 빈 채로 남으면 디렉터리도 정리)

> Tests: 본 Plan 범위 외 (Non-goals 참조). 후속 test-strategy Plan에서 작성.

## Implementation Hints

> 구조 골격 수준만 적는다. 의사코드 금지.

### `common/error/package-info.java`
- javadoc:
  - "framework-neutral shared kernel for the project's error model"
  - "Domain, Application, and Adapter layers may all depend on this package."
  - "MUST NOT import: org.springframework.web.*, org.springframework.http.*, jakarta.servlet.*, jakarta.persistence.*, or any framework-coupled type."
  - 근거 ADR 링크: ADR-0005 §2, ADR-0003

### `common/error/ErrorCategory.java`
- 단순 `enum`. 항목: `NOT_FOUND`, `INVALID_INPUT`, `CONFLICT`, `FORBIDDEN`, `INTERNAL`.
- 메서드 추가 없음.

### `common/error/ErrorCode.java`
- `enum` with 3 fields: `String code`, `ErrorCategory category`, `String defaultMessage`.
- 항목 (Phase 1):
  - `POST_NOT_FOUND("POST_NOT_FOUND", NOT_FOUND, "게시글을 찾을 수 없습니다")`
  - `INVALID_POST_CONTENT("INVALID_POST_CONTENT", INVALID_INPUT, "게시글 내용이 올바르지 않습니다")`
  - `INTERNAL_ERROR("INTERNAL_ERROR", INTERNAL, "일시적인 오류가 발생했습니다")`
- 생성자 private, getter 3개. 추가 메서드 없음.

### `common/error/BusinessException.java`
- `public abstract class BusinessException extends RuntimeException`
- 필드: `final ErrorCode errorCode`, `final Map<String, Object> context` (생성자에서 unmodifiable 래핑).
- 생성자 시그니처: `protected BusinessException(ErrorCode errorCode, Map<String, Object> context)`.
- **불변식**: 생성자에서 `errorCode.category() == ErrorCategory.INTERNAL`이면 `IllegalArgumentException("BusinessException must not carry INTERNAL category")` throw. 이 검사로 도메인이 5xx 코드를 직접 던지는 경로를 차단.
- `super(errorCode.defaultMessage())` 호출.
- getter 2개 (`errorCode()`, `context()`). 다른 메서드 없음.
- `context`가 null이면 빈 unmodifiable map으로 정규화.

### `adapter/in/web/error/ErrorCategoryHttpStatusMapper.java`
- 클래스 (Spring `@Component` 불필요 — pure mapping). `static HttpStatus toHttpStatus(ErrorCategory category)` 1메서드.
- 매핑:
  - `NOT_FOUND` → `HttpStatus.NOT_FOUND`
  - `INVALID_INPUT` → `HttpStatus.BAD_REQUEST`
  - `CONFLICT` → `HttpStatus.CONFLICT`
  - `FORBIDDEN` → `HttpStatus.FORBIDDEN`
  - `INTERNAL` → `HttpStatus.INTERNAL_SERVER_ERROR`
- default 분기: `IllegalStateException("Unmapped category: " + category)` (미래 enum 추가 시 매퍼 갱신 강제).
- `ErrorCategory → HttpStatus` 변환 결정 로직은 본 매퍼에만 존재한다. 다른 어댑터 코드(예: `GlobalExceptionHandler`)가 `ResponseEntity.status(HttpStatus.X)` 형태로 HttpStatus를 *사용*하는 것은 무관 — 본 hint의 대상은 *카테고리에서 status를 결정하는* 분기 로직의 단일화. `common/error/` 안쪽엔 HttpStatus 자체가 등장하지 않는다 (Acceptance Criteria 5).

### 응답 body — RFC 9457 `ProblemDetail`
- 자기 정의 `ErrorResponse` record 삭제. Spring 6의 `org.springframework.http.ProblemDetail`을 직접 사용.
- 표준 필드: `type` (초기 `about:blank`), `title` (status에 따라 자동), `status`, `detail`, `instance` (요청 path).
- 커스텀 property 두 개를 모든 응답에 enrich:
  - `code`: `ErrorCode`의 SCREAMING_SNAKE_CASE 값
  - `timestamp`: `LocalDateTime.now().toString()` (ISO-8601)
- 빌드 헬퍼: `ProblemDetail.forStatusAndDetail(status, message)` → `setInstance(URI.create(path))` → `setProperty("code"/"timestamp", ...)`. `GlobalExceptionHandler` 내부 private 메서드(`enrich(pd, path, errorCode)`)로 캡슐화.
- `ErrorCategory`는 응답에 노출하지 않음. 서버 내부 정책 모델(→ HTTP status 매퍼의 입력)로만 사용. 클라이언트 coarse 분기는 HTTP `status` 필드로 충분.

### `adapter/in/web/exception/GlobalExceptionHandler.java`
- `@RestControllerAdvice` 유지.
- 핸들러 2개:
  1. `@ExceptionHandler(BusinessException.class)` — `ErrorCategoryHttpStatusMapper.toHttpStatus(ex.errorCode().category())`로 status 결정, `ProblemDetail.forStatusAndDetail(status, ex.errorCode().defaultMessage())`로 body 생성, `code`/`timestamp` 커스텀 property + `instance=requestURI` enrich. `HttpServletRequest` 주입으로 path 획득.
  2. `handleExceptionInternal` override — parent가 framework MVC 예외에 대해 이미 ProblemDetail을 만들어 넘김. 그대로 받아 `code` (4xx → `MALFORMED_REQUEST` / 5xx → `INTERNAL_ERROR`)와 `timestamp` 커스텀 property + `instance=path` enrich 후 반환.
  3. `@ExceptionHandler(Exception.class)` — 무조건 500 + `INTERNAL_ERROR` ProblemDetail. (현 단계에선 로깅 미포함; PLAN-0005-C에서 추가.)
- 기존 `PostNotFoundException`/`IllegalArgumentException` 전용 핸들러 삭제.
- 우선순위 검증은 본 Plan에서 자동화하지 않음 (test-strategy Plan에서 다룸). 본 Plan 단계에선 로컬 실행으로 확인.

### `common/error/PostNotFoundException.java` (Phase 2 — 이동 + 변경)
- `extends BusinessException`.
- 생성자: `public PostNotFoundException(Long postId)`. 내부에서 `super(ErrorCode.POST_NOT_FOUND, Map.of("postId", postId))` 호출.
- 추가 메서드 없음.
- 메시지는 `BusinessException`이 `defaultMessage`를 super로 전달하므로 별도 setting 불필요. (postId를 메시지에 끼워야 한다면 ADR-0005 §3 context map의 의도가 흐려짐 — 메시지는 default, postId는 context에 두는 분리가 본 ADR의 의도.)

### `common/error/InvalidPostContentException.java` (Phase 2 — 신설)
- `extends BusinessException`.
- 생성자: `public InvalidPostContentException(String reason, Map<String, Object> context)`. `super(ErrorCode.INVALID_POST_CONTENT, context)`. `reason`은 운영용 정보 (응답 메시지에는 default 사용; reason은 향후 로깅용).
- 또는 단순 시그니처 `public InvalidPostContentException(String fieldName)` + `Map.of("field", fieldName)`. 도메인 호출부의 가독성을 보고 implementer가 결정.

### `domain/post/PostContent.java` (Phase 2 — 수정)
- `if (title == null || title.isBlank()) throw new IllegalArgumentException(...)` → `throw new InvalidPostContentException("title", Map.of("field", "title"))` (예시; 정확한 시그니처는 위 결정대로).
- body null 가드도 동일 패턴.

### `domain/post/Post.java` (Phase 2 — 수정)
- `validateAuthor`의 `IllegalArgumentException` → `InvalidPostContentException`.
- `reconstitute`의 id/createdAt/updatedAt 가드: **변경 없음** (Risks 1 참조).

### 커밋 메시지 (참고)
- Phase 1: `feat(error): introduce BusinessException, ErrorCode catalog, and category-to-status mapper (PLAN-0005-A Phase 1)`
- Phase 2: `refactor(error): migrate domain IllegalArgumentException to BusinessException subtypes (PLAN-0005-A Phase 2)`
