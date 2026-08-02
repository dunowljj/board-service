# PLAN-0012-C: `errors[].code` 전역 도입 (안정적 필드 에러 머신 코드)

> **패밀리 노트**: ADR-0005 §5(검증 책임 분리) 통일 결정의 슬라이스 C(마지막). 형제: **-A** User 경계
> 통일(완료·머지·archived), **-B** Post 경계 통일(완료·머지·archived). 슬라이스별 별도 Plan·PR·archival.

ADR-0005 §5.1 의 `deferred` 항목 — *"안정적 `errors[].code`(머신 코드)의 전역 도입(모든 제약 +
`GlobalExceptionHandler` + Post/query 갱신)"* — 을 실행한다. -A/-B 가 **누가 검증하는가**(경계 vs VO,
단일 출처)를 통일했다면, -C 는 **검증 실패를 어떻게 표현하는가**(응답 계약)를 통일한다.

<!-- 상층: 승인 게이트 -->

## Goal

1. **프로그램적 분기 가능한 필드 에러.** 현재 `errors[]` 엔트리는 `{field, reason}` 뿐이고 `reason` 은
   *한국어 표시 문구*다. 클라이언트가 "이 필드가 왜 틀렸는지"로 분기하려면 **한국어 문자열을 파싱**해야
   한다 — 문구를 고치는 순간 클라이언트가 깨지는 결합. `code` 를 추가해 표시 문구와 기계 판정을 분리한다.
2. **문구 변경의 자유 회복.** 현재 `AuthE2EIT`/`PostE2EIT` 가 `reason` 을 *exact* 로 고정하고 있고
   (`-A` round-5, `-B` 에서 확립), 이는 "code 미도입 동안 reason 이 계약"이라는 임시 상태의 산물이다
   (`AuthE2EIT:175` 주석이 명시). `code` 도입 후 계약의 무게중심을 `code` 로 옮긴다.
3. **i18n 의 전제 조건 확보.** ADR-0005 는 `-C` 를 *"i18n / 프로그램적 분기 필요 시점"* 으로 미뤘다.
   본 Plan 은 i18n 자체를 도입하지 않되(Non-goals), 도입 시 필요한 **안정 키**를 먼저 세운다.
4. **전역 커버리지.** `@RequestBody` DTO(User/Post)와 `@RequestParam` query(`page`/`size`) **양쪽 경로**가
   같은 `code` 어휘를 쓴다. deferred 문구의 *"모든 제약 + `GlobalExceptionHandler` + Post/query 갱신"*.

## Scope

### 1. `errors[]` 엔트리에 `code` 추가 (**additive** 셰이프 변경)

```jsonc
// before
{"field": "title", "reason": "제목은 200자 이하여야 합니다"}
// after
{"field": "title", "code": "TOO_LONG", "reason": "제목은 200자 이하여야 합니다"}
```

`field`/`reason` 키와 값은 **불변** — 기존 클라이언트·테스트는 그대로 통과한다(하위 호환). 최상위
`code`(`VALIDATION_FAILED`)와 HTTP 상태(400)도 불변.

### 2. `ValidationErrorCode` enum 신설 (web adapter 소유)

필드 에러 코드는 응답 최상위 `code` 와 **축이 다르다** — 최상위는 *응답 1건의 분류*
(`VALIDATION_FAILED`), 필드 코드는 *제약 1건의 실패 종류*. 따라서 `common/error/ErrorCode` 에 섞지 않고
`adapter/in/web/error/ValidationErrorCode` 로 분리한다(§ADR-0005 §5.1 "`code`/`message`(표현 관심사)는
web 계층이 소유" 와 정합). 초기 어휘(제약 인벤토리에서 도출):

| code | 발생 제약 | 현재 사용처 |
|---|---|---|
| `REQUIRED` | `@NotBlank`, `@NotNull` | email·nickname·password·title·body |
| `TOO_SHORT` | `@Size(min=…)` 하한 위반 | password(`min=8`) |
| `TOO_LONG` | `@Size(max=…)` 상한 위반, `@MaxUtf8Bytes` | title·body, password(72 bytes) |
| `OUT_OF_RANGE` | `@Min`, `@Max` | query `page`(`@Min(0)`), `size`(`@Min(1) @Max(100)`) |
| `INVALID_FORMAT` | `@ValidEmail`, `@ValidNickname` | email, nickname |
| `INVALID` | 매핑되지 않은 제약 (fallback) | — (현재 해당 없음) |

### 3. 제약 → 코드 파생을 **핸들러 중앙 매핑**으로 (제약 애너테이션 미수정)

`GlobalExceptionHandler` 가 위반의 *제약 애너테이션 타입*을 읽어 코드를 파생한다. **DTO·validator 파일은
한 줄도 바꾸지 않는다.** 근거:

- **중복 0.** 제약마다 코드를 손으로 적으면 18개 제약 인스턴스에 코드가 흩어지고, `-B` 리뷰 L1(메시지
  문구 중복)과 같은 문제를 코드에서 반복한다.
- **자동 커버리지.** 새 DTO/제약이 추가돼도 코드가 자동으로 붙는다 — deferred 문구의 "전역"에 부합.
- **표현 관심사 유지.** 한국어 `message` 는 지금처럼 애너테이션이 소유(문구는 필드마다 달라야 하므로),
  `code` 는 제약 종류에서 파생(종류가 같으면 코드도 같아야 하므로). 두 관심사의 자연스러운 분업.

두 검증 경로 모두 커버하되, **제약 메타데이터 추출 API 가 경로별로 다르다**(Spring 7.0.3 에서 실측 확인):

| 경로 | 예외 | 추출 |
|---|---|---|
| `@Valid @RequestBody` | `MethodArgumentNotValidException` | `FieldError.unwrap(ConstraintViolation.class)` |
| `@RequestParam` 제약 | `HandlerMethodValidationException` | `ParameterValidationResult.unwrap(error, ConstraintViolation.class)` |

`ConstraintViolation` 을 얻으면 `getConstraintDescriptor().getAnnotation()`(타입) 과
`getAttributes()`(min/max 등) 이 함께 나오므로 `@Size` 방향 판정(Risks 4)까지 한 번에 해결된다.
`getCodes()` 기반 이름 파싱은 **주경로가 아니라 fallback** 이다 — 제약 simple name 만 주고 속성을 못 줘서
`TOO_SHORT`/`TOO_LONG` 을 가를 수 없다.

### 4. `errors[]` 엔트리 타입 `Map<String,String>` → record

`ValidationError(String field, String code, String reason)` record 로 교체한다 — 키가 3개로 늘어난
`Map.of` 보다 타입 안전하고, 필드 추가 시 컴파일러가 누락을 잡는다. api-standards 규약대로
`adapter/in/web/dto/response/` 에 둔다.

> JSON 객체의 **키 순서는 수용 기준으로 삼지 않는다** — RFC 8259 상 의미가 없다. record 채택 근거는
> 타입 안전성이며, 직렬화 순서를 테스트로 못 박지 않는다(`@JsonPropertyOrder` 도 두지 않음).

### 5. 코드 어휘 exhaustiveness 가드

fallback(`INVALID`)은 런타임 안전장치일 뿐 정답이 아니다. **main 에서 실제 사용 중인 모든 제약
애너테이션이 non-fallback 코드로 매핑되는지**를 테스트로 고정한다(`TestStrategyArchitectureTest` 의
forward-defense 관행). 새 제약을 도입하면서 매핑을 빠뜨리면 빌드가 깨진다.

스캔 범위는 **`dto/request` 패키지 + 모든 `@RestController` 의 메서드 파라미터**로 일반화한다. 현재
query 제약은 `PostController` 에만 있지만 컨트롤러는 4개(`Post`/`User`/`Auth`/`Csrf`)이고, 한 컨트롤러만
스캔하면 "전역" 주장이 성립하지 않는다(다른 컨트롤러가 query 제약을 추가할 때 누락).

### 6. 테스트 층위 — 각 층이 서로 다른 것을 증명한다

같은 사실을 세 층에서 못 박으면 문구 하나 바꿀 때 세 곳이 깨진다. 역할을 분리한다:

| 층 | 증명 대상 | 범위 |
|---|---|---|
| `ValidationErrorCodeTest`(단위) | 파생 규칙 **전수 매트릭스** — `@Size` min/max 판정, fallback 사다리 | 조합 전부 |
| `AuthE2EIT` / `PostE2EIT`(E2E) | **두 추출 경로**가 실제로 동작하고 직렬화되어 나옴 | 대표 케이스 |
| `PostControllerTest`(슬라이스) | 기존 `field`·`VALIDATION_FAILED` 단언 | **변경 없음** |

- 응답 계약 단언은 **E2E 로 통일**한다 — PLAN-0012-B 가 명시적으로 정한 관행(*"E2E 로 통일 …
  PostControllerTest slice 아님"*)을 따른다. query 제약만 슬라이스로 내리면 "RequestBody 계약은 E2E,
  query 계약은 slice"라는 근거 없는 이원 규칙이 생기고, **AC 의 "두 경로 동일 어휘"를 한 파일에서
  대조할 수 없게 된다**.
- `PostE2EIT` 는 이미 존재하고 컨테이너도 이미 떠 있으므로, 케이스 추가 비용은 새 컨텍스트가 아니라
  MockMvc 호출 수 밀리초다. ADR-0006 이 억제하는 것은 `@SpringBootTest` **컨텍스트의 확산**이지 한 클래스
  안의 단언 수가 아니다.
- 기존 `reason` exact 단언은 **유지**하되(회귀 방어) 각 케이스에 `code` 단언을 **추가**한다.
  `AuthE2EIT:175` 의 "code 미도입" 주석을 갱신한다.

## Non-goals

- **i18n 도입**(`ValidationMessages.properties`, `Locale` 협상, `Accept-Language`) — `code` 가 전제 조건일
  뿐, 본 Plan 은 한국어 `reason` 을 그대로 둔다. 필요 시점에 별도 Plan.
- **필드별 비즈니스 코드**(`TITLE_TOO_LONG`, `PASSWORD_TOO_SHORT` 등) — `{field, code}` 조합으로 이미
  식별 가능하므로 코드에 필드명을 중복 인코딩하지 않는다(아래 Risks 3 참조).
- **최상위 `code` / `ErrorCode` enum / HTTP 상태 매핑 변경** — 불변.
- **`BusinessException` 계열(4xx 도메인 예외)의 `errors[]` 편입** — 도메인 예외는 필드 단위가 아니라
  응답 단위 코드를 쓴다(현행 유지). ADR-0005 §6(도메인 예외 세분화)의 영역.
- **제약 자체의 추가·변경**(새 검증 규칙) — 표현 계층만 다룬다.
- **`reason` exact 단언 제거** — 이번엔 `code` 를 *추가*만 한다. 문구 계약 완화는 클라이언트가 `code` 로
  이전한 뒤의 후속 판단.
- **타입 불일치·malformed JSON 경로에 `errors[]` 부여** — `page=abc` 같은 변환 실패는
  `handleExceptionInternal` → `MALFORMED_REQUEST` 로 흐르며 **`errors[]` 자체가 없다**(필드 단위 위반이
  아니라 요청 파싱 실패). "전역 도입"은 *Bean Validation 위반이 만드는 `errors[]`* 범위이며, 이 경로는
  현행 유지한다.
- **query `size` 하한(`@Min(1)`) 커버리지 보강** — `size=0` 을 거부하는지 검증하는 테스트가 현재 아예
  없다(선재 구멍, `PostControllerTest` 는 `page=-1`/`size=101` 만). `@Min(0)` 과 애너테이션 타입이 같아
  `code` 파생 관점에서 새로 증명하는 게 없으므로 **본 Plan 에서 다루지 않는다**. 별도로 슬라이스 단언
  한 줄을 추가할지는 독립 판단.

## Related ADRs

- **ADR-0005 §5.1** — `deferred` 항목(`errors[].code` 전역 도입)을 **resolved** 로 갱신 + 새 하위 절로
  코드 어휘·파생 규칙·확장 규약 명문화. §"향후 ADR 후보/해소 원장"의 남은 후속 ③도 함께 해소.
- **ADR-0005 §5.1 "복잡 검증의 애플리케이션 계층 이관 기준" 5번 항목(174행) — 개정 필수.** 현재 조문은
  *"에러 구조가 `{field, reason}` 보다 풍부해야 함 — 머신 code, 중첩/배열 path, 비즈니스 분류 등"* 이
  해당하면 **애플리케이션 계층으로 이관**하라고 규정한다. 본 Plan 은 *경계에서* 머신 코드를 붙이므로
  이 조문과 문자 그대로 충돌한다. 원 의도는 "규칙 하나가 Bean Validation 으로 표현 불가할 때"였으나
  조문이 그렇게 읽히지 않으므로, **"필드 단위를 넘는 구조(중첩/배열 path, 비즈니스 분류) — 단순 머신
  코드는 경계가 제공"** 취지로 다시 쓴다. 개정 없이 구현하면 ADR 이 자기모순 상태로 남는다.
- **ADR-0005 §4(에러 응답 스키마, 98행) — 개정 필수.** 116–129행의 검증 실패 응답 **예제가
  `{"field": "title", "reason": "must not be blank"}`** 로 되어 있어 `code` 추가 즉시 stale 이 된다.
  예제와 하위 불릿(`errors[]` 설명)을 함께 갱신한다.
- ADR-0005 §2 — `ErrorCode` 카탈로그(변경하지 않음). 필드 코드가 이 enum 에 들어가지 않는 근거 확인용.
- ADR-0004 §3 — ADR↔Plan 번호 `-A`/`-B`/`-C` 규약(본 Plan 이 `-C` 인 근거).

## Acceptance Criteria

- **셰이프**: 모든 검증 실패 응답의 `errors[]` 엔트리가 `{field, code, reason}` 3키를 가진다. `field`·
  `reason` 의 값은 변경 전과 **완전히 동일**(기존 E2E 단언 무수정 통과 = 회귀 0).
- **어휘 정확도**: 아래 매핑이 **E2E 로** 고정된다(`AuthE2EIT` = User/RequestBody, `PostE2EIT` =
  Post/RequestBody + query). 조합 전수는 `ValidationErrorCodeTest`(단위) 담당.
  - `@NotBlank`(title/email/nickname/password) → `REQUIRED`
  - `@NotNull`(body) → `REQUIRED`
  - `@Size(max)`(title/body 과길이) → `TOO_LONG`
  - `@Size(min=8)`(password 8자 미만) → `TOO_SHORT`
  - `@MaxUtf8Bytes`(password 72바이트 초과) → `TOO_LONG`
  - `@ValidEmail`/`@ValidNickname`(형식 위반) → `INVALID_FORMAT`
  - `@Min`/`@Max`(query `page=-1`, `size=101`) → `OUT_OF_RANGE`
- **두 경로 동일 어휘**: `@RequestBody` 경로(`FieldError.unwrap`)와 `@RequestParam` 경로
  (`ParameterValidationResult.unwrap`)가 같은 enum 어휘를 낸다 — 서로 다른 추출 API 를 쓰므로 이 대조가
  본 Plan 의 핵심 검증이다. **두 경로 케이스가 같은 E2E 클래스(`PostE2EIT`)에 있어야** 대조가 성립한다.
- **aggregation 보존**: 한 요청의 다중 필드 위반이 여전히 `errors[]` 에 모두 담긴다(ADR-0005 §5.1
  null-pass 관용구 불변). `@NotBlank`+`@Size` 동시 위반 시 2건이 나오는 선재 동작도 불변.
- **exhaustiveness**: `dto/request` + 모든 `@RestController` 파라미터에서 수집한 제약 애너테이션이
  전부 `INVALID` fallback 이 아닌 코드로 매핑됨을 테스트가 강제. 매핑 누락 시 빌드 실패.
- **관측 계약 확인**: 구조화 로그(`logback-spring.xml:15`, structured console appender)의
  `errors` key-value 페이로드에 `code` 가 포함되고, 형태 변화가 의도적임을 확인(아래 Risks 7).
- `./gradlew check` BUILD SUCCESSFUL (test + integrationTest + ArchUnit).

## ADR Required

**yes** — ADR-0005 제자리 amend, 단 **§5.1 한 곳이 아니라 3개 지점**을 함께 고쳐야 한다. 이유:

- **응답 계약 변경**이다. additive 라 하위 호환이지만 `errors[]` 셰이프는 **§4** 에 문서화된 공개 계약이고,
  `code` 어휘는 **한 번 노출하면 클라이언트가 의존하는 안정 식별자**가 된다(문구와 달리 자유롭게 못 바꿈).
- **기존 조문과의 충돌 해소**가 필요하다 — §5.1 이관 기준 5번(174행)이 "머신 code 가 필요하면 애플리케이션
  계층으로 이관"이라 규정하는데 본 Plan 은 경계에서 코드를 붙인다. 조문을 고치지 않으면 ADR 이 자기모순.
- **확장 규약**을 정해야 한다 — 새 제약이 추가될 때 기존 코드에 매핑할지 새 코드를 만들지의 기준. 이게
  없으면 어휘가 임의로 불어난다.
- deferred 항목의 resolved 전환이 원장에 기록되어야 한다.

**개정 대상 3곳** (모두 같은 커밋):

1. **§4(98행~)** — 검증 실패 응답 예제(116–129행)에 `code` 반영 + `errors[]` 설명 불릿 갱신.
2. **§5.1 이관 기준 5번(174행)** — "필드 단위를 넘는 구조가 필요함(중첩/배열 path, 비즈니스 분류) —
   단순 머신 코드는 경계가 제공"으로 재작성.
3. **§5.1 deferred 불릿 + §"향후 ADR 후보/해소 원장" ③** — resolved 로 전환 + 새 하위 절(코드 어휘·파생
   규칙·확장 규약·일반 vs 필드별 트레이드오프) 추가 + 상단 Amended 배너.

**supersede 아닌 amend** — §5.1 이 이미 "deferred, 별도 Plan" 으로 예고한 항목이고 §5 의 검증 책임 분리
표준 자체는 바뀌지 않는다(경계=형식, 도메인=의미 그대로). 바뀌는 것은 *경계 실패의 표현 방식*뿐이다.
**구현 전 ADR 개정 먼저**(-B 선례: ADR 커밋 → 구현 커밋).

## Risks

1. **`code` 는 문구보다 되돌리기 어렵다.** 한 번 `TOO_LONG` 을 노출하면 클라이언트가 분기에 쓰므로
   이름 변경이 breaking change 다. *완화*: 어휘를 **최소·일반적**으로 시작(6개), 필드명·도메인 개념을
   코드에 넣지 않음. 세분화는 나중에 추가(추가는 하위 호환, 변경은 아님).
2. **제약 메타데이터 추출이 Spring 구현에 의존.** 두 `unwrap` API 는 현 스택(Boot 4.0.2 / Spring 7.0.3)
   에서 존재를 실측 확인했다 — `ObjectError.unwrap(Class)`(`FieldError` 가 상속),
   `ParameterValidationResult.unwrap(MessageSourceResolvable, Class)`. 다만 `unwrap` 이 실제로
   `ConstraintViolation` 을 돌려주는지는 런타임 동작이라 **구현 착수 시 두 경로 모두 스파이크로 확인**한다.
   *완화*: **fallback 사다리**를 둔다 — ① `unwrap` 성공 → 애너테이션 타입 + 속성으로 정밀 파생,
   ② 실패 시 `getCodes()` 마지막 원소(제약 simple name)로 **이름 기반 파생**(`@Size` 는 방향을 못 가르므로
   보수적으로 `INVALID`), ③ 그것도 실패 시 `INVALID`. 무조건 `INVALID` 로 떨어뜨리는 것보다 degrade 품질이
   높고, 어느 단계에서도 응답이 깨지지 않는다. 단위 테스트가 ①을 고정하므로 Spring 업그레이드 시 조기 경보.
3. **일반 코드(`TOO_LONG`) vs 필드별 코드(`TITLE_TOO_LONG`) 선택.** 전자는 어휘가 작고 자동 파생이
   가능하나, 클라이언트가 항상 `field` 와 함께 봐야 한다. 후자는 단독 해석 가능하나 제약마다 수동
   선언이 필요하고(중복) 필드 추가마다 어휘가 증식한다. **일반 코드를 채택**하되, 이 트레이드오프를
   ADR 에 명시해 나중에 "왜 `TITLE_TOO_LONG` 이 아닌가"에 답할 수 있게 한다.
4. **`@Size` 의 min/max 양의성.** `@Size(min=8, max=…)` 한 애너테이션이 두 방향 실패를 낸다 — 애너테이션
   타입만으로는 `TOO_SHORT`/`TOO_LONG` 을 못 가른다. *완화*: 제약 속성(min/max)과 거부된 값의 길이를
   비교해 판정. 현재 사용처는 min-only(password) / max-only(title·body)로 갈려 있어 즉시 문제는 없으나,
   판정 로직이 없으면 min+max 동시 지정 시 오답이 나온다. **구현 시 판정 규칙 포함 + 테스트**.
5. **`@MaxUtf8Bytes` → `TOO_LONG` 매핑의 의미 손실.** char 길이 초과와 UTF-8 바이트 초과가 같은 코드로
   합쳐진다. *수용* — 클라이언트 관점에서 둘 다 "값이 너무 김"이고, `reason` 이 차이를 설명한다. 분리가
   필요해지면 `TOO_LONG_BYTES` 추가(하위 호환).
6. **`fieldName()` fallback(`argN`)과 코드의 조합.** query 파라미터명이 해석 안 되면 `field` 가 `arg0`
   가 되는 선재 동작이 있다(`GlobalExceptionHandler:176`). `code` 가 붙어도 이 경우 클라이언트 분기가
   여전히 부정확하다. *본 Plan 대상 아님* — 현재 `@RequestParam(name=…)` 이 명시돼 있어 발생하지 않음.
7. **구조화 로그 페이로드 형태 변경(관측 계약).** prod 는 structured console appender 를 쓰고
   (`logback-spring.xml:15`), `GlobalExceptionHandler.logValidationFailed` 가
   `addKeyValue("errors", errors)` 로 리스트를 그대로 싣는다. 엔트리 타입이 `Map` → record 로 바뀌면
   **로그에 찍히는 문자열 형태도 바뀐다**(`{field=…, reason=…}` → `ValidationError[field=…, code=…,
   reason=…]` 가능성 — 직렬화가 Jackson 경유인지 `toString()` 경유인지에 따라 다름). `code` 가 로그에
   들어가는 것 자체는 이득(관측에서 실패 종류 분기 가능, PLAN-0005-C 로깅 관행과 정합)이므로 **의도적
   채택**하되, 형태 변화를 인지하고 넘어간다. *확인 사항*: 로그 출력을 단언하는 기존 테스트가 있으면
   갱신 대상. 대시보드/알럿이 이 필드를 파싱하고 있다면 별도 고지 필요(현재 해당 없음으로 보이나
   구현 시 확인).

<!-- 하층: 실행 재량 -->

## Required Reading

- `docs/adr/0005-exception-error-response-policy.md` — §2(`ErrorCode` 카탈로그, 59행), **§4(에러 응답
  스키마 + `errors[]` 예제, 98–142행 — 개정 대상)**, §5(검증 책임 분리, 144행), **§5.1(경계 통일 +
  이관 기준 5번(174행) + deferred 불릿(178행) — 둘 다 개정 대상)**, §"향후 ADR 후보/해소 원장"(287행~).
  *주의*: `ProblemDetail`/`errors[]` 정의는 §3(Context Map)이 아니라 **§4** 다.
- `docs/plans/done/PLAN-0012-A-user-input-validation.md` / `PLAN-0012-B-post-input-validation.md` —
  형제 슬라이스가 세운 관행(한국어 메시지 exact 계약, 커스텀 제약 3종, 상수 공유)
- `src/main/java/com/dunowljj/board/adapter/in/web/exception/GlobalExceptionHandler.java` — **핵심 변경
  대상**. 특히 `validationError()`(168–174), `reason()`(195–), `handleMethodArgumentNotValid`(49–58),
  `handleHandlerMethodValidationException`(60–81), `fieldName()`(176–)
- `src/main/java/com/dunowljj/board/common/error/ErrorCode.java` / `ErrorCategory.java` — 최상위 code
  카탈로그(변경 안 함, 분리 근거 이해용)
- `src/main/java/com/dunowljj/board/adapter/in/web/error/ErrorCategoryHttpStatusMapper.java` — 같은
  패키지의 매퍼 선례(네이밍·`private` 생성자·`switch` 스타일)
- 제약 인벤토리 전체:
  - `adapter/in/web/dto/request/RegisterRequest.java`(`@NotBlank`×3, `@Size(min=8)`, `@MaxUtf8Bytes`,
    `@ValidEmail`, `@ValidNickname`)
  - `adapter/in/web/dto/request/CreatePostRequest.java` / `UpdatePostRequest.java`
    (`@NotBlank`, `@NotNull`, `@Size(max)`×2 each)
  - `adapter/in/web/PostController.java:76-77`(`@Min(0)`, `@Min(1) @Max(100)`)
  - `adapter/in/web/validation/`(`ValidEmail`/`ValidNickname`/`MaxUtf8Bytes` + validators)
- 기존 테스트:
  - **단언 추가 대상** — `e2e/AuthE2EIT.java`(175–231), `e2e/PostE2EIT.java`(141–186)
  - **변경하지 않되 읽을 것** — `adapter/in/web/PostControllerTest.java`(132, 147, 242, 374, 386).
    query 제약이 슬라이스에서 이미 `VALIDATION_FAILED` + `field` 로 검증되고 있음을 확인용
    (E2E 에 추가할 케이스와 중복 단언을 만들지 않기 위해)
- `src/test/java/com/dunowljj/board/architecture/TestStrategyArchitectureTest.java` — forward-defense
  테스트 관행(exhaustiveness 가드가 따를 스타일)
- `CLAUDE.md`, `.claude/skills/api-standards.md`(Error Format), `.claude/skills/clean-architecture.md`
  (web 예외 처리 위치), `.claude/skills/plan-lifecycle.md`

## Files to Touch (예상 — Implementation 단계에서 조정)

**신규**

- `adapter/in/web/error/ValidationErrorCode.java` — 코드 enum(6개) + 제약 애너테이션 → 코드 파생.
  `ErrorCategoryHttpStatusMapper` 와 같은 패키지·스타일.
- `adapter/in/web/dto/response/ValidationError.java` — `record ValidationError(String field, String code,
  String reason)`.
- 테스트: `adapter/in/web/error/ValidationErrorCodeTest.java`(파생 규칙 **전수 매트릭스** — `@Size`
  min/max 판정, fallback 사다리 포함), `adapter/in/web/error/ValidationErrorCodeCoverageTest.java`
  (exhaustiveness 가드).

**수정**

- `adapter/in/web/exception/GlobalExceptionHandler.java` — `validationError()` 가 `code` 를 채우고
  `ValidationError` 를 반환. 두 핸들러 경로 모두. `logValidationFailed` 페이로드 형태 변화 확인(Risks 7).
- `docs/adr/0005-exception-error-response-policy.md` — **3개 지점**(§4 예제 / §5.1 이관 기준 5번 /
  §5.1 deferred + 원장 ③) + 새 하위 절 + 상단 Amended 배너. `## ADR Required` 참조.
- `e2e/AuthE2EIT.java` — User/RequestBody 경로 기존 `reason` 단언 옆에 `code` 단언 추가.
  `AuthE2EIT:175` 의 "code 미도입" 주석 갱신.
- `e2e/PostE2EIT.java` — Post/RequestBody 경로에 `code` 단언 추가 **+ query 경로 케이스 신규**
  (`page=-1`, `size=101` → `OUT_OF_RANGE`). 두 추출 경로를 **같은 파일에서 대조**하기 위함(AC "두 경로
  동일 어휘").
- DTO javadoc 3곳 — `RegisterRequest:16`, `CreatePostRequest:13`, `UpdatePostRequest`(해당 문구 존재 시).
  "`errors[].code` 미도입 동안 `reason` 이 표시 메시지" 서술이 거짓이 되므로 갱신.

**변경하지 않음**: **검증 애너테이션과 validator 로직**(제약 선언·`message`·validator 구현체 전부 불변) +
도메인. DTO 는 *설명 주석만* 갱신한다. `PostControllerTest` 도 변경하지 않는다 — 기존 `field`·
`VALIDATION_FAILED` 단언 유지, `code` 단언은 추가하지 않는다(같은 사실을 세 층에서 못 박지 않음).

## Implementation Hints

- **파생 지점은 한 곳.** `ValidationErrorCode.from(...)` 하나가 진실원이고 `GlobalExceptionHandler` 는
  호출만 한다. 두 핸들러 경로가 서로 다른 타입(`FieldError` vs `MessageSourceResolvable`)을 주므로,
  파생 함수의 입력을 *제약 애너테이션 타입 + 속성 + 거부값* 으로 **정규화**한 뒤 매핑하면 경로별 분기가
  파생 로직으로 새지 않는다.
- **제약 메타데이터 추출 — `unwrap` 이 주경로.** 경로별 API 는 Scope 3 표 참조. 둘 다
  `ConstraintViolation` 을 얻은 뒤 `getConstraintDescriptor().getAnnotation().annotationType()`(타입) 과
  `getConstraintDescriptor().getAttributes()`(min/max) 를 읽는다. `getCodes()` 는 **주경로가 아니다** —
  제약 이름만 주고 속성이 없어 `@Size` 방향을 못 가른다. fallback 사다리(Risks 2): unwrap → 이름 기반
  → `INVALID`. 착수 시 두 경로 스파이크로 확인하고 결과를 Execution Notes 에 남긴다.
- **`@Size` min/max 판정.** 제약 속성 `min`/`max` 와 거부값 길이를 비교 — 길이 `< min` 이면 `TOO_SHORT`,
  `> max` 면 `TOO_LONG`. 둘 다 아니면(이론상 불가) `INVALID`. 거부값이 `null` 이면 `@NotNull`/`@NotBlank`
  가 이미 별도 엔트리를 내므로 `@Size` 는 애초에 발화하지 않는다(null-pass 관용구).
- **exhaustiveness 가드 골격.** ArchUnit 이 아니라 리플렉션 스캔이 단순하다. 수집 범위는 **`dto/request`
  패키지의 모든 record 컴포넌트 + 모든 `@RestController` 의 메서드 파라미터**(특정 컨트롤러 하드코딩
  금지 — 컨트롤러가 4개고 앞으로 늘어난다). `@Constraint` 메타애너테이션이 붙은 애너테이션을 모아
  각각이 `ValidationErrorCode.from(...)` 에서 `INVALID` 가 아닌 값을 내는지 단언.
  (`TestStrategyArchitectureTest` 처럼 "지금 0건이어도 나중에 강제"되는 forward-defense.)
- **테스트 케이스 윤곽** — E2E 는 기존 케이스에 `jsonPath("$.errors[?(@.field == 'title')].code",
  hasItem("TOO_LONG"))` 형태로 **추가만** 한다(기존 `reason` 단언 삭제 금지 — 회귀 0 확인이 목적).
  `PostE2EIT` 에는 query 케이스(`GET /api/posts?page=-1`, `?size=101`)를 **신규 추가**해 두 추출 경로가
  한 파일에서 대조되게 한다. 단위 테스트가 파생 규칙의 본체를 커버하고, E2E 는 "두 경로에서 실제로
  직렬화되어 나온다"만 본다 — **매트릭스를 E2E 로 옮기지 않는다**.
- **ADR 먼저.** `## ADR Required` 가 yes 이므로 ADR 개정(3개 지점)을 **구현 전에** 커밋한다(-B 선례:
  ADR 커밋 → 구현 커밋). §4 예제·§5.1 이관 기준·deferred 를 한 커밋에 묶는다.

## Execution Notes

<!-- 실행 중 비자명한 결정만 시간순 append -->
