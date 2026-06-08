
# ADR-0005: 예외/에러 응답 정책

## Status
Proposed

## Date
2026-04-26

## Context

PLAN-0004로 CRUD 골격이 완성된 직후, 예외 처리 영역에는 다음과 같은 임시 구현이 남아 있다.

- 도메인 예외는 `PostNotFoundException` 단 한 개이며, 공통 부모 없이 `RuntimeException`을 직접 상속한다.
- 도메인의 invariant 위반은 `IllegalArgumentException`으로 던져지고, 핸들러가 이를 그대로 400으로 매핑한다. 이 매핑은 라이브러리 내부에서 발생한 `IllegalArgumentException`까지 포괄하여 사용자에게 잘못된 의미의 400을 돌려줄 위험이 있다.
- 에러 코드(`"POST_NOT_FOUND"`, `"BAD_REQUEST"`)는 핸들러 안에 문자열 리터럴로 박혀 있어 단일 진실원이 없다.
- 에러 응답은 `{ code, message, timestamp }` 3필드로 디버깅에 필요한 컨텍스트(`path`)가 누락되어 있다.
- DTO 단의 입력 검증(Bean Validation)이 도입되지 않아, 형식 오류와 도메인 invariant 위반이 같은 경로로 흘러간다.
- 관측성 인프라(MDC, traceId, 구조화 로깅)는 부재하며, 사용자 신고 1건을 로그에서 추적할 수 있는 수단이 없다.

이 영역은 다음 도메인(Comment, Auth, Search 등) 추가 시 가장 먼저 무너지는 자리이고, 한 번 외부에 노출된 응답 스키마/에러 코드는 사실상 API 계약이 된다. 따라서 **장기적으로 변경 비용이 가장 큰 축부터 먼저 고정**해야 한다.

## Decision

예외/에러 응답 정책을 다음 원칙으로 정의한다. 본 ADR은 정책의 윤곽만 정의하고, 단계적 구현은 PLAN-0005-A / PLAN-0005-B / PLAN-0005-C에서 분리하여 수행한다.

### 1. 예외 계층 — 분리 기준: "클라이언트가 고칠 수 있는가"

이 절의 분리 기준은 단 하나다: **클라이언트가 원인을 이해하고 요청을 수정해서 해결할 수 있는 실패인가, 아니면 서버 내부 문제라서 클라이언트가 어떻게 해도 고칠 수 없는 실패인가.** 이 기준이 응답에 어떤 정보를 노출할지, 어떻게 로깅할지를 결정한다.

- **`BusinessException`** (abstract) — *클라이언트가 고칠 수 있는 실패*
  - 입력 오류, 리소스 없음, 권한 없음 등 4xx 의미의 실패만 표현한다.
  - 응답에 구체 원인(`ErrorCode`, 필요 시 `errors[]`)을 노출한다 — 클라이언트가 보고 고치라는 의도이므로.
  - `ErrorCode`와 `Map<String, Object> context`(식별자 위주의 운영용 메타)를 보유한다.
  - 위치: `common/error` 패키지 — 자세한 위치 결정은 §2 참조. 도메인이 `BusinessException` 서브타입(예: `InvalidPostContentException`)을 직접 throw 해야 하므로(§5), `BusinessException`은 도메인이 의존 가능한 위치에 있어야 한다.

- **미처리 `Exception` / Web adapter 5xx fallback** — *서버 내부 문제 (클라이언트가 고칠 수 없음)*
  - DB 장애, 외부 API 장애, 타임아웃, 라이브러리 NPE 등.
  - 컨트롤러 return-value 검증 실패처럼 Spring MVC가 5xx로 판정한 프레임워크 경로도 같은 서버 내부 문제로 본다.
  - 구체 원인은 응답으로 노출하지 않는다 (보안·정보 누출 방지, 그리고 어차피 클라이언트가 손쓸 수 없음).
  - `adapter/in/web`의 5xx fallback 경로가 **`ErrorCode.INTERNAL_ERROR`(500)** 한 가지 응답으로 일괄 수렴시킨다.
  - 내부 원인은 응답 헤더의 `X-Trace-Id`와 서버 로그(full stack)로만 추적한다 — 사용자 신고가 들어오면 traceId 한 값으로 로그를 찾는 동선.

**`SystemException` 같은 5xx 전용 클래스는 지금 도입하지 않는다.** 지금 만들면 DB 장애·외부 API 장애·타임아웃·NPE를 억지로 트리에 분류해 끼워야 하는데, 처리·응답·로그 정책이 모두 동일한 동안에는 이 분류가 의미를 만들지 못하고 클래스 수만 늘린다. 분류가 실제로 정책을 가르는 시점(예: retryable vs non-retryable, 알림 정책 분기, 외부 의존성 격리/circuit breaker)에 별도 ADR로 도입한다.

```
RuntimeException
  ├── BusinessException (abstract — 4xx, client-fixable)
  │     ├── PostNotFoundException
  │     ├── InvalidPostContentException
  │     └── ... (도메인별 추가)
  └── (그 외 모든 미처리 예외 / Web adapter 5xx fallback — INTERNAL_ERROR로 수렴)
```

### 2. ErrorCode — 응답 계약 카탈로그 (헥사고날 경계 준수)

`ErrorCode`는 **응답으로 노출 가능한 모든 코드의 단일 진실원**이다. 4xx 비즈니스 코드(`POST_NOT_FOUND`, `VALIDATION_FAILED` 등)와 5xx fallback 코드(`INTERNAL_ERROR`)가 한 카탈로그에 모인다 — API 계약 관점에서는 두 종류가 똑같이 "응답 body의 `code` 필드에 등장 가능한 값"이므로 한 자리에서 조회 가능해야 한다.

- `SCREAMING_SNAKE_CASE`(`POST_NOT_FOUND`) 코드 형식.
- `ErrorCode` enum은 `code`, `category`, `defaultMessage` 세 필드만 보유한다.
- `ErrorCategory`는 정책 모델(`NOT_FOUND`, `INVALID_INPUT`, `CONFLICT`, `FORBIDDEN`, `INTERNAL` 등)이며 Spring Web 타입에 의존하지 않는다.
- **위치: `common/error` 패키지** — `ErrorCode`, `ErrorCategory`, `BusinessException`은 같은 자리에 둔다.
  - 이 패키지는 잡다한 유틸리티 모음이 아니라 **framework-neutral shared kernel** (Domain과 Application이 함께 의존 가능한 에러 모델 핵심)이다. 이 의미를 패키지 javadoc/README에 명시한다.
  - **포함 가능**: 순수 자바 + ADR-0005가 정의한 에러 모델 (`ErrorCode`, `ErrorCategory`, `BusinessException`, `BusinessException` 서브타입).
  - **포함 금지**: Spring Web 타입(`HttpStatus`, `ResponseEntity` 등), JPA/Hibernate 타입, Servlet 타입, 기타 어댑터 의존 타입.
  - 이유: 도메인이 `BusinessException` 서브타입(`InvalidPostContentException` 등)을 직접 throw 해야 하므로(§5), 만약 `application/error`에 두면 **Domain → Application 의존**이 발생해 ADR-0003의 "Application depends on Domain, Domain is pure" 원칙과 충돌한다. `common/error`를 framework-neutral shared kernel로 정의하면 Domain·Application·Adapter 모두 같은 자리를 의존할 수 있다.
  - 대안으로 검토했던 "도메인은 `DomainException`만 던지고 Application이 `BusinessException`으로 변환" 방식은 더 엄격하지만 CRUD 초기 단계에서는 클래스/매핑이 과하게 늘어나 골격 우선(ADR-0004) 원칙에 부합하지 않는다고 판단했다.

**카탈로그 ↔ Exception 트리는 1:1이 아니다.**

- `BusinessException` 서브타입은 *4xx 카테고리* `ErrorCode`만 보유한다 (불변식). 도메인/유스케이스 코드에서 `INTERNAL_ERROR`를 직접 throw 하지 않는다.
- `INTERNAL_ERROR`는 카탈로그에는 있지만 어떤 `BusinessException` 서브타입과도 매핑되지 않는다. **§1의 Web adapter 5xx fallback 경로가 직접 발행**한다.
- 이 비대칭은 §1의 분리 기준("클라이언트가 고칠 수 있는가")의 직접 결과이다: throw 측은 의도가 명확한 4xx만 다루고, 5xx는 Web adapter fallback에서 일괄 처리한다.

**`HttpStatus`는 `ErrorCode`에 두지 않는다.** `ErrorCategory → HttpStatus` 매핑은 `adapter/in/web` 계층의 `ErrorCategoryHttpStatusMapper`에서 수행한다.

- 이유: ADR-0003의 헥사고날 원칙상 inner policy model(`ErrorCode`)은 외부 어댑터 타입(Spring `HttpStatus`)에 의존하지 않아야 한다. 동일한 정책을 비-Web 진입점(배치, gRPC, 메시지 컨슈머)에서도 재사용 가능해야 한다.

**확장 동선**:

- 새 비즈니스 예외 = `ErrorCode` enum 항목 1줄 + `BusinessException` 서브클래스 1개. 핸들러 변경 없음.
- 4xx 핸들러 흐름: `BusinessException → ErrorCode → ErrorCategory → HttpStatus`.
- 5xx 핸들러 흐름: Web adapter의 5xx fallback 경로가 `ErrorCode.INTERNAL_ERROR` 응답을 직접 발행 (§1).

### 3. Context Map — 허용 규약

`BusinessException.context`는 자유로운 `Map<String, Object>`이지만, 다음 규약을 둔다.

- **허용**: 엔티티 식별자(`postId`, `commentId`, `userId`), 요청 식별자(`traceId`)
- **금지**: 요청 body 전체, 인증 토큰/비밀번호/PII, 자유 텍스트, 사용자 입력 원문
- 응답에는 노출하지 않는다(로그에만 사용).
- 향후 `ErrorContext` 전용 타입으로 강제 가능하지만, 초기엔 규약 + 코드 리뷰로 통제한다.

### 4. 에러 응답 스키마 — RFC 9457 ProblemDetail + 커스텀 property

응답 body는 **RFC 9457 (HTTP Problem Details, ex-RFC 7807)** 표준을 따른다 — Spring 6의 `org.springframework.http.ProblemDetail`을 그대로 사용. 우리 본래 필드(`code` SCREAMING_SNAKE_CASE, `timestamp`)는 **커스텀 property**로 표준 본문에 덧붙인다.

표준 응답:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "게시글을 찾을 수 없습니다",
  "instance": "/api/posts/42",
  "code": "POST_NOT_FOUND",
  "timestamp": "2026-04-29T10:15:30"
}
```

검증 실패 시 `errors[]`를 커스텀 property로 추가:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "입력 형식이 올바르지 않습니다",
  "instance": "/api/posts",
  "code": "VALIDATION_FAILED",
  "timestamp": "...",
  "errors": [
    { "field": "title", "reason": "must not be blank" }
  ]
}
```

- 표준 필드(`type`/`title`/`status`/`detail`/`instance`)는 RFC 9457 의미를 그대로 따른다. `instance`에 요청 path를 담는다.
- 커스텀 property `code`는 우리 `ErrorCode` 카탈로그의 SCREAMING_SNAKE_CASE 값. 클라이언트가 분기 처리할 때 가장 안정적인 키.
- 커스텀 property `timestamp`는 ISO-8601 문자열.
- `ErrorCategory`는 *서버 내부* 정책 모델로만 두고 응답에는 노출하지 않는다 — `ErrorCategory → HttpStatus`가 1:1이라 클라이언트는 HTTP `status`로 coarse 분기하고 `code`로 fine 분기하는 것이 자연스럽다. 응답에 `category`까지 두면 status와 의미 중복이 발생한다.
- 검증 실패 시의 `errors[]`도 커스텀 property (PLAN-0005-B에서 도입).
- `type` URI는 초기엔 `about:blank` (Spring 기본). 추후 에러 문서 호스팅 시 `https://errors.{host}/POST_NOT_FOUND` 같은 도메인-특화 URI로 전환 가능 — 이때 `code` 커스텀 property는 그대로 유지해 클라이언트 호환성을 보존한다.
- **표준 채택 이유**: 업계 표준(RFC 9457), Spring 6 네이티브 지원으로 `ResponseEntityExceptionHandler`가 framework MVC 4xx에 ProblemDetail body를 자동 생성 → 우리는 `code`/`timestamp` 커스텀 property만 enrich하면 됨. 자기 정의 스키마를 유지하는 비용보다 표준에 맞춰가는 비용이 작다.
- `traceId`는 **응답 body가 아니라 응답 헤더 `X-Trace-Id`로 노출한다.**
  - 이유: body는 API 계약 = 변경 비용이 크다. 관측성 식별자는 HTTP 본연의 메타 영역(헤더)이 더 자연스럽고, 클라이언트 도구(devtools/curl)가 자동 노출한다.
  - 사용자 신고 시 헤더 한 값만 캡처하여 로그 grep이 가능하다.

### 5. 검증 책임 분리 (3분류 카탈로그)

검증 규칙은 **형식·구조 / 도메인 invariant / Cross-cutting** 3종으로 분류한다. 동일해 보이는 검사(예: not-blank)도 어느 분류에 속하느냐에 따라 책임 위치가 달라진다.

| 종류 | 검증 위치 | 예시 | 실패 시 |
|---|---|---|---|
| **형식·구조 (Pure form)** | DTO (Bean Validation) | 타입/포맷 변환, JSON 파싱, regex, enum 멤버 매핑, 길이 상한과 같은 *전송 계층 보호용* 한계값 | `MethodArgumentNotValidException` → `VALIDATION_FAILED` (400) + `errors[]` |
| **도메인 invariant** | 도메인 (진실원) | VO 유효성(`PostContent`의 not-blank·length 등 *도메인 정의*상의 제약), 상태 전이 규칙, self-contained 교차 필드 규칙 | `BusinessException` 서브타입 (도메인 정의) |
| **Cross-cutting** | application / port / 별도 계층 | 소유권(작성자 == 요청자), uniqueness(제목 중복 검사), 인증·인가 | application 정책, port 확인, 또는 별도 ADR |

원칙:

- **도메인 invariant의 진실원은 도메인이다.** 도메인 객체는 Web adapter를 거치지 않는 진입점(배치, 이벤트 컨슈머, 직접 호출)에서도 항상 유효해야 한다. not-blank·length가 도메인 객체의 유효성을 *구성*한다면, 그 검사는 도메인이 책임진다.
- **DTO 검증은 도메인 검증을 대체하지 않는다.** DTO 검증의 역할은 ① 빠른 거절(fail-fast)로 도메인 진입 전 차단, ② field-level 응답(`errors[]`)으로 사용자 친화적 피드백 제공이다. 형식적으로 동일한 검사가 두 곳에 나타날 수 있으나 의미와 응답 형태가 다르므로 중복으로 보지 않는다.
- **Cross-cutting은 도메인 invariant로 분류하지 않는다.** 소유권은 application policy/인가와, uniqueness는 DB 제약·트랜잭션·port 확인과 결합된다. 이 둘을 도메인에 끌어들이면 설계가 꼬인다.
- **도메인 검증 실패는 `IllegalArgumentException`이 아니라 도메인 전용 `BusinessException` 서브타입(`InvalidPostContentException` 등)으로 던진다.** 라이브러리 내부 예외와 의미가 섞이지 않도록.

### 6. 도메인 예외 세분화 — 점진적 분해

- 초기엔 `InvalidPostContentException` 1개로 시작한다.
- 처리/응답/로그 정책이 실제로 갈리는 시점에 분해한다.
  - 예: 제목 길이 초과는 클라이언트가 잘라야 함 → 별도 코드
  - 예: 작성자 정책 위반 → 별도 코드 + 별도 로깅 정책
- 처음부터 `InvalidPostTitleException`, `InvalidPostBodyException` 등을 만들면 의미보다 클래스 수가 먼저 늘어 구조가 약해진다.

### 7. 로깅 정책

기본 원칙: **쓸모없는 stack trace는 줄이고, 운영에 필요한 구조화 필드는 더 잘 남긴다.** "로그를 줄이자"가 아니다 — 줄이는 것은 노이즈, 늘리는 것은 집계·검색·alert가 가능한 구조화 데이터다.

| 분류 | 레벨 | 출력 |
|---|---|---|
| 일반 `BusinessException` (4xx) | WARN | 구조화 1줄, stack trace 없음. 예: `code=POST_NOT_FOUND method=GET path=/api/posts/42 traceId=... ctx={postId=42}` |
| 검증 실패 (`VALIDATION_FAILED`) | WARN | 구조화 1줄. 예: `code=VALIDATION_FAILED method=POST path=/api/posts traceId=... errors=[title:blank]` |
| Web adapter 5xx fallback | ERROR | 구조화 1줄 + **full stack with cause chain**. 예: `code=INTERNAL_ERROR method=POST path=/api/posts traceId=... exception=... stacktrace=...` |

레벨/포맷의 근거 (§1의 분리 기준에서 도출):

- **일반적인 4xx는 stack trace를 남기지 않는다.** 클라이언트 입력/상태에 의해 예측 가능하게 발생하므로 full stack은 운영 신호 대비 노이즈가 크다 — 대량 발생 시 정작 5xx 신호가 묻힌다. 대신 `code`, `method`, `path`, `traceId`, 필요한 `ctx`를 구조화 필드로 남겨 검색·집계 가능하게 한다.
- **5xx는 cause chain까지 full stack을 남긴다.** §1대로 응답에는 원인을 노출하지 않으므로 서버 로그/APM/trace가 유일한 원인 추적 수단이다.
- **`traceId`는 모든 분류에 공통**으로 남긴다 — `X-Trace-Id` 헤더 → 로그 grep 동선이 분류와 무관하게 작동해야 하기 때문.

**범위 제한 (Scope):**

- **모든 4xx가 운영상 중요하지 않다는 뜻은 아니다.** 다만 본 ADR / PLAN-0005-C의 기본 구현 범위는 **4xx를 구조화 로그로 남기는 것까지**다.
- 보안/남용/이상 징후성 4xx(인증 실패 급증, 권한 실패 반복, ID enumeration 의심 등)에 대한 **metric, threshold, alert channel, 차단 정책은 인증/보안 정책 도입 시 별도 ADR에서 정의한다.**
- 따라서 현재 단계에서는 4xx에 대해 **stack trace를 남기거나 알림 정책을 구현하지 않는다.** PLAN-0005-C가 이 범위를 넘어 metric/alert 인프라까지 포함하지 않도록 한다.
- 구현 경계 정리:
  - **지금 구현**: 구조화 로그 필드 (`code`, `method`, `path`, `traceId`, `ctx`, `errors`)
  - **지금 구현하지 않음**: metric, alert threshold, alert channel, 자동 차단
  - **나중에 별도 ADR**: 인증/보안 정책 ADR에서 위 항목 정의

- MDC에 `traceId`, `method`, `path`, `query`를 자동 주입한다.
- 운영 환경은 JSON 구조화 로깅을 채택한다(개발은 텍스트 패턴).
- **클라이언트 주입 입력의 위생 처리** — 헤더/쿼리는 클라이언트가 임의로 보낼 수 있으므로 로그/MDC에 흘리기 전에 다음 규칙을 적용한다:
  - `X-Trace-Id` 헤더: trim 후 비어있지 않음 / 길이 ≤ 128 / 제어문자 미포함을 통과한 값만 echo, 실패 시 서버 발급 UUID.
  - Query string: 키 정규식 가드(`^[a-zA-Z][a-zA-Z0-9_-]{0,31}$`) 통과 + `observability.query.value-allowlist`에 등록된 키만 `key=value`로 노출. 정규식 미통과는 `[invalid]`, allowlist 외 키는 default-deny로 `[N keys redacted]`로 카디널리티만 보존. 키 이름 자체가 드러내는 PII(예: `?reset_token_for_alice@corp.com=...`) 누출을 방지하기 위함이다.

### 8. Plan 분리 — 변경 주기 단위

본 ADR은 다음 3개의 Plan으로 분리하여 단계적으로 적용한다. 각 Plan은 독립적으로 리뷰/머지 가능하다.

| Plan | 범위 | 변경 주기 |
|---|---|---|
| **PLAN-0005-A** 에러 모델 표준화 | **Phase 1 (에러 모델 골격)**: `ErrorCode` enum (code/category/defaultMessage), `ErrorCategory` enum, `ErrorCategoryHttpStatusMapper` (Web adapter), `BusinessException` 추상 클래스, RFC 9457 `ProblemDetail` 응답 + `code`/`timestamp` 커스텀 property, 핸들러 압축. **Phase 2 (도메인 마이그레이션)**: 도메인의 `IllegalArgumentException` → 도메인 전용 `BusinessException` 서브타입 치환. | 년 단위 (API 계약) |
| **PLAN-0005-B** 입력 검증 표준화 | (아래 §PLAN-0005-B 상세 참조) | 분기 단위 (DTO 변경) |
| **PLAN-0005-C** 관측성 / 로깅 표준화 | (아래 §PLAN-0005-C 상세 참조) | 인프라 도입 단위 (자주 변경) |

#### PLAN-0005-B 상세

**In-scope**:
- `build.gradle`에 `spring-boot-starter-validation` 추가
- DTO에 Jakarta Bean Validation 어노테이션 부착 (`@NotBlank`, `@Size`, `@Pattern` 등)
- 컨트롤러 핸들러 시그니처에 `@Valid` 부착
- `ErrorCode.VALIDATION_FAILED` 항목 추가
- 검증 실패 응답 body에 `errors[]` 커스텀 property 추가 (ProblemDetail의 `setProperty("errors", List.of(...))`)
- `MethodArgumentNotValidException`을 `VALIDATION_FAILED` + field-level `errors[]`로 응답하는 핸들러 추가 (`GlobalExceptionHandler.handleMethodArgumentNotValid` override 또는 별도 `@ExceptionHandler`)

**Out-of-scope** (다른 Plan으로 미룸):
- 도메인 invariant 변경 — PLAN-0005-A에서 이미 처리
- 인증/인가 검증 — 인증 도입 시 별도 ADR
- 비즈니스 규칙 검증 (예: 게시글 수 상한) — 도메인 invariant이므로 도메인 레이어 책임

**전제**: PLAN-0005-A가 머지되어 `ErrorCode` / `ProblemDetail` 응답 골격 / `GlobalExceptionHandler`가 존재.

#### PLAN-0005-C 상세

**In-scope**:
- TraceId 생성 필터(`OncePerRequestFilter`) — 요청 진입 시 ID 발급, MDC에 주입
- 응답 헤더 `X-Trace-Id`로 노출 (ADR-0005 §4)
- MDC에 `traceId`, `method`, `path`, `query` 자동 주입 (query는 키 정규식 + allowlist 위생 통과분만)
- `logback-spring.xml` (또는 동등) — 운영 환경은 JSON 인코더, 개발은 텍스트 패턴
- `BusinessException` (4xx) WARN 한 줄, Web adapter 5xx fallback ERROR + full stack 로깅 정책 구현 (ADR-0005 §7)

**Out-of-scope** (다른 Plan/ADR로 미룸):
- 분산 트레이싱 표준 (W3C Trace Context, `traceparent` 헤더) — 별도 ADR
- APM 연동 (Datadog/NewRelic 등) — 도구 도입 ADR
- 보안/남용 4xx의 metric/alert/threshold 정책 — 인증/보안 ADR (ADR-0005 §7 범위 제한 참조)
- 구조화 로그 schema 표준화 (필드 이름/타입 카탈로그) — 운영 단계에서 별도 검토

**전제**: PLAN-0005-A 머지 (필요 시 PLAN-0005-B와 무관하게 진행 가능).

**PLAN-0005-A 내부 phase 분리 이유**: 리뷰 포커스가 다르므로 별개 커밋으로 분리한다 — Phase 1은 새 추상화·경계 설계(BusinessException 계약, ErrorCategory enum, 매퍼 격리, ErrorResponse 스키마), Phase 2는 throw-site 치환 정확도(어느 도메인 가드를 어떤 서브타입으로, context 키, import 경로). 두 종류의 결함을 다른 시야로 봐야 한다.

**단, 두 phase는 독립 배포 단위가 아니다.** Phase 1은 `GlobalExceptionHandler` 재작성을 포함하므로 단독 머지 시 기존 `PostNotFoundException`/`IllegalArgumentException`이 안전망으로 떨어져 일시적 회귀(404/400 → 500)가 발생하고, Phase 2는 `BusinessException` 부재로 단독 컴파일이 안 된다. 따라서 두 커밋은 **반드시 같은 PR 안에서 함께 머지**된다.

Phase 2 도중 골격 수정이 필요해지면 **별도 후속 Plan으로 분기**한다 (구체 번호는 ADR-0004 §3 명명 규약을 따라 그 시점에 부여; 현재 규약에 하위 접미사를 신설하지 않는다).

**한 번에 묶지 않는 이유**: 위 세 영역은 변경 주기가 다르다. 운영 도구(JSON encoder 교체 등)를 바꾸려고 API 계약(ErrorResponse 스키마)까지 같이 흔들 일을 만들지 않는다.

## Consequences

### 긍정

- 새 도메인이 추가되어도 ErrorCode enum에 항목 추가 + Exception 서브클래스 1개로 끝난다. 핸들러는 변경되지 않는다.
- 응답 스키마/에러 코드라는 **API 계약**이 한 자리(ErrorCode enum)에 모여 카탈로그처럼 조회 가능하다.
- DTO/도메인 검증 책임이 카탈로그로 문서화되어, Comment/Auth/Search가 들어와도 같은 골격 위에 쌓을 수 있다.
- MDC + `X-Trace-Id` 헤더로 사용자 신고 1건의 추적이 가능해진다.

### 부정 / 비용

- ErrorCode enum이 시간이 지나면 비대해진다. 카탈로그 가독성은 유지되지만, 도메인이 늘어나면 enum 분리(예: `PostErrorCode`, `CommentErrorCode`)를 고민해야 할 수 있다 → 별도 ADR.
- `BusinessException.context` 규약은 코드 리뷰로 통제한다. 통제가 약해지면 로그 스키마가 오염될 위험이 있다.
- traceId를 헤더 계약으로 둔 결정은 추후 분산 트레이싱(W3C Trace Context 등) 도입 시 명세 정합 검토가 필요하다.

### 향후 ADR 후보

- `SystemException` 분류 도입 (retryable / non-retryable 분기 등이 필요해진 시점)
- ErrorCode enum 분리 정책 (도메인별 enum vs 단일 enum)
- 분산 트레이싱 표준 적용 (`traceparent` 헤더 등)
- 인증/인가 정책 (Spring Security 도입 시) — 보안/남용 4xx의 metric·alert·차단 정책 포함 (§7 범위 제한 참조)
- **입력 검증 에러 계약 통일** (PLAN-0011 리뷰에서 발견, 2026-06-07) — 현재 발견된 한계:
  1. email 형식 / nickname 허용문자가 DTO `@Pattern`(§5 form-layer) 누락으로 VO 까지 흘러 `INVALID_USER_CONTENT` 로 응답됨. §5 의도대로면 form-layer 에서 `VALIDATION_FAILED` + `errors[]` 가 나와야 하는 *정합 갭*.
  2. `INVALID_USER_CONTENT` 가 사용자 입력 오류(email/nickname/password 형식)와 내부 불변식(`passwordHash` blank = 서버 버그, 400 부적합)을 한 코드에 혼재.
  3. `errors[]` 가 `field` + message 뿐이라 안정적 상세 code(예: `EMAIL_INVALID_FORMAT`, `NICKNAME_INVALID_CHARACTERS`)가 없어 클라이언트 i18n/분기에 약함.
  4. DTO regex 와 VO regex 가 규칙을 이중 정의 → divergence 위험.
  - 통일 방향: 같은 의미의 입력 실패는 경계(@Pattern)·VO 어디서 잡히든 *동일 외부 계약*(`VALIDATION_FAILED` + 상세 code 포함 `errors[]`)으로 수렴. 경계·VO 가 동일 규칙/코드/메시지를 공유(예: VO 정책 객체를 재사용하는 `@ValidEmail`/`@ValidNickname` 커스텀 제약)하고, 내부 불변식은 사용자 검증 오류에서 분리(→ 5xx/내부 예외). 통일 대상은 *내부 예외 타입*이 아니라 *외부 API 오류 계약*이다.
  - 본 ADR(§5) 의 계층 분리 원칙 자체는 유효 — 위는 그 위에서의 정합·세분화이며 별도 ADR(0005 amend/supersede 검토) + Plan 으로 진행한다. PLAN-0011 은 본 ADR 의 현재 모델을 준수한 채 머지된다.

## Related

- ADR-0003: Clean/Hexagonal Architecture + DDD + CQRS
- ADR-0004: 초기 단계 정책 — 골격 우선, 디테일 점진적 보강 (본 ADR이 그 정책에서 "디테일 점진적 보강"의 첫 사례)
- PLAN-0005-A: 에러 모델 표준화
- PLAN-0005-B: 입력 검증 표준화
- PLAN-0005-C: 관측성 / 로깅 표준화
