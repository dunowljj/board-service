# PLAN-0005-C: 관측성 / 로깅 표준화

<!-- 상층: 승인 게이트 — 방향/범위/완료 기준 -->

## Goal

ADR-0005 §4·§7에 정의된 관측성·로깅 정책을 도입한다. 모든 요청에 traceId를 발급해 MDC와 응답 헤더 `X-Trace-Id`에 노출하고, `GlobalExceptionHandler`가 분류별 로그(WARN 한 줄 / ERROR + full stack)를 구조화 필드로 남긴다. 운영 환경은 JSON, 개발은 텍스트 패턴.

기대 효과: 사용자 신고 1건이 들어오면 `X-Trace-Id` 헤더 한 값으로 서버 로그를 grep해 원인 추적 가능.

## Scope

PLAN-0005-A/B와 동일하게 두 phase로 구성. **리뷰 포커스가 다르므로 별개 커밋으로 분리**하되, **두 phase는 같은 PR 안에서 함께 머지**한다 — Phase 1만 머지하면 traceId가 MDC에 들어가지만 핸들러가 로그를 남기지 않아 추적 동선 미완성.

### Phase 1 — TraceId 인프라 (필터 + MDC + 응답 헤더)

- `adapter/in/web/observability/TraceIdFilter` 신설 (`OncePerRequestFilter` 상속).
  - 요청 진입 시: `X-Trace-Id` 헤더가 있고 **최소 위생 검증** 통과(공백 제거 후 비어있지 않음 / 길이 ≤ 128 / 제어문자 미포함)하면 그 값을 사용, 그렇지 않으면 `UUID.randomUUID().toString()` 새로 발급. 분산 트레이싱 형식(W3C `traceparent` 등) 검증은 본 Plan 범위 외(별도 ADR).
  - MDC에 `traceId`, `method`, `path` 세 키 주입.
  - 응답 헤더 `X-Trace-Id`로 동일 ID 노출 (ADR-0005 §4).
  - finally 블록에서 **본 필터가 put한 세 키만** `MDC.remove(...)` 호출. `MDC.clear()` **금지** — 다른 필터(Security/Tracing 등)가 같은 MDC를 쓸 때 그 키까지 함께 지워짐. 더 안전한 옵션은 진입 직전 이전 값 백업 → finally에서 복원이지만, 본 Plan 단계엔 필터가 traceId만 다루므로 명시적 `remove` 세 번이면 충분.
- 필터 등록: `@Component`로 자동 등록되거나 `FilterRegistrationBean`으로 명시 등록 — implementer 재량. 우선순위는 `Ordered.HIGHEST_PRECEDENCE` 근처 (다른 필터보다 먼저 traceId가 MDC에 있어야 함).

### Phase 2 — 로깅 정책 적용 (logback 설정 + 핸들러 로깅)

- `GlobalExceptionHandler`의 주요 예외 진입점에 SLF4J 로그 추가:
  - `handleBusiness` (4xx BusinessException) → `log.warn` 한 줄. stack trace 없음. 필드: `code`, `ctx` (MDC가 traceId/method/path 자동 주입).
  - `handleMethodArgumentNotValid` / `handleHandlerMethodValidationException`의 request parameter 검증 실패(`isForReturnValue()` false) → `log.warn` 한 줄. 필드: `code=VALIDATION_FAILED`, `errors`.
  - `handleExceptionInternal` (기타 framework 4xx → MALFORMED_REQUEST) → `log.warn` 한 줄. 필드: `code`, `exceptionClass` (예외 타입만, stack은 없음).
  - `handleUnexpected` (5xx INTERNAL_ERROR 안전망) → `log.error` + **full stack with cause chain**. ADR-0005 §7대로 서버 로그가 유일한 원인 추적 수단.
  - `handleHandlerMethodValidationException`의 return-value 검증 실패(`isForReturnValue()` true) → `INTERNAL_ERROR` body로 응답하고 `log.error` + full stack (5xx 카테고리이므로).
- `logback-spring.xml` 신설:
  - default (dev/local 프로파일): 사람 읽기 쉬운 텍스트 패턴. traceId/method/path를 MDC에서 꺼내 prefix.
  - `prod` 프로파일: Spring Boot 4 내장 structured logging으로 JSON 출력. `application-prod.yml`에서 `logging.structured.format.console=ecs`(또는 `logstash`)를 지정하고, custom `logback-spring.xml`의 prod appender는 Boot의 structured console appender를 include하거나 `StructuredLogEncoder`에 `${CONSOLE_LOG_STRUCTURED_FORMAT}`을 넘겨 해당 설정을 실제로 반영한다.
- `application.yml`에 프로파일별 분기 또는 `application-prod.yml` 분리 — implementer 재량.

### 양 Phase 공통

- 본 Plan은 자동 테스트를 작성하지 않는다 — 후속 test-strategy Plan에서 처리. PLAN-0005-A/B와 일관.
- 동작 검증은 로컬 `bootRun` + curl + 로그 출력 육안 확인.

## Non-goals

- 분산 트레이싱 표준 (W3C Trace Context, `traceparent` 헤더, span 전파 등) — 별도 ADR.
- APM 연동 (Datadog / NewRelic / Tempo / Jaeger) — 도구 도입 ADR.
- 보안/남용 4xx의 metric / alert / threshold / 자동 차단 — ADR-0005 §7 범위 제한대로 인증/보안 ADR.
- 구조화 로그 schema 표준화 (필드 이름·타입 카탈로그) — 운영 단계에서 별도 검토.
- 액세스 로그 (every request hit) — Tomcat access log 기본값 사용 또는 후속.
- 자동 테스트 작성 — test-strategy Plan.
- 도메인 / 애플리케이션 레이어의 비즈니스 로깅 (도메인 이벤트, audit log) — 본 Plan은 *예외 경로* 로깅만 다룸. 비즈니스 로깅은 별도 정책.

## Related ADRs

- ADR-0005 §4 (`X-Trace-Id` 응답 헤더), §7 (로깅 정책 — 분류별 레벨/포맷, MDC, 구조화) — 본 Plan의 직접 근거.
- ADR-0003: Hexagonal — 필터는 `adapter/in/web/`, 로깅은 핸들러(어댑터) 책임. 도메인/유스케이스에 영향 없음.
- ADR-0004: 골격 우선 — 본 Plan은 *관측성 골격*까지. 분산 트레이싱·APM 같은 디테일은 후속.

## Acceptance Criteria

1. `TraceIdFilter`가 `adapter/in/web/observability/`에 위치하며 `OncePerRequestFilter`를 상속한다. MDC에 `traceId`, `method`, `path` 세 키를 주입하고 finally 블록에서 본 필터가 put한 세 키만 `MDC.remove(...)`로 제거한다 (`MDC.clear()` 금지, 코드 리뷰로 leak 방지 확인).
2. 모든 응답에 `X-Trace-Id` 헤더가 포함된다 (`curl -i` 출력에서 확인). 요청의 `X-Trace-Id`가 최소 위생 검증을 통과했다면 trim된 값이 응답에 echo되고, 실패했다면 새 UUID가 응답된다.
3. `GlobalExceptionHandler`의 주요 예외 진입점 + return-value 검증 분기에 SLF4J 로그가 추가됐다:
   - 4xx 분류 → `log.warn`, stack trace 없음
   - 5xx 분류(INTERNAL_ERROR 안전망 + return-value 검증) → `log.error` + 전체 cause chain
4. `logback-spring.xml`이 존재하며 default(dev)는 텍스트 패턴, `prod` 프로파일은 JSON 구조화 출력. traceId/method/path가 모든 로그 라인에 등장 (MDC 자동 주입).
5. 기존 외부 API 동작 회귀 없음 + 신규 검증 시나리오:
   - PLAN-0005-A의 4 시나리오 + PLAN-0005-B의 신규 2 시나리오 모두 응답 헤더에 `X-Trace-Id` 포함.
   - 같은 traceId 값이 서버 로그에서 grep 가능.
   - 5xx 시나리오(예: 의도적으로 던지는 RuntimeException) 발생 시 stack trace가 로그에 출력됨.
6. 본 Plan은 자동 테스트를 작성하지 않는다. 기존 `BoardServiceApplicationTests`는 회귀 없이 통과한다 (`./gradlew test`). AC5 시나리오는 implementer가 로컬 실행으로 확인하고 PR description에 결과(curl + 로그 일부) 명시.
7. 두 phase는 별개 커밋으로 분리된다 (`feat(observability): traceId filter + MDC + X-Trace-Id header (PLAN-0005-C Phase 1)`, `feat(observability): structured logging policy + logback config (PLAN-0005-C Phase 2)`).

## ADR Required

**no** — ADR-0005 §4·§7이 본 Plan의 정책 근거를 모두 정의함. 본 Plan은 그 정책을 구현할 뿐.

## Risks

1. **MDC 정리 누락 → 스레드 풀 leak** — `OncePerRequestFilter.doFilterInternal`에서 `try { ... } finally { MDC.remove(traceId/method/path); }` 패턴 강제. 누락 시 다음 요청이 이전 요청의 traceId로 로그를 남기는 cross-request 오염. 단, `MDC.clear()`는 본 필터가 소유하지 않은 다른 MDC 키까지 삭제할 수 있으므로 사용하지 않는다. 코드 리뷰 + 로컬 다중 요청 테스트로 확인.

2. **클라이언트 주입 traceId의 보안 고려** — `X-Trace-Id` 헤더를 외부 신뢰 게이트웨이가 발급한다는 *전제*가 깨지면, 악의적 클라이언트가 traceId를 주입해 로그 검색을 교란할 수 있다. 본 Plan 단계에선 인증/게이트웨이가 없으므로 UUID 형식까지 강제하지는 않되, 공백 제거 후 비어있지 않음 / 길이 ≤ 128 / 제어문자 미포함의 최소 위생 검증을 통과한 값만 echo한다. 인증/보안 ADR에서 정책 강화 (예: UUID 형식만 허용 또는 항상 서버 발급).

3. **JSON 인코더 의존성 추가의 운영 영향** — `logstash-logback-encoder` 도입 시 logback 클래스패스 확장. Spring Boot 4의 `logging.structured.format.console` 내장 옵션이 충분하면 외부 의존성 회피 가능. Implementer가 먼저 내장 옵션 시도, 부족하면 의존성 추가하고 PR description에 결정 이유 명시.

4. **PLAN-0005-A/B 핸들러와의 코드 충돌** — 본 Plan의 Phase 2가 `GlobalExceptionHandler`의 주요 예외 진입점을 수정한다. PLAN-0005-A/B로 안정된 핸들러 흐름을 *건드리는* 변경이므로, 회귀 위험은 로깅 호출이 *응답 빌드 후* 들어가도록 위치를 끝부분에 두어 제어 흐름 영향을 최소화. 응답 자체는 return-value 검증 실패를 `INTERNAL_ERROR`로 분기하는 보정 외에는 변경하지 않는다.

5. **로그 비용 / volume** — 모든 4xx에 WARN 로그를 남기면 클라이언트 실수가 많은 환경에서 로그가 비대해질 수 있다. ADR-0005 §7대로 *집계 가능한 구조화 필드*로 남기므로 검색·필터링은 가능하지만, 로그 저장 비용은 운영 단계에서 모니터링 후 sampling/level 조정 검토. 본 Plan은 sampling 도입 안 함.

6. **Spring Boot structured logging 옵션의 안정성** — `logging.structured.format.console`은 Spring Boot 3.4+ 기능. Boot 4.0.2에서 안정 동작 가정하지만 행여 ECS/Logstash 포맷 호환성 문제 발견 시 logstash-encoder로 fallback.

---

<!-- 하층: 실행 재량 — 코드베이스 충돌 시 갱신 가능 -->

## Required Reading

- `docs/adr/0003-clean-architecture-ddd-hexagonal.md`
- `docs/adr/0004-foundation-first-policy.md`
- `docs/adr/0005-exception-error-response-policy.md` (특히 §4 응답 스키마, §7 로깅 정책)
- `docs/plans/done/PLAN-0005-A-error-model-skeleton.md` (Implementation Hints의 GlobalExceptionHandler 절 — 본 Plan이 그 핸들러에 로깅 추가)
- `docs/plans/done/PLAN-0005-B-input-validation.md` (머지 후 done/으로 이동될 예정 — 동일 핸들러에 검증 분기 추가됨)
- `CLAUDE.md`
- 현재 상태 파악용:
  - `src/main/java/com/dunowljj/board/adapter/in/web/exception/GlobalExceptionHandler.java`
  - `src/main/resources/application.yml` 또는 `application.properties`
  - `build.gradle` (logging 의존성 확인)

## Files to Touch

### Phase 1 — TraceId 인프라

#### NEW
- `src/main/java/com/dunowljj/board/adapter/in/web/observability/package-info.java` (간단한 패키지 설명)
- `src/main/java/com/dunowljj/board/adapter/in/web/observability/TraceIdFilter.java`

#### Tests
> Tests: 본 Plan 범위 외. 후속 test-strategy Plan에서 작성.

### Phase 2 — 로깅 정책 적용

#### NEW
- `src/main/resources/logback-spring.xml`
- (선택) `src/main/resources/application-prod.yml` 또는 기존 `application.yml`에 `spring.profiles.*` 분기

#### MODIFY
- `src/main/java/com/dunowljj/board/adapter/in/web/exception/GlobalExceptionHandler.java` — 5개 핸들러 진입점에 SLF4J 로그 호출 추가
- `build.gradle` — JSON 인코더 의존성 추가 시 (Spring Boot 내장으로 충분하면 변경 없음)

#### Tests
> Tests: 본 Plan 범위 외. 후속 test-strategy Plan에서 작성.

## Implementation Hints

> 구조 골격 수준만 적는다. 의사코드 금지.

### `adapter/in/web/observability/TraceIdFilter.java`
- `extends OncePerRequestFilter`.
- 상수: `static final String HEADER = "X-Trace-Id"`, `MDC_TRACE_ID = "traceId"`, `MDC_METHOD = "method"`, `MDC_PATH = "path"`.
- `doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)`:
  - candidate = `req.getHeader(HEADER)`를 trim한 값.
  - traceId = candidate가 비어있지 않고 길이 ≤ 128이며 제어문자를 포함하지 않으면 candidate, 아니면 `UUID.randomUUID().toString()`.
  - `res.setHeader(HEADER, traceId)`.
  - `MDC.put(MDC_TRACE_ID, traceId)` / `put(MDC_METHOD, req.getMethod())` / `put(MDC_PATH, req.getRequestURI())`.
  - `try { chain.doFilter(req, res); } finally { MDC.remove(MDC_TRACE_ID); MDC.remove(MDC_METHOD); MDC.remove(MDC_PATH); }`.
- `@Component` 또는 `FilterRegistrationBean`으로 등록.

### `adapter/in/web/exception/GlobalExceptionHandler.java` 로깅 추가
- 클래스 상단: `private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);`.
- 각 핸들러 메서드 *반환 직전*에 로그 호출 (응답 빌드 후, side-effect 분리). Spring Boot structured logging이 `addKeyValue(...)`를 JSON 필드로 반영할 수 있도록 SLF4J fluent API를 우선 사용:
  - `handleBusiness` →
    ```java
    log.atWarn()
            .addKeyValue("code", ex.errorCode().code())
            .addKeyValue("ctx", ex.context())
            .log("business exception");
    ```
    (traceId/method/path는 MDC가 자동 주입.)
  - request body / request parameter validation 실패 →
    ```java
    log.atWarn()
            .addKeyValue("code", ErrorCode.VALIDATION_FAILED.code())
            .addKeyValue("errors", errors)
            .log("validation failed");
    ```
  - `handleHandlerMethodValidationException`은 먼저 `ex.isForReturnValue()`를 검사:
    - `false`: `VALIDATION_FAILED` body + 위 WARN 로그.
    - `true`: `INTERNAL_ERROR` body + 아래 ERROR 로그. 이 분기는 클라이언트 입력 오류가 아니라 컨트롤러가 잘못된 반환값을 만든 서버 버그다.
  - `handleExceptionInternal` (4xx framework) →
    ```java
    log.atWarn()
            .addKeyValue("code", ErrorCode.MALFORMED_REQUEST.code())
            .addKeyValue("exceptionClass", ex.getClass().getSimpleName())
            .log("malformed request");
    ```
  - `handleHandlerMethodValidationException`의 return-value 분기 (5xx) →
    ```java
    log.atError()
            .addKeyValue("code", ErrorCode.INTERNAL_ERROR.code())
            .addKeyValue("exceptionClass", ex.getClass().getSimpleName())
            .setCause(ex)
            .log("return-value validation failed");
    ```
  - `handleUnexpected` (5xx 안전망) →
    ```java
    log.atError()
            .addKeyValue("code", ErrorCode.INTERNAL_ERROR.code())
            .addKeyValue("exceptionClass", ex.getClass().getSimpleName())
            .setCause(ex)
            .log("unexpected exception");
    ```
- import: `org.slf4j.{Logger,LoggerFactory}`.

### `src/main/resources/logback-spring.xml`
- `<configuration>` 루트.
- `<springProfile name="!prod">` 블록: `ConsoleAppender` + 패턴 (예시)
  ```
  %d{HH:mm:ss.SSS} [%X{traceId:-NO_TRACE}] %-5level %logger{36} method=%X{method} path=%X{path} - %msg%n
  ```
- `<springProfile name="prod">` 블록: 다음 둘 중 하나
  - **A (우선)**: Spring Boot 4 내장 structured logging — `application-prod.yml`에 `logging.structured.format.console: ecs` (또는 `logstash`)를 두고, prod appender는 Boot의 structured console appender를 include하거나 `StructuredLogEncoder`에 `${CONSOLE_LOG_STRUCTURED_FORMAT}`을 넘겨 이 설정을 반영한다.
  - **B (fallback)**: `net.logstash.logback.encoder.LogstashEncoder` 사용 (의존성 추가 필요). Boot 내장 방식으로 요구 포맷을 만족하지 못할 때만 선택하고 PR description에 이유를 명시.
- `<root level="INFO">` + 적절한 appender ref.

### `application.yml` (또는 분리 파일)
- 현재 properties 또는 yml 어느 쪽인지 확인 후 일관성 유지.
- prod 프로파일에서 `logging.structured.format.console` 설정 (방안 A 채택 시).

### 커밋 메시지 (참고)
- Phase 1: `feat(observability): traceId filter + MDC + X-Trace-Id header (PLAN-0005-C Phase 1)`
- Phase 2: `feat(observability): structured logging policy + logback config (PLAN-0005-C Phase 2)`
