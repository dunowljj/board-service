# PLAN-0012: User 입력 검증 경계 통일

ADR-0005 (예외/에러 응답 정책) §5(검증 책임 분리) 정합·강화. email/nickname 형식·문자·길이,
password byte 길이 검증을 **경계(Bean Validation)로 끌어올려**, User 입력 실패가 도메인 VO
(`INVALID_USER_CONTENT`, 400) 로 흘러내리지 않고 경계(`VALIDATION_FAILED`, 400) 에서 일관
처리되게 한다. PLAN-0011 리뷰 + Codex 논의 Known-Limitation(ADR-0005 "향후 ADR 후보")의 일부 해소.

> **범위 결정 (2026-06-09)**: 안정적 `errors[].code` 의 *전역* 도입(모든 제약 + Post/query 갱신)은
> `GlobalExceptionHandler` 전역 변경이라 본 Plan 에서 분리, **후속 Plan**으로 미룬다. 본 Plan 은
> `errors[]` 셰이프(`{field, reason}`)를 *바꾸지 않고* User 입력 검증의 *경계 통일*에만 집중한다.

<!-- 상층: 승인 게이트 -->
## Goal

1. email 형식 / nickname 허용 문자·길이 / password byte 길이의 **형식·길이 검증 실패**가
   `VALIDATION_FAILED` 로 수렴하도록 경계 커스텀 제약으로 끌어올림 (VO 까지 흘러
   `INVALID_USER_CONTENT` 가 *되지 않음*). *uniqueness 중복은 본 Plan 대상 아님* — `DUPLICATE_EMAIL`/
   `DUPLICATE_NICKNAME` (409) 그대로 유지.
2. 경계 validator 가 도메인 VO *정책 메서드*(`Email.isValid` / `Nickname.isValidDisplay`)를 **단일 출처로 공유** — 정규식/길이뿐 아니라 trim/normalize 순서까지 단일화해 divergence 제거.
3. **내부 불변식 분리** — `PasswordHash` blank 등 *사용자 입력이 아닌* 불변식 위반은 400 이 아니라
   5xx (서버 버그).
4. `errors[]` 셰이프(`{field, reason}`) **불변**, `GlobalExceptionHandler` **미변경** → Post DTO /
   page·size query 검증 **무영향**.

## Scope

- **커스텀 경계 제약**:
  - `@ValidEmail` / `@ValidNickname` — ConstraintValidator 가 도메인 VO *정책 메서드*
    (`Email.isValid` / `Nickname.isValidDisplay`)를 재사용. trim 후 길이 + 형식/문자를 *단일* 검증.
  - `@MaxUtf8Bytes(72)` — password byte 길이(BCrypt truncation 한계) 경계 검증.
- **RegisterRequest 애너테이션 정리** (중복/불일치 제거):
  - email: `@Size` 제거 → `@NotBlank` + `@ValidEmail`.
  - nickname: `@Size` 제거 → `@NotBlank` + `@ValidNickname`.
  - password: `@NotBlank` + `@Size(min = 8)`(char, trim 안 함) + `@MaxUtf8Bytes(72)`.
- **도메인 VO 정책 메서드 노출**: `Email`/`Nickname` 이 *정책 메서드*(`isValid`/`isValidDisplay`,
  필요 시 `normalize`)를 노출하고 VO 생성자도 그 메서드를 재사용(정규화+검증 단일 출처, 상수 단독
  노출 지양). validator 가 이 메서드를 호출. VO 자체 검증은 비-웹 경로 백스톱으로 유지
  (§5 "DTO 가 도메인 검증 대체 안 함").
- **내부 불변식 분리**: `PasswordHash` 는 *해시 출력 / DB 값만* 받고 평문(사용자 입력)을 접촉하지
  않는다. blank 가드 위반은 클라이언트가 고칠 수 없는 서버/데이터 조건이므로
  `BusinessException`(`INVALID_USER_CONTENT`, 400) 이 아니라 **plain RuntimeException** 으로 던져
  web adapter 5xx fallback(`INTERNAL_ERROR`) 으로 흐른다. VO 가 HTTP 상태를 *판단*하지 않고 "4xx 인
  척을 안 함" → 5xx 기본값. (caller·상류 가드 가정에 비의존)
- **password byte 검사 — 이중 방어**: `@MaxUtf8Bytes(72)` 는 *웹 응답을 `VALIDATION_FAILED` 로
  통일하는 1차 방어*. `UserCommandService.validatePassword` 의 byte 검사는 **필수 백스톱으로 유지**
  (`RegisterUserUseCase` 는 application port 라 비-웹 호출 가능 + BCrypt 72 byte truncation 은 *보안*
  문제 → service 최종 방어 필수). 경계가 service 검사를 *대체하지 않는다*.

## Non-goals

- **`errors[].code` 전역 마이그레이션** (모든 제약 code 부여 + `GlobalExceptionHandler` 변경 + Post/
  query DTO·테스트 갱신) — **후속 Plan** (백로그 기록).
- Post (`InvalidPostContentException`) 의 계약 통일 — 후속.
- login 흐름 — 이미 `AuthenticationFailedException` → 401 통일(PLAN-0011). 변경 없음.
- password 복잡도 / breached password / email verification — 별도 백로그.

## Related ADRs

- **ADR-0005** §5(검증 책임 분리) — *정합 강화* 대상(경계가 형식·길이 검증 책임, VO 는 백스톱). §1
  (4xx/5xx 분리) — passwordHash 내부 불변식 = 5xx 정합.
- ADR-0011 (User/인증) — §4b 에러 카탈로그.

## Acceptance Criteria

- invalid email 형식 / nickname 허용 외 문자 / password > 72 byte 로 register → **`VALIDATION_FAILED`
  (400)** + 기존 `errors[]`(`{field, reason}`) 셰이프. (VO 까지 흘러 `INVALID_USER_CONTENT` 가
  *되지 않음*)
- **`errors[].reason` 사용자 표시용 고정**: `errors[].code` 미도입 동안 프론트의 필드별 표시 메시지는
  `reason` 이 유일하므로, `@ValidEmail`/`@ValidNickname`/`@MaxUtf8Bytes` 의 기본 `message` 는 *사용자에게
  바로 보여줄 한국어 문장*으로 정의하고, E2E 가 해당 `errors[].reason` 을 검증한다.
- email/nickname 길이를 `@Size` 와 커스텀이 **이중으로 안 잡음** (중복 `errors` 없음) — `@Size` 제거.
- 규칙(email 정규식, nickname 문자·길이, email 길이)이 경계 validator 와 도메인 VO 에서 **동일
  정의 공유** — 한쪽만 바꿔도 다른 쪽 따라가는지 테스트로 고정.
- `PasswordHash` blank → 5xx (`INTERNAL_ERROR`), 400 아님.
- email/nickname **중복**(uniqueness)은 변함없이 `DUPLICATE_EMAIL`/`DUPLICATE_NICKNAME` (409) — 본
  Plan 의 통일 대상 아님(형식·길이 검증만 대상).
- 비-웹 호출(`RegisterUserUseCase` 직접)에서도 password > 72 byte 는 `validatePassword` 백스톱이 차단.
- `errors[]` 셰이프 **불변** + Post DTO / query 검증 응답 **무변화**(회귀 0).
- `./gradlew check` BUILD SUCCESSFUL.

## ADR Required

**yes (light)** — ADR-0005 §5 제자리 amend: ① User 입력 형식·길이 검증의 *경계 책임*을 명문화
(커스텀 validator 가 VO 규칙 공유, VO 는 백스톱), ② `PasswordHash` blank = 내부 불변식 → 5xx(§1
정합), ③ 안정적 `errors[].code` 통일은 후속 Plan 으로 deferred 명시. *대체로 §5 정합 강화*라
supersede 아닌 amend. **구현 전 ADR 개정 먼저.**

## Risks

1. **도메인 경계 오염** — VO 가 code/message(표현 관심사)를 알면 안 됨. *완화*: 도메인은 *규칙(정규식/
   길이 predicate)* 만 소유, message 는 web(애너테이션). `domain_pure` 로 검증. (web→domain 의존은
   ArchUnit 상 허용)
2. **trim 관대성 정합** — 커스텀 validator 가 VO 와 동일 trim 후 검증. password 는 *trim 안 함*(공백도
   유효 문자).
3. **`@Size` 제거 후 빈 입력 경로** — null/blank 는 `@NotBlank` 가 처리. 커스텀 validator 는 `@Pattern`
   처럼 null/blank 를 valid 취급(다른 제약이 잡음)하도록 구현해 중복 메시지 회피.
4. **password min(char) vs max(byte) 혼재** — `@Size(min=8)`(char) + `@MaxUtf8Bytes(72)`(byte) 분리
   명확화. 비-웹 호출 BCrypt truncation 방어 = `validatePassword` byte check **필수 유지**(선택 아님).

<!-- 하층: 실행 재량 -->
## Required Reading

- `docs/adr/0005-exception-error-response-policy.md` — §1(4xx/5xx 분리), §5(검증 책임 분리),
  §"향후 ADR 후보"(Known-Limitation)
- `docs/adr/0011-user-aggregate-and-session-authentication.md` — §4b
- `src/main/java/com/dunowljj/board/adapter/in/web/exception/GlobalExceptionHandler.java`
  (`handleMethodArgumentNotValid` / `validationError` — *변경 안 함*, errors[] 경로 이해용)
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/request/RegisterRequest.java`
- `src/main/java/com/dunowljj/board/domain/user/Email.java` / `Nickname.java` / `PasswordHash.java`
- `src/main/java/com/dunowljj/board/application/service/UserCommandService.java` (`validatePassword`)
- `src/main/java/com/dunowljj/board/common/error/InvalidUserContentException.java` / `BusinessException.java`
- `CLAUDE.md`, `.claude/skills/api-standards.md`, `.claude/skills/clean-architecture.md`,
  `.claude/skills/plan-lifecycle.md`

## Files to Touch (예상 — Implementation 단계에서 조정)

신규:
- `adapter/in/web/validation/ValidEmail.java` + `ValidEmailValidator.java`
- `adapter/in/web/validation/ValidNickname.java` + `ValidNicknameValidator.java`
- `adapter/in/web/validation/MaxUtf8Bytes.java` + `MaxUtf8BytesValidator.java`

수정:
- `domain/user/Email.java` / `Nickname.java` — *정책 메서드*(`isValid`/`normalize` 등) 노출,
  VO 생성자도 그 메서드 재사용(정규화+검증 단일 출처). (상수 단독 노출 지양 — divergence 위험)
- `adapter/in/web/dto/request/RegisterRequest.java` — email/nickname `@Size` 제거 + `@ValidEmail`/
  `@ValidNickname`, password `@MaxUtf8Bytes(72)` 추가
- `application/service/UserCommandService.java` — `validatePassword` byte/min 검사 **유지(필수 백스톱)**.
  경계 `@MaxUtf8Bytes`/`@Size(min)` 는 추가 1차 방어이지 대체 아님. (사실상 변경 없음, 의도 주석 보강만)
- `domain/user/PasswordHash.java` — blank → 내부 RuntimeException(5xx)
- `docs/adr/0005-exception-error-response-policy.md` — §5 light amend + Amended 노트

## Implementation Hints

- **규칙 단일 출처 — 상수보다 정책 메서드**: 정규식/길이 *상수만* 공유하면 trim/lowercase/NFC/NFKC
  *적용 순서*가 validator 와 VO 에서 재divergence 할 수 있다. 가능하면 도메인 *정책 메서드*를 공유 —
  예: `Email.isValid(String raw)` / `Email.normalize(...)`, `Nickname.isValidDisplay(...)` /
  `Nickname.normalizedDisplay(...)`. VO 생성자도 같은 메서드를 사용해 *정규화+검증 로직 전체*가
  단일 출처가 되게 한다. validator 는 이 boolean 정책 메서드를 호출, `message` 는 애너테이션(web).
  도메인은 code/message 모름 → `domain_pure` 유지.
- **null/blank 처리**: 커스텀 validator 는 `value == null || value.isBlank()` 면 `true`(valid) 반환
  (`@NotBlank` 가 빈 값 책임). 비어있지 않을 때만 trim 후 길이+형식/문자 검사.
- **`@MaxUtf8Bytes`**: `value.getBytes(UTF_8).length <= max`. password 에 적용(trim 안 함).
- **passwordHash blank**: `PasswordHash` 는 *해시 산출물 / DB 복원값만* 받으므로(평문 미접촉) blank 는
  BCrypt(non-blank 반환) 가정 하에 hasher 오설정 / DB 데이터 손상 = 서버·데이터 조건. (DB `NOT NULL`
  은 null 만 막고 `""` 는 못 막음 — 컬럼 차원 차단은 `CHECK` 별도 의제.) BusinessException 아님 → plain
  RuntimeException(예: `IllegalStateException`) → `GlobalExceptionHandler` catch-all 5xx
  (`INTERNAL_ERROR`). 정상 입력으론 도달 불가한 방어 가드 — *상류 password 정책
  (@NotBlank/validatePassword)에 의존하지 않고* 성립(구조적 분류).
- **테스트 골격**: (a) ValidEmail/ValidNickname/MaxUtf8Bytes unit (valid/invalid + null/blank pass +
  VO 규칙 일치), (b) RegisterRequest slice/E2E — invalid email 형식 / nickname 문자 / password
  byte → `VALIDATION_FAILED` + 해당 field, 중복 errors 없음, (c) PasswordHash blank → 5xx, (d)
  기존 Post/query 검증 응답 무변화 회귀 확인.

## Execution Notes

<!-- 실행 중 비자명한 결정만 시간순 append -->

- 2026-06-13: 구현 완료. (1) `Email.isValid`/`Nickname.isValidDisplay` static 정책 메서드 추출 — VO 생성자도 재사용해 규칙 단일 출처. `normalize` 는 isValid 통과 후 호출되는 private(non-null 전제)로 단순화(이전 nullable 반환안 폐기), `Locale.ROOT` 유지(locale 의존 lowercase=터키 I 회피). (2) `PasswordHash` blank → `IllegalStateException`(plain, BusinessException 아님 → 5xx). (3) `@ValidEmail`/`@ValidNickname`/`@MaxUtf8Bytes` 신설 — null/blank 는 통과시켜 `@NotBlank` 가 존재 검사 담당(중복 errors 회피, 표준 idiom). RegisterRequest email/nickname `@Size` 제거. (4) `UserCommandService.validatePassword` byte/min 검사 **유지**(필수 백스톱, 비-웹 BCrypt truncation 방어) — 경계는 1차 방어이지 대체 아님(변경 없음). (5) `errors[]` 셰이프·`GlobalExceptionHandler` 불변. `./gradlew check` 그린 — AuthE2EIT 12(email 형식/nickname 문자/password 75byte → VALIDATION_FAILED + field-level errors 검증).
