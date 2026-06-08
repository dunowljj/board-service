# ADR-0011: User aggregate + Spring Security session 기반 인증

## Status
Proposed

**Amended 2026-06-07**: §4 로그인 구현 방식을 custom authentication filter 로 확정 — §4 가 열어둔 controller/filter 택일 중 filter 채택(초기 PLAN-0011 컨트롤러 구현에서 정정). 미shipped·in-flight 초기 결정이라 supersede ADR 대신 제자리 개정.

## Date
2026-05-24

## Context

본 게시판이 *익명 free-input author* 에서 *인증된 사용자* 모델로 전환한다. 직접적 동인은 두 자리:

- **Comment / Like 도입의 전제 자리** — 좋아요는 사용자 1 명 1 회만 허용 (사용자 결정 2026-05-24). `Like aggregate` 의 `(post_id, user_id) UNIQUE` 제약은 *user 식별* 이 전제. 댓글도 *작성자 식별 + 본인 글 수정/삭제* 시 user 가 필요.
- **현 `Post.author` 가 free-input String** — 임시 모델. 게시판이 본격화되면 *닉네임 충돌 / 표시 일관성 / 도용 위험* 의식이 누적.

다음 부담이 잠재한다.

- **anonymous 모델 유지 시** — Like / 본인 글 검증 / 사용자 페이지 등 *모든 의제* 가 *임시 fingerprint* 같은 *대체 식별* 필요. 결국 *user* 를 *재발명*.
- **user 도입 시 인증 구체 패턴 결정** — session / JWT / OAuth2 / 자체 토큰 등 *큰 의제*. 의제 분리 안 하면 *user aggregate 결정* 자체가 *인증 패턴* 에 묶임.
- **Spring Security 도입 시 *전역 의식*** — `SecurityFilterChain`, CSRF, cookie 정책 등 *모든 요청* 에 영향. 후행 비용 매우 큼 (ADR-0004 §1).

본 ADR 은 *User aggregate 자체 정의* + *Spring Security session-based 인증* + *권한 범위* 를 *함께* 결정한다. 사유: User 와 *그 인증 패턴* 이 밀접 묶임 — 분리 시 한쪽이 다른쪽의 *추상 결정* 만 되어 응집도 약화.

본 ADR 은 *결정* 만 박는다. 구체 artifact 좌표 / property key / SQL 스키마 / 구현 패턴은 PLAN-0011 영역.

## Decision

### 1. User aggregate 도입

새 도메인 aggregate `User` 를 도입한다.

- **aggregate root**: `User`
- **invariant**:
  - `email` *전역 unique* — 로그인 식별자. **trim + lower-case canonical** 저장 (RFC 5321 의 local-part case-sensitivity 가 실무에서 *case-insensitive 통일* 이 표준 + 앞뒤 공백 보존 가치 0). 입력 boundary 에서 `trim().toLowerCase(Locale.ROOT)` 정규화 후 저장 + unique constraint 도 정규화된 값 기준. 현 단계 email 입력은 **ASCII email format 전제** — IDN / punycode / SMTPUTF8 (`Unicode local-part`) 는 국제화 계정 의제 진입 시 별도 ADR
  - `nickname` *표시 식별자* + `nicknameCanonical` *전역 unique* (중복 검증용) — *별도 필드 분리, 둘 다 정규화 적용*:
    - **display (`nickname`)** = `Normalizer.normalize(input.trim(), NFC)` (trim → NFC). 표시용 — 사용자 의도 case 보존. NFC 결합 형태 통일로 *시각 동일 다른 코드포인트* 표시 일관
    - **canonical (`nicknameCanonical`)** = `Normalizer.normalize(input.trim(), NFKC).toLowerCase(Locale.ROOT)` (trim → NFKC → lower-case). 중복 검증용. NFKC 는 *호환 변형* 까지 통일 (예: 전각/반각, 합자)
    - 표시도 *raw* 가 아닌 *정규화* 적용 — 앞뒤 공백 / 조합 분리 형태가 화면에 남는 자리 차단
  - **DB unique constraint 는 `nicknameCanonical` 컬럼에만**, `nickname` 표시 컬럼에는 unique 없음 — 표시 컬럼에 잘못 unique 거는 자리 차단. 컬럼 타입은 **`varchar` (canonical 분리 접근과 정합)**. 구체 길이 제한 / charset 은 PLAN-0011 결정
  - `password` — *반드시 hash* 로 저장 (plain text 금지, §5)
- **aggregate 간 참조** — id only (ADR-0012 의 정책 영역). 본 ADR 은 *User aggregate 자체* 만 박음
- 도메인 모델은 *Post* 와 동일 패턴 — `domain.user.User` + `application.port.{in,out}` + `adapter.{in,out}` (ADR-0003 의 헥사고날)

필드 후보 (구체는 PLAN-0011):

- `id` (Long, IDENTITY)
- `email` (String, unique, NOT NULL) — trim + lower-case canonical 저장
- `nickname` (String, NOT NULL) — 표시용 (trim + NFC 정규화), *unique 없음*
- `nicknameCanonical` (String, **unique**, NOT NULL) — 중복 검증용 (trim + NFKC + lower-case)
- `passwordHash` (String, NOT NULL) — *plain password 는 도메인에 흐르지 않는다* 의식
- `createdAt` / `updatedAt` — `AuditingEntityListener` (ADR-0008)

### 2. 인증 패턴 — Spring Security **session-based** (JSESSIONID)

- **session-based authentication** — Spring Security 기본 흐름
- 로그인 성공 시 서버가 `SecurityContext` 에 `Authentication` 주입, `JSESSIONID` 쿠키 발급
- 로그아웃 시 session invalidate (`HttpSession.invalidate()`)
- session timeout: PLAN-0011 에서 명시 (권장 30 분, §7 의 *기본값 의존 금지* 정합). 갱신 정책 (rolling refresh) 은 Open Question

선택 사유 (사용자 결정 2026-05-24):

- *서버 세션의 단순성* — Spring Security 기본 지원이 가장 두꺼움. 현 단계가 *REST API* (Thymeleaf 부재) 지만 session 의 *서버 통제* 가치가 JWT 대비 큼 (token 폐기 / 즉시 logout / blacklist 의식 회피)
- JWT 의 *stateless* / *refresh token 의식* / *blacklist 의식* 회피
- *CSRF 의식 동반* — REST API + session 채택의 trade-off. §6 의 CookieCsrfTokenRepository + 헤더 패턴으로 보완
- SPA / mobile client 도입 시 *별도 ADR* 시점 — CSRF / cookie 정책 재검토

### 3. 권한 범위 — *조회 anonymous OK, 쓰기 로그인 필수*

| Endpoint pattern | 권한 |
|---|---|
| `GET /api/posts`, `GET /api/posts/{id}` | anonymous OK |
| `GET /api/posts/{id}/comments` | anonymous OK |
| `POST /api/posts`, `PUT /api/posts/{id}`, `DELETE /api/posts/{id}` | authenticated |
| `POST /api/posts/{id}/comments`, `PUT /...`, `DELETE /...` | authenticated |
| `POST /api/posts/{id}/likes`, `DELETE /api/posts/{id}/likes` | authenticated |
| `POST /api/auth/register`, `POST /api/auth/login` | anonymous OK (인증 진입 자리) |
| `POST /api/auth/logout` | authenticated |
| `GET /api/csrf` | anonymous OK (CSRF token 발급, §6) |
| `GET /api/users/me` | authenticated |

권한 검증 자리:

- `SecurityFilterChain` 의 `authorizeHttpRequests` 로 *전역 매핑* (1차 방어선)
- 도메인 권한 (예: *본인 글만 수정/삭제*) 은 *application service 책임* — `@PreAuthorize` 같은 메서드 어노테이션도 가능하나 *시스템 결정으로 박지 않음* (구현 차원 결정, PLAN-0011 영역)

### 4. 회원가입 / 로그인 / 로그아웃 flow

- **`POST /api/auth/register`** — `{email, nickname, password}` 입력. 검증:
  - email format
  - nickname 길이 / 허용 문자 정책 (Open Question — 구체 PLAN 결정)
  - password 정책 (§5)
  - email *전역 unique* / nicknameCanonical *전역 unique* (§1 정합) — DB constraint 위반 시 *409 Conflict + ProblemDetail* (`DUPLICATE_EMAIL` / `DUPLICATE_NICKNAME` 후보, §4b)
  - 성공 시 password BCrypt 해시 + User 저장 + *201 Created* 응답
- **`POST /api/auth/login`** — `{email, password}` (JSON) 입력. password 검증 → 성공 시 session 생성 + JSESSIONID 발급, 실패 시 *401 Unauthorized + ProblemDetail*. **custom authentication filter 방식 채택** — `AbstractAuthenticationProcessingFilter` 확장, `/api/auth/login` POST 매칭, JSON body 파싱. Spring Security `formLogin`(form-urlencoded 전제) 미사용.
  - **결정 근거 (2026-06-07, 초기 PLAN-0011 컨트롤러 방식에서 정정)**: 커스텀 컨트롤러 방식은 session fixation 재발급(`changeSessionId`)·`SecurityContextRepository.saveContext` 를 *수동* 호출해야 하므로 누락 시 *인증 성공해도 다음 요청 비인증* 회귀가 조용히 발생. 필터 방식은 `SessionAuthenticationStrategy`(session fixation 방어)·`SecurityContextRepository`(SecurityContext 의 session 영속)를 **프레임워크가 보장**한다.
  - 인증 성공은 `AuthenticationSuccessHandler` → *204 No Content*. 실패(`AuthenticationException`)는 `AuthenticationFailureHandler` → **401 + ProblemDetail (`AUTHENTICATION_FAILED`)** 로 직접 변환 — `GlobalExceptionHandler` catch-all 로 흐르면 안 됨.
  - `SecurityContext` 의 session 저장은 필터 체인의 `SecurityContextRepository` 가 담당 — **수동 `saveContext` 불필요**.
  - 기본 `formLogin` failure redirect / 빈 응답 / catch-all 500 은 ADR-0005 응답 계약 위반으로 금지
- **`POST /api/auth/logout`** — session invalidate. *204 No Content*

ProblemDetail 응답 형식은 ADR-0005 정합.

### 4b. Spring Security 예외의 ProblemDetail 정합 + 예외 역할 분리

Spring Security 의 인증 / 인가 실패는 **`DispatcherServlet` 이전 단계** 에서 발생. `@RestControllerAdvice` (`GlobalExceptionHandler`) 가 *못 잡음*. 따라서 *별도 핸들러 명시* 필요:

- **`AuthenticationEntryPoint`** — 미인증 요청이 protected resource 진입 시 401 응답 커스터마이즈. ProblemDetail 형식 (ADR-0005) 으로 직렬화
- **`AccessDeniedHandler`** — Security filter chain 에서 인증은 됐지만 권한 부족 시 403 응답 커스터마이즈. ProblemDetail 형식

**예외 역할 분리** (catch-all 500 회귀 차단):

- **Spring Security filter chain 자리** — `AuthenticationException` / `AccessDeniedException` 사용. `AuthenticationEntryPoint` / `AccessDeniedHandler` 가 처리
- **application service 자리** — *Spring `AccessDeniedException` 사용 금지*. 대신 `BusinessException` 계열 (예: `NotPostOwnerException`) throw. `GlobalExceptionHandler` 가 처리, ErrorCode `ACCESS_DENIED` (category `FORBIDDEN`, HTTP 403) 응답
- 사유: 현 `GlobalExceptionHandler` 의 catch-all (`@ExceptionHandler(Exception.class)`) 이 *application service 가 던진 일반 `AccessDeniedException`* 을 *500 으로 가로챌 위험*. `BusinessException` 계열은 *별도 `@ExceptionHandler(BusinessException.class)`* 가 우선 매칭되어 *적절한 4xx + ProblemDetail* 보장

**`ErrorCategory` 확장**:

현재 `NOT_FOUND / INVALID_INPUT / CONFLICT / FORBIDDEN / INTERNAL` 5 종. 본 ADR 결정으로 **`UNAUTHORIZED` 추가** (401, 인증 안 됨). `FORBIDDEN` 은 *권한 부족* (403) 으로 의미 명확화.

**ErrorCode 카탈로그 — `ACCESS_DENIED` 는 본 ADR 확정, 나머지는 PLAN-0011 결정 후보**:

| 코드 | 상태 | category | HTTP | 사용 자리 |
|---|---|---|---|---|
| `ACCESS_DENIED` | **본 ADR 확정** | `FORBIDDEN` | 403 | Security filter chain (`AccessDeniedHandler`) + application service 소유권 검증 실패 (§9) 공통 |
| `AUTHENTICATION_REQUIRED` | PLAN-0011 후보 | `UNAUTHORIZED` | 401 | `AuthenticationEntryPoint` — 미인증 진입 |
| `AUTHENTICATION_FAILED` | PLAN-0011 후보 | `UNAUTHORIZED` | 401 | login 실패 (`AuthenticationException`) |
| `DUPLICATE_EMAIL` | PLAN-0011 후보 | `CONFLICT` | 409 | 회원가입 시 email 중복 |
| `DUPLICATE_NICKNAME` | PLAN-0011 후보 | `CONFLICT` | 409 | 회원가입 시 nickname 중복 |

`ACCESS_DENIED` 가 본 ADR 확정인 이유 — §9 의 소유권 검증 결정이 *application service 의 BusinessException 계열* 을 *반드시 ErrorCode 어떤 것* 에 매핑해야 흐름이 닫힘. 다른 후보는 *flow / 카탈로그 명명 영역* 으로 PLAN 가능.

기본값 (Spring Security default) 채택 시 *redirect 또는 빈 응답* 회귀 위험 — ADR-0005 의 ProblemDetail 계약 *파괴*. 따라서 본 ADR 의 *명시 결정* 으로 박음.

구체 핸들러 구현 / SecurityFilterChain 등록 패턴 / ErrorCode 최종 이름은 PLAN-0011 영역.

### 5. password 정책 — `BCryptPasswordEncoder`

- **`BCryptPasswordEncoder`** — Spring Security 기본
- strength: 기본 `10` (cost factor)
- 평문 저장 *절대 금지*. `User.passwordHash` 는 *해시만* 보유, *plain password* 는 application 입력 boundary 에서만 살아있다 (검증 후 즉시 hash, 폐기)
- 비밀번호 길이 / 복잡도 정책 — Open Question (PLAN-0011 에서 minimum 8자 등 기본값 박을 자리)

### 6. CSRF 활성화 (REST API + session 패턴)

session-based 채택이라 *CSRF 활성화 유지* (Spring Security default + defense in depth).

본 프로젝트는 *REST API only* (Thymeleaf 부재, `@RestController` + `/api/*` JSON). 따라서 *전통적 form hidden field* 방식이 아니라 **CookieCsrfTokenRepository + 헤더 패턴** 채택:

- **token 저장**: `CookieCsrfTokenRepository.withHttpOnlyFalse()` — JavaScript 가 cookie 에서 token 읽기 가능 (XSRF-TOKEN cookie)
- **XSRF-TOKEN 쿠키 보안 속성** — *기본값 의존 금지* (§7 정합):
  - `HttpOnly=false` — *필수* (JS 가 읽어야 함). 그래도 *내용은 token* 이라 *세션 탈취 불가* (token 만으로는 인증 안 됨, JSESSIONID 필요). 단 *CSRF token 자체는 XSS 방어 아님* — XSS 가 발생하면 JS 가 token 읽고 *사용자 세션으로 요청 가능*. XSS 방어는 *입력 sanitize / Content Security Policy / 출력 escape* 등 별도 영역
  - `Secure` — profile 별 (dev=false, prod=true). 운영에서 HTTPS-only
  - `SameSite=Lax` — JSESSIONID 와 통일
  - `Path` / `Domain` — PLAN-0011 결정 (앱 경로 / 서브도메인 정책)
- **token 전송**: 클라이언트가 `X-CSRF-TOKEN` 헤더에 `XSRF-TOKEN` cookie 값 복사하여 전송 (cookie 명 `XSRF-TOKEN`, header 명 `X-CSRF-TOKEN` — PLAN-0011 / `SecurityConfig.CSRF_HEADER_NAME` 정합. 발급 경로 응답의 `headerName` 도 동일)
- **token 발급 경로**: `GET /api/csrf` (또는 동등) — anonymous OK, 첫 진입 시 token 발급용
- **mutation endpoint** (`POST` / `PUT` / `DELETE`) 는 *반드시 token 동반* — 없으면 403
- 로그인 endpoint (`POST /api/auth/login`) 도 *예외 아님* — 첫 호출 전 `GET /api/csrf` 로 token 확보 필요

구체 filter chain 설정 / endpoint 패턴 / 테스트는 PLAN-0011 영역.

대안 (거부):
- *CSRF 비활성화 + SameSite=Lax + Origin 검증* — 더 단순하지만 *defense in depth 약화*. SameSite 의 *subdomain attack / browser quirks* 우려. session 채택의 *서버 통제* 가치와 정합 안 함
- *Spring Security default (HttpOnly cookie)* — JS 가 token 읽기 불가 → REST client 부적합

SPA / mobile client 도입 시 *별도 ADR* — *CSRF 비활성화 + Origin 검증* 같은 대체 의식 재검토 자리.

### 7. cookie security + session timeout — *기본값 의존 금지*

본 ADR 은 **기본값 의존 금지** 결정 박음. `application.yml` / profile 별 yml 에서 *명시* 필수. 사유: 보안 정책은 *기본값 변경에 영향 받음* (Spring Boot 메이저 버전 / Servlet 컨테이너 교체 등). 명시가 *결정 응집* + *변경 의식*.

JSESSIONID 쿠키 속성:

- **`HttpOnly`** — *명시*. `true`. XSS 로 cookie 탈취 방어 (`server.servlet.session.cookie.http-only`)
- **`Secure`** — *profile 별 명시*. dev/local `false` (HTTP), prod `true` (HTTPS 전제). `server.servlet.session.cookie.secure`
- **`SameSite`** — *명시*. `Lax`. *CSRF 1차 방어* + *외부 링크 진입 시 세션 유지*. `Strict` 는 *외부 링크 깨짐* 부작용으로 채택 안 함 (`server.servlet.session.cookie.same-site`)
- **session timeout** — *명시*. PLAN-0011 의 *기본값 박음* (권장 30 분, `server.servlet.session.timeout=30m`). 갱신 정책 (rolling refresh) 은 Open Question

구체 property key / profile 별 값은 PLAN-0011 영역.

### 8. authority / role model — *단일 role*

현 단계 모든 인증된 사용자가 *동등 권한*. Admin / Moderator 등 *role 분화* 는 Open Question — 모더레이션 / 신고 의제 진입 시점에 별도 ADR.

`User` 도메인은 *role field 보유 안 함* 현 단계. 미래 도입 시 *enum 또는 collection field* 추가는 *마이너 추가* — 본 ADR 의 결정에 *영향 없는 자리*.

### 9. `Post.author` 마이그레이션 — *Post → User 참조* + 인증 주체에서 도출

본 ADR 머지 후 PLAN-0011 의 *구현 단위* 가 *동시에 Post.author 마이그레이션* 수행.

**스키마 변경**:
- 현 `Post.author: String` (free input) → `Post.authorId: Long` (User id 참조)
- aggregate 간 *id only 참조* (ADR-0012 영역) 정합
- *seed data / 기존 데이터* 정합: PLAN-0011 에서 *seed 작성자 User 도 함께 seed* (예: `관리자` 사용자 미리 등록 후 그 id 로 Post seed)

**입력 boundary 변경 (spoofing 차단)**:
- `CreatePostRequest` / `UpdatePostRequest` 의 `author` 필드 **제거** — 클라이언트가 작성자 식별 못 함
- `actorUserId` 는 **인증 주체에서 자동 도출** — Spring Security `Authentication` 의 `principal` 에서 `User.id` 추출 → application service 호출 시 인자로 주입
- 도용 시도 자체 *입력 단계에서 불가능* — 클라이언트가 보내봐야 무시

**소유권 검증 (application service 책임)**:
- `PUT /api/posts/{id}` / `DELETE /api/posts/{id}` 호출 시 application service 가 *대상 Post 의 `authorId` 와 인증 주체 `actorUserId` 비교*
- 불일치 시 **`BusinessException` 계열 throw** (예: `NotPostOwnerException`, ErrorCode `ACCESS_DENIED` 카테고리). Spring `AccessDeniedException` *직접 사용 금지* — §4b 의 예외 역할 분리 정합
- `GlobalExceptionHandler` 가 `BusinessException` 매칭 → 403 + ProblemDetail (catch-all 500 가로채기 차단)
- *URL 추측 / id 조작* 으로 타인 글 수정 자리 차단
- 동일 패턴 — Comment / Like 의 update / delete 도 본인 자리만 허용 (각자 도메인의 `NotXxxOwnerException` 또는 공통 `NotOwnerException` — PLAN-0011 결정)

*임시 hybrid* 시간 0 — User 도입 + Post.author 정정 + 인증 endpoint + spoofing 차단이 *한 PLAN (또는 한 PR)* 에서 동시 진입.

## Considered Alternatives

- **JWT (stateless)** — 거부 사유: 사용자 결정 (2026-05-24). session 의 *단순성 + Spring Security 기본 지원* 우선. JWT 의 *refresh token / blacklist / 시계 동기화* 의식이 현 단계 의제와 비용 대비 가치 약함. SPA / mobile client 도입 시 *별도 ADR* 자리.
- **OAuth2 + 소셜 로그인** (Google / GitHub) — 거부 사유: *외부 provider 의존* + *nickname 부여 단계 추가 의식* + *email verified 의식*. 현 단계 의제 분리. *소셜 로그인 도입* 은 미래 ADR — 본 ADR 의 *email/password* 모델 위에 *추가* 형태로 확장 가능.
- **자체 토큰 (custom)** — 거부 사유: 표준 회피 + 휠 재발명. Spring Security session 또는 JWT 표준 중 하나.
- **anonymous 게시 허용** — 거부 사유: 사용자 결정 (2026-05-24). Like aggregate 의 *user 식별 필요* + 본인 글 검증 의제로 인해 *user 가 어차피 도입* → anonymous 모델은 *임시* 일 뿐 *영구 어색*.
- **`Post.author` 마이그레이션을 별도 PLAN 으로 분리** — 거부 사유: 사용자 결정 (2026-05-24). *임시 hybrid 상태* (String author + User 도입 상태) 시간 0 이 가치 큼.
- **role-based authorization 처음부터 도입** — 거부 사유: 현 단계 단일 role 충분. Admin / Moderator 의제 진입 시점 (모더레이션 / 신고 등) 에 *별도 ADR* — 본 ADR 의 결정에 *영향 없음*.
- **`@PreAuthorize` / 메서드 보안** ADR 결정 — 거부 사유: *구현 패턴 결정* 으로 PLAN 영역. 본 ADR 은 *권한 정책* 만 박음 (어디까지 anonymous OK, 어디부터 authenticated 필수).
- **PostgreSQL `citext` 타입으로 nickname case-insensitive 처리** — 거부 사유: `citext` 는 *원본 컬럼 자체* 를 case-insensitive 로 비교하는 접근이라 *canonical 분리* (§1) 와 충돌. 두 메커니즘 (`citext` 의 자체 collation + canonical 컬럼 별도 unique) 이 섞이면 *중복 정책이 두 군데로 분산* — 한쪽 갱신 시 다른쪽 stale 위험. canonical 컬럼 (`nicknameCanonical` varchar unique) 단일 진실원 채택.

## Rejected Suggestions

본 ADR 설계 과정에서 *실제로 제안되었으나 거부된* 안.

- **본 ADR 을 *User aggregate (ADR-0011)* + *인증 패턴 (ADR-0012)* 분리** — 거부 사유: 사용자 결정 (2026-05-24, 옵션 1a). User 와 *그 인증 패턴* 이 밀접 묶임. 분리 시 한쪽이 다른쪽의 *추상 결정* 만 되어 결정 응집도 약화.
- **본 ADR 에 *비밀번호 재설정 / 이메일 인증* 포함** — 거부 사유: *flow 결정* 으로 의제 범위 큼 (token 발급 / SMTP / TTL 등). 본 ADR 머지 후 *후속 ADR* 또는 PLAN 의제. Open Question.
- **본 ADR 에 *session 분산* (Spring Session + Redis) 포함** — 거부 사유: 현 단계 단일 인스턴스 가정. scale 시점에 *별도 ADR* — 본 ADR 의 *기본 session* 결정과 호환 (덮어쓰기 0).

## Consequences

**긍정적 영향**

- *session 의 단순성* + Spring Security 표준 정합 — *학습 비용 ↓*, *Spring 생태계 자료 풍부*
- *조회 anonymous* 유지로 *SEO / 신규 사용자 접근성* 보존 — 로그인 없이도 게시판 탐색 가능
- *쓰기 로그인 필수* 로 Comment / Like 의 user 식별 자연 — Like aggregate 의 unique constraint 정합
- *Post.author = User 참조* 로 *aggregate 간 id 참조 정책 (ADR-0012)* 정합 + 본인 글 검증 / 표시 일관성 / 도용 차단
- BCrypt 해시로 *DB dump 유출 시 평문 password 보호* 1 차 방어선

**부정적 영향 / 트레이드오프**

- *session 의 stateful* — scale 시 *session 분산 의제* 진입 (Spring Session + Redis 등 별도 ADR)
- *Spring Security 도입의 전역 의식* — `SecurityFilterChain`, CSRF, cookie 정책이 *모든 요청* 에 영향. 후행 비용 큼 (의도된 ADR-0004 §1)
- *SPA / mobile client 도입 시 재검토* — session + CSRF 이 *client-server 의식 분리* 와 마찰. 그 시점 *별도 ADR* (CSRF 비활성화 + Origin 검증 등)
- *비로그인 사용자 UX 제한* — 댓글 / 좋아요 시 *로그인 redirect* 의식 (PLAN 의 web layer 결정 영역)
- *Post.author String → User 참조 마이그레이션* 비용 (PLAN-0011 영역, 본 ADR 의 *§9* 결정으로 *임시 hybrid 회피*)
- *seed data 갱신* — 현 `local-data.sql` 의 `author: '관리자'` 가 *User 참조* 로 정정 필요 (PLAN-0011 영역)

## Open Questions

- **session 분산** (Spring Session + Redis 도입 시점) — scale / 다중 인스턴스 의제 진입 시.
- **role / authority 모델** — Admin / Moderator 의 분화 시점. 모더레이션 / 신고 의제 진입 시 별도 ADR.
- **비밀번호 재설정 / 이메일 인증** — token 발급 / SMTP 도입 / TTL 결정 묶임. 별도 ADR 또는 PLAN.
- **account lockout** — brute force 방어 (N 회 연속 실패 시 잠금 등). 현 단계 미적용, scale 시 의제.
- **비밀번호 정책** (길이 / 복잡도 / 사전 검사) — PLAN-0011 에서 *기본값* (예: 최소 8자) 박음. 향후 정책 강화는 별도.
- **email 인증 (verified email)** — 현 단계 *입력 즉시 가입* 가정. 스팸 / 도용 의제 진입 시 verification 단계 추가.
- **소셜 로그인 (OAuth2)** — 미래 의제. 본 ADR 의 email/password 모델 위에 *추가* 형태로 확장.
- **JSESSIONID name override / 보안 추가 설정** — `server.servlet.session.cookie.*` 의 명시 정책. PLAN-0011 결정.
- **nickname confusable / homograph 정책** — NFKC + lower-case 정규화는 *호환 변형 / 대소문자* 까지만 잡음. *서로 다른 문자권의 시각적 유사 문자* (예: Latin `a` vs Cyrillic `а`, Latin `o` vs Greek `ο`) 는 *서로 다른 canonical* 로 *공존 가능* — 닉네임 도용 공격 자리. 현 단계 구현 범위 아님 (한국어 사용자 중심 가정). 글로벌 서비스 의제 진입 시 Unicode `confusables.txt` / ICU `Spoof Checker` 같은 *추가 정규화 단계* 별도 ADR.
- **국제화 email** (IDN / punycode / SMTPUTF8 의 Unicode local-part) — §1 의 *ASCII email format 전제* 가 *국제화 계정 의제 진입 시* 재검토 자리. RFC 6532 / RFC 5891 의식 동반. 현 단계 부재, 별도 ADR.

## Related

- **ADR-0003 (Clean/Hexagonal + DDD + CQRS)** — User aggregate 도 동일 패턴 (domain / port / adapter).
- **ADR-0005 (예외 / 에러 응답 정책)** — 인증 실패 401 / 권한 부족 403 / 중복 가입 409 의 ProblemDetail 응답 정합.
- **ADR-0006 (테스트 전략)** — User aggregate 테스트도 동일 계층 분리 (domain unit / @DataJpaTest slice / E2E).
- **ADR-0008 (Auditing)** — `User.createdAt` / `updatedAt` 도 `AuditingEntityListener` 정합.
- **ADR-0012 (예정)** — aggregate 경계 + id 참조 정책. 본 ADR 의 *User aggregate* 도 그 정책 적용 대상 (id only 참조).
- **PLAN-0011 (예정)** — 본 ADR 의 구현 단위. User aggregate + Spring Security session + auth endpoint + Post.author 마이그레이션 + seed 갱신 통합.
