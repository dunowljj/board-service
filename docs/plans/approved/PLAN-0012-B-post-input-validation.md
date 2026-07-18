# PLAN-0012-B: Post 입력 검증 경계 통일 (단일 출처 + VO 백스톱)

> **패밀리 노트**: ADR-0005 §5(검증 책임 분리) 통일 결정의 슬라이스 B(Post). 형제: **-A** User 경계
> 통일(완료·머지·archived), **-C** `errors[].code` 전역 도입(deferred). 슬라이스별 별도 Plan·PR·archival.

ADR-0005 §5·§5.1 에서 이미 채택한 검증 표준(경계=형식·길이 1차 방어 / VO=백스톱, 규칙 단일 출처)을
**레거시 Post 에 적용(conformance)**한다. Post 는 PLAN-0004(CRUD) 때 이 패턴보다 *먼저* 만들어져
경계 `@Size` + VO blank/null 만 검사하는 옛 방식으로 남아 있다.

<!-- 상층: 승인 게이트 -->

## Goal

1. **버그 수정 아님 — 잠재 갭 정리.** Post 는 경계(`@NotBlank`/`@NotNull`/`@Size`)가 이미 web 입력을
   `VALIDATION_FAILED` 로 수렴시키므로 *현재 응답 계약 누출은 없다* — *DTO 제약 위반 입력(title blank,
   body null, title/body 과길이)은 경계가 먼저 400 으로 거부해 `PostContent`(VO)까지 도달하지 않는다*.
   (body `""` 는 위반 아님 → 정상적으로 VO 도달·통과.) 본 Plan 은
   **이미 정한 표준에 대한 미준수 갭**(아래)을 닫는 conformance 작업이다.
2. **규칙 단일 출처.** 제목/본문 길이 한도(200 / 10000)가 현재 **경계 `@Size` 리터럴에만** 존재하고
   VO 는 길이를 모른다 → 경계·VO divergence 위험. 길이 한도를 도메인 `PostContent` 상수로 **단일화**하고
   경계가 그 상수를 참조.
3. **VO 백스톱.** `PostContent` 가 길이 규칙을 *스스로* 강제해, 웹을 거치지 않는 호출(배치/직접
   `Post.create`/마이그레이션)에서도 과길이 값이 도메인에 들어오지 못하게 한다(§5 "DTO 가 도메인 검증
   대체 안 함"의 실현). **web 동작은 불변**(경계가 이미 과길이 거부 → VO 길이 검사는 비-웹 경로에서만 발화).
4. **errors[].reason 계약 정합.** -A round-5 에서 User 제약에 한국어 메시지를 명시(프레임워크 영문 기본
   누출 차단)한 것과 동일하게, Post 제약(`@NotBlank`/`@NotNull`/`@Size`)에도 한국어 메시지를 명시한다.

## Scope

- **`PostContent` 길이 정책 단일 출처화**:
  - `public static final int MAX_TITLE_LENGTH = 200;` / `MAX_BODY_LENGTH = 10000;` 노출.
  - 생성자에 길이 검사 추가(백스톱): title 이 `MAX_TITLE_LENGTH` 초과 → `InvalidPostContentException("title")`,
    body 가 `MAX_BODY_LENGTH` 초과 → `InvalidPostContentException("body")`. 기존 blank/null 검사 유지.
  - title `@NotBlank`↔blank, body `@NotNull`↔null 의 **비대칭(빈 본문 허용) 보존** — body `""` 는 유효
    (length 0 ≤ max).
- **경계 DTO 가 도메인 상수 참조**(`CreatePostRequest` / `UpdatePostRequest`):
  - `@Size(max = PostContent.MAX_TITLE_LENGTH)` / `@Size(max = PostContent.MAX_BODY_LENGTH)` — 리터럴
    제거, 상수 참조(단일 출처). (`@Size` 의 `max` 는 컴파일 상수 필요 → `static final int` 충족.)
  - 모든 제약에 한국어 메시지 명시(`@NotBlank`/`@NotNull`/`@Size`).
- **커스텀 validator 신설 안 함(의도적).** -A 는 규칙이 복잡(정규식·허용문자·정규화 순서)해 정책 메서드
  + 커스텀 제약이 divergence 방어에 필요했다. Post 규칙은 *plain 길이 int* 뿐이라 `@Size` 가 이미
  선언적으로 표현한다 → `@ValidPostTitle` 류 커스텀 제약은 이득 없는 의식(YAGNI). 단일 출처의 단위도
  Post 에선 *메서드가 아니라 상수*가 정직하다(공유할 정규화/형식 로직이 없음).

> **설계 결정 (2026-07-03, 확정)**: **옵션 1 채택** — `@Size(max = 상수)` + VO 백스톱 + *등가성
> 테스트*. 대안(옵션 2: 커스텀 `@ValidPostTitle` → `PostContent.isValidTitle()` 로 *로직*까지 공유)은
> `s.length() <= N` 을 위해 애너테이션+validator 4파일을 만드는 오버엔지니어링이라 기각.
> **인정하는 트레이드오프**: 옵션 1 은 *상수(숫자)만* 공유하고 길이 비교 *로직*은 `@Size`(Hibernate)와
> VO 가 독립 구현한다 → 측정 방식(Java char `.length()`)·경계 연산자(≤max)·trim(안 함)이 재divergence
> 할 수 있는 좁은 표면이 남는다. 이 표면은 **등가성 테스트**(경계 `@Size` 판정과 VO 판정이 *같은 길이
> 임계에서* 함께 통과/거부하는지 고정)로 닫는다 — -A 의 `agrees_with_policy`(validator==정책메서드)
> 발상과 동일. 참고: -A 도 단순 규칙(`password` `@Size(min=8)`)은 메서드 공유 없이 경계 독립 제약으로
> 뒀다 → 옵션 1 은 -A 자신의 단순-규칙 관행과 일치.

## Non-goals

- **`errors[].code` 전역 마이그레이션** — 슬라이스 -C, deferred.
- Post *uniqueness/권한/존재*(`POST_NOT_FOUND`, ownership 403 등) — 형식·길이 검증만 대상.
- 제목/본문 *내용* 규칙(금칙어·HTML sanitize·마크다운) — 별도 백로그.
- 페이지네이션 query(`page`/`size`) 검증 — 본 Plan 무관.

## Related ADRs

- **ADR-0005** §5(검증 책임 분리 — 경계=형식·길이, VO=백스톱), §5.1(단일 출처·경계 통일 기법) — Post
  에 *적용*. §"향후 ADR 후보/해소 원장"에 deferred 로 적힌 Post 마이그레이션을 resolved 로 갱신.
- ADR-0004(§3 ADR↔Plan 번호 -A/-B/-C 규약) — 본 Plan 이 -B 임의 근거.

## Acceptance Criteria

- 제목 200자 초과 / 본문 10000자 초과로 create·update → **`VALIDATION_FAILED` (400)** + 기존
  `errors[]`(`{field, reason}`) 셰이프, 해당 field(title/body). (web 동작 불변 — 회귀 0)
- Post 제약 위반 시 `errors[].reason` 이 *한국어* 메시지(프레임워크 영문 기본 누출 없음) — E2E 가 exact 검증.
- 길이 한도가 경계와 VO 에서 **동일 상수 공유** — `PostContent.MAX_*` 한 곳만 바꿔도 양쪽 반영됨을
  테스트로 고정(경계 `@Size` 가 상수 참조, VO 가 같은 상수로 검사).
- **등가성 테스트**: 경계(`@Size`) 판정과 VO 판정이 *같은 임계*에서 함께 통과/거부함을
  `jakarta.validation.Validator`(DTO) + 생성자(VO)로 고정하되, 주장한 세 divergence 벡터를 실제로 자극 —
  ① off-by-one(`MAX` 통과 / `MAX+1` 거부) ② trim(앞뒤 공백 포함 `MAX+2`) ③ codePoint(emoji surrogate
  pair). 한쪽 의미(연산자·측정·trim)가 바뀌면 깨지도록.
- **비-웹 백스톱**: `new PostContent(title, 과길이 body)` / `Post.create(...)` 직접 호출이
  `InvalidPostContentException` 으로 차단(웹 경계 없이도).
- title `@NotBlank`(blank 거부) / body `@NotNull`(빈 본문 `""` 허용) 비대칭 **보존** — 회귀 없음.
- `InvalidPostContentException` 의 web 도달 여부 불변(정상 경로 도달 불가) — 응답 계약 무변화.
- `./gradlew check` BUILD SUCCESSFUL (기존 Post 슬라이스/E2E 회귀 0).

## ADR Required

**yes (light)** — ADR-0005 §5.1 제자리 amend: Post 경계 통일이 -A 와 동일 표준(경계=1차, VO=백스톱,
길이 단일 출처)을 따름을 명문화 + "향후 ADR 후보/해소 원장"의 *Post 마이그레이션 deferred* 항목을
resolved 로 갱신. **새 결정 아님(기존 §5 표준의 Post 적용) → supersede 아닌 amend/원장 갱신.**
구현 전 ADR 개정 먼저.

## Risks

1. **web→domain 의존(DTO 가 `PostContent.MAX_*` 참조)** — adapter→domain 은 헥사고날/ArchUnit 상 허용
   (-A 의 validator→`Email.isValid` 선례 동일). *완화*: 상수만 참조, 도메인은 code/message 모름
   (`domain_pure` 유지).
2. **VO 길이 검사 추가로 기존 테스트 회귀** — 짧은 fixture(`"t"`, `"b"`)는 영향 없음. 과길이 신규
   케이스만 추가. web 은 경계가 먼저 잡으므로 응답 불변.
3. **`@Size(max = 상수)` 컴파일 제약** — `max` 는 상수식 필요 → `public static final int` 로 노출해야
   함(계산식/메서드 불가). 백스톱 VO 검사는 같은 상수 재사용.
4. **VO 가 web errors 를 이중 생성하지 않음** — web 은 경계만 발화하고 VO 는 미도달(경계 통과분만 VO
   도달)이므로, VO 길이 검사가 `errors[]` 에 항목을 *추가*하지 않는다. *단, 경계 제약끼리는 겹칠 수
   있다*: `title = " ".repeat(201)` 은 `@NotBlank`(blank)와 `@Size`(길이>max)가 **동시 위반**이라 title
   에 errors 2건이 정상이다(비-blank 과길이만 1건). 이는 -B 가 만드는 게 아니라 기존 `@NotBlank`+`@Size`
   조합의 선재 동작(-A `password` `@NotBlank`+`@Size(min=8)` 도 동일) — 본 Plan 대상 아님. "errors 1건"을
   보장하지 않으며, `@GroupSequence` 단락은 -A 결정대로 aggregation 손실 때문에 도입하지 않는다.

<!-- 하층: 실행 재량 -->
## Required Reading

- `docs/adr/0005-exception-error-response-policy.md` — §5(검증 책임 분리), §5.1(경계 통일·단일 출처),
  §"향후 ADR 후보/해소 원장"
- `docs/plans/done/PLAN-0012-A-user-input-validation.md` — 미러링할 -A 패턴(정책 공유·백스톱·한국어
  메시지·E2E exact reason). *단, Post 는 커스텀 validator 없이 상수 공유*라는 차이 유의.
- `src/main/java/com/dunowljj/board/domain/post/PostContent.java` / `Post.java`(생성 지점 3곳:
  create/reconstitute/updateContent)
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/request/CreatePostRequest.java` / `UpdatePostRequest.java`
- `src/main/java/com/dunowljj/board/common/error/InvalidPostContentException.java`(`INVALID_POST_CONTENT`,
  `INVALID_INPUT`→400)
- `src/main/java/com/dunowljj/board/adapter/in/web/exception/GlobalExceptionHandler.java`
  (`handleMethodArgumentNotValid` — *변경 안 함*, errors[] 경로 이해용)
- 기존 테스트: `domain/post/PostContentTest.java` / `PostTest.java`,
  `adapter/in/web/PostControllerTest.java`, `e2e/PostE2EIT.java`
- `CLAUDE.md`, `.claude/skills/api-standards.md`, `.claude/skills/clean-architecture.md`,
  `.claude/skills/plan-lifecycle.md`

## Files to Touch (예상 — Implementation 단계에서 조정)

신규: **없음**(커스텀 validator 미신설 — -A 와의 의도적 차이).

수정:
- `domain/post/PostContent.java` — `MAX_TITLE_LENGTH`/`MAX_BODY_LENGTH` 상수 노출 + 생성자 길이 검사
  (백스톱). blank/null/비대칭 기존 로직 유지.
- `adapter/in/web/dto/request/CreatePostRequest.java` / `UpdatePostRequest.java` — `@Size(max=상수)`
  참조 + 한국어 메시지 명시.
- `docs/adr/0005-exception-error-response-policy.md` — §5.1 light amend + 해소 원장 갱신 + Amended 노트.

테스트:
- `domain/post/PostContentTest.java` — 과길이 title/body → `InvalidPostContentException`, 경계값
  (`MAX` 통과, `MAX+1` 거부), body `""` 허용 유지.
- `domain/post/PostTest.java` — AC 가 명시한 비-웹 경로를 *직접* 커버: `Post.create(...)` /
  `updateContent(...)`(→ `reconstitute` 도 동일 경로) 에 과길이 title/body → `InvalidPostContentException`.
  (PostContentTest 는 VO 단위, PostTest 는 팩토리 경로가 백스톱을 실제로 태우는지 확인 — AC 문구와 정합.)
- **신규 등가성 테스트**(예: `adapter/in/web/dto/request/PostLengthContractTest.java`) — 경계 `@Size`
  (`jakarta.validation.Validator`)와 VO 가 같은 길이 임계에서 함께 통과/거부(옵션 1 로직-중복 방어).
- `e2e/PostE2EIT.java` — create/update 에서 **모든 Post 제약**의 `errors[].reason` 한국어 *exact* 검증
  (프레임워크 영문 기본 누출 계약을 완전히 닫음, -A `AuthE2EIT` round-5 미러). 최소 케이스:
  ① title `""` → `@NotBlank` 한국어 exact, ② body `null` → `@NotNull` 한국어 exact, ③ title 과길이 →
  `@Size` 한국어 exact, ④ body 과길이 → `@Size` 한국어 exact. 모두 `VALIDATION_FAILED`(400).
  *E2E 로 통일*(Acceptance 의 "E2E exact" 와 정합, PostControllerTest slice 아님).

## Implementation Hints

- **단일 출처 = 상수(메서드 아님).** -A Hints 는 "상수보다 정책 메서드"였는데, 그건 email/nickname 의
  *정규화 순서* divergence 때문. Post 는 정규화/형식 로직이 없고 규칙이 길이 int 뿐 → `PostContent`
  의 `public static final int MAX_TITLE_LENGTH/MAX_BODY_LENGTH` 가 정직한 단일 출처. 경계 `@Size(max=…)`
  와 VO 생성자가 *같은 상수*를 참조. 예시:
  ```java
  // domain/post/PostContent.java — 규칙 소유(단일 출처) + 백스톱
  public static final int MAX_TITLE_LENGTH = 200;
  public static final int MAX_BODY_LENGTH  = 10_000;
  public PostContent(String title, String body) {
      if (title == null || title.isBlank())      throw new InvalidPostContentException("title");
      if (title.length() > MAX_TITLE_LENGTH)      throw new InvalidPostContentException("title"); // 백스톱
      if (body == null)                           throw new InvalidPostContentException("body");
      if (body.length() > MAX_BODY_LENGTH)        throw new InvalidPostContentException("body");  // 백스톱
      this.title = title; this.body = body;
  }
  // adapter/in/web/dto/request/CreatePostRequest.java — 경계는 상수를 참조(리터럴 금지)
  @NotBlank(message = "제목을 입력해주세요")
  @Size(max = PostContent.MAX_TITLE_LENGTH,
        message = "제목은 " + PostContent.MAX_TITLE_LENGTH + "자 이하여야 합니다")  // 문구 숫자도 상수 파생
  String title
  ```
  `@Size(max = 상수)` 는 `static final int` 가 컴파일 상수라 허용. **메시지의 숫자도 상수 concat**
  (`"제목은 " + MAX_TITLE_LENGTH + "자…"`)으로 하면 문자열도 컴파일 상수식이라 애너테이션에서 유효 →
  "200" 하드코딩 없이 문구까지 단일 출처(상수 한 곳만 바꾸면 검증·메시지 동시 반영).
- **등가성 테스트(옵션 1 의 로직-중복 방어).** `@Size` 와 VO 는 길이 비교를 *독립 구현*(상수만 공유)
  하므로, 둘이 *같은 임계에서* 갈리는지 고정한다. `jakarta.validation.Validator` 로 DTO 를, 생성자로
  VO 를 검증하되, **주장한 세 divergence 벡터(경계값·trim·codePoint)를 실제로 자극하는 케이스**를 넣어야
  한다(단순 `"a".repeat(MAX)` 만으론 trim/codePoint 변경을 못 잡음):
  **title(`MAX_TITLE_LENGTH`)·body(`MAX_BODY_LENGTH`) 각각**, **Create·Update 두 DTO 각각**에 대해
  검증한다 — body 는 별도 상수·별도 애너테이션이고 두 DTO 가 독립 선언이라, 어느 한 필드가 *잘못된 상수*
  를 참조하면(예: body `@Size` 가 `MAX_TITLE_LENGTH` 참조, 또는 Update 만 오타) title-only 테스트는 못
  잡는다. 세 divergence 벡터(경계값·trim·codePoint)를 *해당 필드의 MAX 기준으로* 자극:
  ```java
  // helper: 경계(@Size via Validator)와 VO(생성자)가 expectValid 로 함께 통과/거부하는지 단언.
  //   dtoFactory: (title,body)->CreatePostRequest / ->UpdatePostRequest (양쪽 각각 호출)
  //   field 별로 MAX 를 바꿔가며(title=MAX_TITLE_LENGTH, body=MAX_BODY_LENGTH) 호출
  void agree(int MAX, ... field, ... dtoFactory) {
      s -> {
        // (1) off-by-one: MAX 통과 / MAX+1 거부
        assertBoundaryAndVoAgree("a".repeat(MAX),     true,  field, dtoFactory);
        assertBoundaryAndVoAgree("a".repeat(MAX + 1), false, field, dtoFactory);
        // (2) trim: 앞뒤 공백 포함 MAX+2 (raw 거부). VO 가 trim 하면 MAX 통과 → 불일치
        assertBoundaryAndVoAgree(" " + "a".repeat(MAX) + " ", false, field, dtoFactory);
        // (3) codePoint: emoji(char 2 / codePoint 1) MAX 개 = char 2*MAX>MAX(거부), codePoint MAX(≤MAX).
        //     char 기준(정답) 거부지만 codePointCount 면 통과 → 불일치. (MAX+1 개면 양측 거부→구분 못 함)
        assertBoundaryAndVoAgree("😀".repeat(MAX), false, field, dtoFactory);
      };
  }
  // title×{Create,Update}, body×{Create,Update} 총 4조합 실행
  ```
  `assertBoundaryAndVoAgree` 는 `validator.validate(dto)` 의 해당 field 위반 유무와 `new PostContent(...)`
  throw 유무가 *둘 다* expectValid 와 일치함을 확인(다른 필드는 유효값 고정). 누가 VO 를
  `>=`·`codePointCount`·`trim` 으로 바꾸거나, 한 DTO 필드가 잘못된 상수를 참조하면 깨진다.
  (의도 semantics 확정: **Java `char` 수(`.length()`) 기준, trim 안 함** — `@Size` 와 동일.)
- **VO 백스톱 위치.** 길이 검사는 `PostContent` 생성자(blank/null 검사 바로 뒤). `Post.create` /
  `reconstitute` / `updateContent` 모두 `new PostContent` 를 거치므로 세 경로 자동 커버.
- **web 응답 불변 보장.** 경계 `@Size` 가 과길이를 먼저 `VALIDATION_FAILED` 로 잡으므로 VO 길이 검사는
  web 응답에 영향 없음(비-웹 경로 방어만). 회귀 테스트로 기존 Post E2E 그린 유지 확인.
- **한국어 메시지.** `@NotBlank`/`@NotNull` 은 고정 문구(`"제목을 입력해주세요"` 등), `@Size` 의 숫자는
  위 예시처럼 **상수 concat** 으로 파생(리터럴 "200" 금지 — 상수와 재divergence 회피). 문구는 구현 시 확정.
- **테스트 골격**: (a) `PostContentTest` 길이 백스톱(경계값 200/10000 pass, +1 reject; body "" pass),
  (b) 상수 공유 회귀 가드(경계와 VO 가 같은 `MAX_*` 를 봄 — 예: reflection 없이 상수 직접 단언 +
  DTO slice 로 max 경계 확인), (c) Post 과길이 E2E → `VALIDATION_FAILED` + 한국어 reason exact,
  (d) 기존 Post 정상 흐름/짧은 fixture 회귀 0.

## Execution Notes

<!-- 실행 중 비자명한 결정만 시간순 append -->

- 2026-07-19: 구현 완료. (1) ADR-0005 §5.1 "Post 적용" amend + 원장 Post 마이그레이션 resolved(상단 배너 2026-07-05 추가). (2) `PostContent` 에 `MAX_TITLE_LENGTH=200`/`MAX_BODY_LENGTH=10_000` 상수 + 생성자 길이 백스톱(blank/null 검사 뒤, `char` 수 기준·trim 안 함). 세 팩토리 경로(create/reconstitute/updateContent) 자동 커버. (3) `CreatePostRequest`/`UpdatePostRequest` — `@Size(max = PostContent.MAX_*)` 상수 참조 + 메시지 숫자도 상수 concat(`"제목은 " + MAX + "자…"` 컴파일 상수식) + 전 제약 한국어 메시지. 커스텀 validator 미신설(옵션 1). (4) 테스트: `PostContentTest` 길이 경계(MAX/MAX+1), `PostTest` 팩토리 경로 과길이(AC 명시 경로 직접), 신규 `PostLengthContractTest`(등가성 — title·body × Create·Update × 경계/trim/codePoint 벡터), `PostE2EIT` 4제약 exact 한국어 reason(create) + Update 과길이(DTO 검증이 404 조회 전 발화). (5) `errors[]` 셰이프·web 응답 계약 불변, `@NotBlank`+`@Size` blank-과길이 2건은 선재 동작(변경 없음). `./gradlew check` BUILD SUCCESSFUL(test+integrationTest+ArchUnit — DTO→domain 의존 허용 확인).
