# PLAN-0011: User aggregate + Spring Security session 인증 + Post.author 마이그레이션

ADR-0011 (User aggregate + Spring Security session 기반 인증) 의 구현 단위. ADR-0011 이 *결정* 만 박은 자리 (구현 패턴 / property key / 명시 안 박힌 정책 등) 를 모두 본 PLAN 에서 박는다 + 코드 구현.

본 PLAN 은 *규모가 매우 큼* (~30+ 파일) 이지만 **단일 PR 진행 결정** — ADR-0011 §9 의 *임시 hybrid 시간 0* 정신 정합 + User 도입과 Post.author 마이그레이션이 *같은 결정 흐름*. 분할 (PLAN-0011-A / B 등) 은 Considered Alternatives 에서 *거부*.

## Goal

ADR-0011 §1~§9 모든 결정의 *직접 구현* + ADR 이 *PLAN-0011 영역* 으로 deferred 한 자리들 본 PLAN 에서 박음:

1. `User` aggregate 도입 (domain / port / adapter 헥사고날 패턴)
2. Spring Security session-based 인증 (`SecurityFilterChain` + `BCryptPasswordEncoder` + `SecurityContextRepository`)
3. 인증 endpoint — `/api/auth/{register, login, logout}` + `/api/csrf` + `/api/users/me`
4. ProblemDetail 핸들러 — `AuthenticationEntryPoint` / `AccessDeniedHandler`
5. `ErrorCategory` 에 `UNAUTHORIZED` + `ErrorCode` 7 종 추가 + HTTP status mapper 갱신
6. `BusinessException` 계열 — `DuplicateEmailException` / `DuplicateNicknameException` / `NotPostOwnerException` 등
7. CSRF — `CookieCsrfTokenRepository.withHttpOnlyFalse()` + XSRF-TOKEN cookie 속성 명시
8. session timeout + cookie security — `application.yml` / profile 별 명시 (기본값 의존 금지)
9. `Post.author` 마이그레이션 — `String` → `User id 참조` (`authorId: Long`) + `actorUserId` 인증 주체에서 도출 + 소유권 검증
10. seed data 갱신 — 관리자 User 도 seed + Post seed 가 그 id 참조

`./gradlew check` BUILD SUCCESSFUL + 모든 기존 테스트 green 유지 (마이그레이션 영향 자리 갱신 포함).

## Scope

### Included — A 영역 (User + Security infrastructure + Auth)

#### 1. User 도메인 / port / adapter

**도메인** (`src/main/java/com/dunowljj/board/domain/user/`):
- `User.java` (aggregate root) — invariant: email/nickname 정규화 + canonical 분리. 생성자 / `create` / `reconstitute` 패턴 (Post 와 동일)
- `Email.java` — value object. `trim().toLowerCase(Locale.ROOT)` 정규화 책임. 형식 검증 (간단한 regex 또는 `jakarta.mail` 없이 자체 + boundary 단계 추가 검증)
- `Nickname.java` — value object. `display` (`trim + NFC`) + `canonical` (`trim + NFKC + lowerCase`) 두 정규화. 길이 / 허용 문자 검증
- `PasswordHash.java` — value object. *해시 결과만* 보유. 평문 password 는 *도메인에 흐르지 않음*
- `InvalidUserContentException.java` (`common.error`, BusinessException 계열) — invariant 위반 시 throw, ErrorCode 후보: `INVALID_USER_CONTENT` (`INVALID_INPUT` 카테고리)
- `UserNotFoundException.java` (`common.error`, BusinessException 계열) — ErrorCode `USER_NOT_FOUND` (`NOT_FOUND`)

**application port in** (`application.port.in.`):
- `RegisterUserUseCase` + `RegisterUserCommand` (record: email, nickname, password)
- `LoginUserUseCase` + `LoginCommand` (record: email, password) — *반환: actorUserId (`Long`)*. SecurityContext 저장은 web adapter 책임
- `GetCurrentUserUseCase` — `/api/users/me` 의 actorUserId 기반 조회

**application port out** (`application.port.out.`):
- `SaveUserPort` — User 저장 (insert)
- `LoadUserPort` — `findById(Long id)` / `findByEmail(Email canonical)` (login 자리)
- `ExistsUserPort` — `existsByEmail(Email canonical)` / `existsByNicknameCanonical(String canonical)` — register 시 중복 사전 검증 (DB unique constraint 도 fallback)
- `PasswordHasherPort` — `hash(String rawPassword)` / `matches(String rawPassword, PasswordHash passwordHash)`. Spring Security `PasswordEncoder` 를 application service 에 직접 주입하지 않기 위한 outbound capability

**application service** (`application.service.`):
- `UserCommandService` — register / login 처리. password BCrypt 해시 / matches 는 `PasswordHasherPort` 로 위임, 중복 검증은 *사전 exists check* 만 수행. DB unique constraint race fallback 은 persistence adapter 가 변환. login 은 *email/password 검증* 후 *actorUserId 반환* (adapter 가 SecurityContext 저장). 실패 시 `AuthenticationFailedException` (`AUTHENTICATION_FAILED`, `UNAUTHORIZED`) throw
- `UserQueryService` — getCurrentUser

**adapter out persistence** (`adapter.out.persistence.user.`):
- `UserJpaEntity` (id, email, nickname, nicknameCanonical, passwordHash, createdAt, updatedAt) — `nicknameCanonical` 에만 unique constraint. `@Table(uniqueConstraints = ...)` 로 constraint name 명시: `uk_users_email`, `uk_users_nickname_canonical`. `nickname` 표시 컬럼은 no unique
- `UserJpaRepository` (Spring Data JPA)
- `UserPersistenceAdapter` (`SaveUserPort` / `LoadUserPort` / `ExistsUserPort` 모두 구현). DB unique constraint race fallback 은 여기서 처리 — `DataIntegrityViolationException` 을 명시 constraint name (`uk_users_email`, `uk_users_nickname_canonical`) 기준으로 `DuplicateEmailException` / `DuplicateNicknameException` 으로 변환. application service 로 `org.springframework.dao.*` 의존 전파 금지
- `UserMapper` — entity ↔ domain 변환

**adapter out security** (`adapter.out.security.`):
- `BCryptPasswordHasherAdapter` — `PasswordHasherPort` 구현. Spring Security `PasswordEncoder` 에 위임 (`BCryptPasswordEncoder` strength 10)

#### 2. Spring Security infrastructure

**`build.gradle`** — `implementation 'org.springframework.boot:spring-boot-starter-security'` 추가 + `testImplementation 'org.springframework.security:spring-security-test'` 추가. 이유: WebMvc / E2E 에서 `csrf()` / authenticated principal mocking / security filter assertion 을 명시적으로 검증.

**security config** (`config.security.`):
- `SecurityConfig` — `SecurityFilterChain` bean + `PasswordEncoder` bean + `SecurityContextRepository` bean (`HttpSessionSecurityContextRepository`)
  - `authorizeHttpRequests` 매핑: GET `/api/posts*`, GET `/api/csrf`, POST `/api/auth/register`, POST `/api/auth/login` anonymous OK / 나머지 mutation + auth 후 endpoint authenticated
  - CSRF: `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `setCookieName("XSRF-TOKEN")` + `setCookiePath("/")` + `setCookieCustomizer(builder -> builder.sameSite("Lax").secure(secure))`. header name 은 Spring Security 기본 `X-CSRF-TOKEN` 을 사용
  - `securityContext` config — explicit `securityContextRepository(HttpSessionSecurityContextRepository)` 설정 (JSON 커스텀 login 자리 정합)
  - `exceptionHandling` — `authenticationEntryPoint` + `accessDeniedHandler` 명시
  - `sessionManagement` — `SessionCreationPolicy.IF_REQUIRED` (default 명시)
- `BCryptPasswordEncoder` — strength 10
- `ProblemDetailAuthenticationEntryPoint` (adapter 영역, ProblemDetail 직렬화 — `ErrorCode.AUTHENTICATION_REQUIRED`)
- `ProblemDetailAccessDeniedHandler` (adapter 영역, ProblemDetail 직렬화 — `ErrorCode.ACCESS_DENIED`)

*ArchUnit 의식*: `config.security.*` 가 *Spring Security* 의존. application 패키지 안에 두면 `application_spring_narrow` 위반. *adapter 또는 별도 config 패키지* (현 `config/` 와 동일 위치, application 외부) 에 배치.

#### 3. Auth endpoints

**adapter.in.web.auth** (`adapter.in.web.auth.`):
- `AuthController`:
  - `POST /api/auth/register` — `RegisterRequest` → `RegisterUserUseCase` → 201 + 응답 (User id, email, nickname display)
  - `POST /api/auth/login` — **`AuthController` 가 아니라 custom authentication filter 가 처리** (F-b, ADR-0011 §4 amended 2026-06-07). `JsonUsernamePasswordAuthenticationFilter` (`AbstractAuthenticationProcessingFilter` 확장, `/api/auth/login` POST 매칭) 의 `attemptAuthentication` 이 JSON body 파싱 → **기존 `LoginUserUseCase.login()` 직접 호출** → `UsernamePasswordAuthenticationToken.authenticated(actorUserId, null, List.of())` 반환. 베이스의 `successfulAuthentication` 이 session fixation 전략 + `SecurityContextRepository` 저장을 *프레임워크 차원* 수행 → 성공 `AuthenticationSuccessHandler` 204 / 실패(`AuthenticationException`) `AuthenticationFailureHandler` 401 ProblemDetail. `formLogin` 미사용, `AuthenticationManager` 는 trivial pass-through(auth 는 use case 가 수행)
  - `POST /api/auth/logout` — `SecurityContextLogoutHandler` 호출 또는 `HttpSession.invalidate()` → 204
- `UserController`:
  - `GET /api/users/me` — `Authentication.principal` → actorUserId → `GetCurrentUserUseCase` → 200 + user info
- `CsrfController` (또는 `/api/csrf` mapping):
  - `GET /api/csrf` — `CsrfToken` 응답 + `XSRF-TOKEN` Set-Cookie 명시 발급. anonymous OK. JS 가 첫 진입 시 호출
- `dto/request/`: `RegisterRequest`, `LoginRequest`
- `dto/response/`: `UserResponse` (id, email, nickname display, createdAt 정도)

**Bean Validation** — Spring Security 기존 `@Valid` 흐름과 정합. `@NotBlank @Email`, `@Size` 등.

#### 4. Error model 확장

**`common.error.`**:
- `ErrorCategory` 에 `UNAUTHORIZED` 추가 (총 6 종)
- `ErrorCode` 에 7 종 추가:
  - `ACCESS_DENIED` (`FORBIDDEN`, "접근 권한이 없습니다") — ADR-0011 §4b 확정
  - `AUTHENTICATION_REQUIRED` (`UNAUTHORIZED`, "로그인이 필요합니다") — `AuthenticationEntryPoint` 자리
  - `AUTHENTICATION_FAILED` (`UNAUTHORIZED`, "이메일 또는 비밀번호가 올바르지 않습니다") — login 실패
  - `DUPLICATE_EMAIL` (`CONFLICT`, "이미 사용 중인 이메일입니다") — register
  - `DUPLICATE_NICKNAME` (`CONFLICT`, "이미 사용 중인 닉네임입니다") — register
  - 또한 `USER_NOT_FOUND` (`NOT_FOUND`) + `INVALID_USER_CONTENT` (`INVALID_INPUT`) — User 도메인 검증
- `ErrorCategoryHttpStatusMapper` 갱신 — `UNAUTHORIZED -> HttpStatus.UNAUTHORIZED`. enum switch exhaustive 컴파일 깨짐 방지
- `BusinessException` 계열 신규:
  - `DuplicateEmailException`
  - `DuplicateNicknameException`
  - `InvalidUserContentException`
  - `UserNotFoundException`
  - `AuthenticationFailedException`
  - `NotPostOwnerException` (B 영역)
- `GlobalExceptionHandler` 갱신 — `BusinessException` 분기 (기존) 가 새 ErrorCode 들 자동 처리. 새 핸들러 추가 *불요* (기본 `BusinessException` 분기로 충분).

#### 5. application.yml / profile 별 security 속성

**`application.yml`** (전역 default):
```yaml
server:
  servlet:
    session:
      timeout: 30m
      cookie:
        name: JSESSIONID
        http-only: true
        same-site: lax
        # secure: profile-specific
```

**`application-local.yml`** 추가:
```yaml
server:
  servlet:
    session:
      cookie:
        secure: false
```

**`application-prod.yml`** 추가:
```yaml
server:
  servlet:
    session:
      cookie:
        secure: true
```

XSRF-TOKEN cookie 속성은 *Spring 의 standard property 없음* — `CookieCsrfTokenRepository` builder 패턴으로 `SecurityConfig` 안에서 설정 (`setCookiePath("/")` + `setCookieCustomizer`). `secure` 값은 `server.servlet.session.cookie.secure` profile 값을 주입해 JSESSIONID 와 동일 정책으로 맞춘다. CSRF header name 은 Spring Security 기본 `X-CSRF-TOKEN` 을 사용한다.

#### 6. ArchUnit 갱신 (필요 시)

*검토*: 현 8 규칙은 *기존 패키지 가정* — `..domain..` / `..application..` / `..adapter..` / `..common.error..`. User 패키지가 같은 패턴 따라가면 *자동 정합*. 다만:
- `..config.security..` 가 Spring Security 의존 — `application_spring_narrow` 규칙은 application 한정이라 *영향 없음*. *명시 확인*.
- `domain_pure` — User 도메인이 Spring 의존 0 보장
- `common_error_framework_neutral` — 새 ErrorCode / Exception 도 동일 패턴
- 새 ArchUnit 규칙 *추가 안 함* (현 규칙으로 충분 — User 패키지가 패턴 정합)

### Included — B 영역 (Post.author 마이그레이션)

#### 7. 도메인 / persistence / DTO 마이그레이션

- `Post.java` — `author: String` 필드 → `authorId: Long`. `create` / `reconstitute` 시그니처 변경 (`String author` → `Long authorId`). `getAuthor()` → `getAuthorId()`. `validateAuthor` → `validateAuthorId` (null 검증)
- `PostJpaEntity.java` — `author` 컬럼 → `author_id` 컬럼 (`@Column(name="author_id")` Long). `getAuthor()` → `getAuthorId()`
- `PostMapper.java` — 매핑 갱신
- `CreatePostRequest.java` — `author` 필드 *제거* (인증 주체에서 도출). title / body 만 유지
- `UpdatePostRequest.java` — author 변경 자리 없음 확인
- `PostResponse.java` — `author: String` → `authorId: Long` + `authorNickname: String` (display 형)
- `AuditedPostResult.java` (port in result) — `author: String` → `authorId: Long` + `authorNickname: String`
- `PostListResult.java` / `PostPage.java` / `AuditedPost.java` — 목록/상세 응답이 `authorNickname` 을 운반할 수 있게 result record 갱신
- `LoadPostPort` / `PostJpaRepository` / `PostPersistenceAdapter` — `posts.author_id` 와 `users.id` join 기반 projection 추가. `findById` / `findPage` / create-update 반환 경로가 모두 `authorNickname` 포함
- `CreatePostCommand` (UseCase nested) — `author: String` 제거, `actorUserId: Long` 추가

#### 8. application service + controller — 인증 주체에서 도출

- `PostCommandService.create()` — `CreatePostCommand` 수령 시 `actorUserId` 사용. `Post.create(title, body, actorUserId)` 호출
- `PostCommandService.update()` / `delete()` — *소유권 검증*: `LoadPostPort.findById(id)` → `existing.getAuthorId().equals(actorUserId)` 불일치 시 `NotPostOwnerException` throw (ErrorCode `ACCESS_DENIED`)
- `PostController` — `Authentication authentication` 인자 (또는 `@AuthenticationPrincipal`) → `actorUserId` 추출 → service 호출 시 인자로 전달. *ArchUnit 정합 확인*: controller 만 Spring Security 의존, application 으로 전파 0

#### 9. seed data

- `local-data.sql`:
  - 먼저 admin User INSERT (예: email='admin@example.com', nickname='관리자', nicknameCanonical='관리자', passwordHash=BCrypt('admin123') 미리 계산값)
  - 그 다음 Post seed 가 `author_id = <admin user id>` (or use sub-query)
- *BCrypt 해시 값 사전 계산* — seed SQL 안에 hardcoded. 보안 결정: dev seed 라 OK

#### 10. 테스트 갱신

- `PostFixtures` — `aValidPost(authorId: Long)` 패턴 + 기본 `DEFAULT_AUTHOR_ID = 1L`
- `PostTest`, `PostCommandServiceTest`, `PostControllerTest`, `PostPersistenceAdapterTest`, `PostE2EIT` — author 사용 자리 모두 `authorId` 로 갱신
- 새 테스트:
  - `UserPersistenceAdapterTest` (@DataJpaTest slice) — save / findByEmail / existsByXxx + email/nicknameCanonical 정규화 검증 + nickname display 보존
  - `AuthE2EIT` (@SpringBootTest) — register → CSRF token 획득 → login → cookie 보유 → me → logout 흐름
  - `SecurityConfigE2EIT` — anonymous GET 200 / mutation + CSRF 없음 403 / mutation + CSRF 있음 + 비인증 401 / mutation + CSRF 있음 + 인증 200
  - `PostOwnershipIT` — A 사용자 글을 B 사용자가 수정 시도 시 403 + ACCESS_DENIED

### Not Included

- **비밀번호 재설정 / 이메일 인증** — ADR-0011 Open Question, 별도 ADR
- **account lockout** — Open Question
- **role / Admin** — Open Question
- **소셜 로그인 (OAuth2)** — 미래 ADR
- **session 분산 (Redis)** — scale 시점, 별도 ADR
- **nickname confusable / homograph 정규화** — 글로벌 서비스 의제, 별도 ADR
- **국제화 email (IDN / SMTPUTF8)** — 별도 ADR
- **`@PreAuthorize` 메서드 보안** — 본 PLAN 은 `SecurityFilterChain.authorizeHttpRequests` 만. 메서드 단위 보안은 미래 결정
- **Comment / Like / PostMetrics 도입** — 후속 PLAN 영역
- **password 정책 강제** — 본 PLAN 은 *최소 8자만* 박음. 복잡도 / 사전 검사 / pwned password 검사 등은 별도 ADR

## Non-goals

- 운영 인프라 결정 (운영 DB / session 분산 / 보안 강화).
- ADR-0011 본문 변경 — 이미 settled.
- 도메인 추가 (Comment / Like / PostMetrics).
- migration tool 도입 (Flyway / Liquibase) — 현재 `create-drop` (dev) / `create` (test) / `validate` (prod) 유지.

## Related ADRs

- **ADR-0011** (User aggregate + Spring Security session 인증) — 본 PLAN 권위. §1~§9 직접 구현.
- **ADR-0003** (Clean/Hexagonal + DDD + CQRS) — User aggregate 도 동일 패턴. ArchUnit 규칙 정합.
- **ADR-0005** (예외 / 에러 응답 정책) — ProblemDetail 응답 + ErrorCode 카탈로그.
- **ADR-0006** (테스트 전략) — 새 테스트 자리들도 동일 계층 분리 (domain unit / @DataJpaTest slice / E2E).
- **ADR-0008** (Auditing) — User entity 도 `AuditingEntityListener` 정합 (createdAt / updatedAt).
- **ADR-0010** (DB 인프라) — Testcontainers + PostgreSQL 환경에서 새 테스트 작성.

## Files to Inspect

- `docs/adr/0011-user-aggregate-and-session-authentication.md` — 본 PLAN 권위 (모든 § 결정).
- `src/main/java/com/dunowljj/board/common/error/` (ErrorCategory / ErrorCode / BusinessException / 기존 예외) — 확장 대상.
- `src/main/java/com/dunowljj/board/adapter/in/web/error/ErrorCategoryHttpStatusMapper.java` — `UNAUTHORIZED` → 401 매핑 추가 대상.
- `src/main/java/com/dunowljj/board/adapter/in/web/exception/GlobalExceptionHandler.java` — `@ExceptionHandler(BusinessException.class)` 기존 분기 확인 — 새 ErrorCode 자동 처리.
- `src/main/java/com/dunowljj/board/domain/post/Post.java` — 마이그레이션 대상.
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostJpaEntity.java` — 컬럼 변경 대상.
- `src/main/java/com/dunowljj/board/adapter/in/web/PostController.java` + `dto/request/CreatePostRequest.java` — author 입력 제거 대상.
- `src/main/java/com/dunowljj/board/application/service/PostCommandService.java` + `application/port/in/CreatePostUseCase.java` — actorUserId 패턴 도입.
- `src/main/java/com/dunowljj/board/application/port/in/result/AuditedPostResult.java` — author 필드 마이그레이션.
- `src/main/java/com/dunowljj/board/application/port/in/result/PostListResult.java` / `application/common/PostPage.java` / `application/port/out/result/AuditedPost.java` — `authorNickname` 운반 여부 갱신.
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostJpaRepository.java` — author nickname join projection 추가 대상.
- `src/main/java/com/dunowljj/board/config/TimeConfig.java` — User audit 도 동일 Clock 정합 확인.
- `src/test/java/com/dunowljj/board/architecture/HexagonalArchitectureTest.java` — 새 패키지 영향 확인 (위반 없는지).
- `src/test/java/com/dunowljj/board/architecture/TestStrategyArchitectureTest.java` — `@SpringBootTest` 위치 규칙 확인 (`..e2e..` / BoardServiceApplicationTests 한정).
- `src/test/java/com/dunowljj/board/config/PostgresTestcontainersConfig.java` — 새 통합 테스트도 동일 `@Import` 패턴.
- `src/test/java/com/dunowljj/board/domain/post/PostFixtures.java` — author 패턴 마이그레이션.
- `src/main/resources/application.yml` / `application-local.yml` / `application-prod.yml` — session / cookie 속성 명시.
- `src/main/resources/db/seed/local-data.sql` — admin User + Post.authorId 갱신.

## Files to Touch

### A 영역 — User + Security infrastructure + Auth

신규 (production — User 도메인):
- `src/main/java/com/dunowljj/board/domain/user/User.java`
- `src/main/java/com/dunowljj/board/domain/user/Email.java`
- `src/main/java/com/dunowljj/board/domain/user/Nickname.java`
- `src/main/java/com/dunowljj/board/domain/user/PasswordHash.java`

신규 (production — User application):
- `src/main/java/com/dunowljj/board/application/port/in/RegisterUserUseCase.java`
- `src/main/java/com/dunowljj/board/application/port/in/LoginUserUseCase.java`
- `src/main/java/com/dunowljj/board/application/port/in/GetCurrentUserUseCase.java`
- `src/main/java/com/dunowljj/board/application/port/in/result/UserResult.java` (또는 동등)
- `src/main/java/com/dunowljj/board/application/port/out/SaveUserPort.java`
- `src/main/java/com/dunowljj/board/application/port/out/LoadUserPort.java`
- `src/main/java/com/dunowljj/board/application/port/out/ExistsUserPort.java`
- `src/main/java/com/dunowljj/board/application/port/out/PasswordHasherPort.java`
- `src/main/java/com/dunowljj/board/application/service/UserCommandService.java`
- `src/main/java/com/dunowljj/board/application/service/UserQueryService.java`

신규 (production — User persistence):
- `src/main/java/com/dunowljj/board/adapter/out/persistence/user/UserJpaEntity.java`
- `src/main/java/com/dunowljj/board/adapter/out/persistence/user/UserJpaRepository.java`
- `src/main/java/com/dunowljj/board/adapter/out/persistence/user/UserPersistenceAdapter.java`
- `src/main/java/com/dunowljj/board/adapter/out/persistence/user/UserMapper.java`

신규 (production — Security outbound adapter):
- `src/main/java/com/dunowljj/board/adapter/out/security/BCryptPasswordHasherAdapter.java`

신규 (production — Auth web):
- `src/main/java/com/dunowljj/board/adapter/in/web/auth/AuthController.java`
- `src/main/java/com/dunowljj/board/adapter/in/web/auth/UserController.java`
- `src/main/java/com/dunowljj/board/adapter/in/web/auth/CsrfController.java`
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/request/RegisterRequest.java`
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/request/LoginRequest.java`
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/response/UserResponse.java`

신규 (production — Security):
- `src/main/java/com/dunowljj/board/config/security/SecurityConfig.java`
- `src/main/java/com/dunowljj/board/config/security/ProblemDetailAuthenticationEntryPoint.java`
- `src/main/java/com/dunowljj/board/config/security/ProblemDetailAccessDeniedHandler.java`
- `src/main/java/com/dunowljj/board/config/security/JsonUsernamePasswordAuthenticationFilter.java` — F-b login filter (`AbstractAuthenticationProcessingFilter` 확장, `LoginUserUseCase` 직접 호출, `AuthenticationFailedException` → `BadCredentialsException` 번역)
- `src/main/java/com/dunowljj/board/config/security/JsonLoginSuccessHandler.java` — 204 No Content
- `src/main/java/com/dunowljj/board/config/security/JsonLoginFailureHandler.java` — 401 ProblemDetail (`AUTHENTICATION_FAILED`)

(`AuthController` 에서 `login` 제거 — register 만 남김. login 은 위 필터로 이전.)

신규 (production — error):
- `src/main/java/com/dunowljj/board/common/error/DuplicateEmailException.java`
- `src/main/java/com/dunowljj/board/common/error/DuplicateNicknameException.java`
- `src/main/java/com/dunowljj/board/common/error/InvalidUserContentException.java`
- `src/main/java/com/dunowljj/board/common/error/UserNotFoundException.java`
- `src/main/java/com/dunowljj/board/common/error/AuthenticationFailedException.java`

수정 (production):
- `build.gradle` — `spring-boot-starter-security` + `spring-security-test` 의존성
- `src/main/java/com/dunowljj/board/common/error/ErrorCategory.java` — `UNAUTHORIZED` 추가
- `src/main/java/com/dunowljj/board/common/error/ErrorCode.java` — 7 종 추가 (ACCESS_DENIED, AUTHENTICATION_REQUIRED, AUTHENTICATION_FAILED, DUPLICATE_EMAIL, DUPLICATE_NICKNAME, USER_NOT_FOUND, INVALID_USER_CONTENT)
- `src/main/java/com/dunowljj/board/adapter/in/web/error/ErrorCategoryHttpStatusMapper.java` — `UNAUTHORIZED -> HttpStatus.UNAUTHORIZED` 추가
- `src/main/resources/application.yml` — session timeout + cookie 속성
- `src/main/resources/application-local.yml` — `secure: false`
- `src/main/resources/application-prod.yml` — `secure: true`

신규 (test — A 영역):
- `src/test/java/com/dunowljj/board/domain/user/UserTest.java`
- `src/test/java/com/dunowljj/board/domain/user/EmailTest.java`
- `src/test/java/com/dunowljj/board/domain/user/NicknameTest.java`
- `src/test/java/com/dunowljj/board/domain/user/UserFixtures.java`
- `src/test/java/com/dunowljj/board/application/service/UserCommandServiceTest.java`
- `src/test/java/com/dunowljj/board/application/service/UserQueryServiceTest.java`
- `src/test/java/com/dunowljj/board/adapter/out/persistence/user/UserPersistenceAdapterTest.java` (@DataJpaTest + Testcontainers)
- `src/test/java/com/dunowljj/board/adapter/in/web/auth/AuthControllerTest.java` (@WebMvcTest)
- `src/test/java/com/dunowljj/board/e2e/AuthE2EIT.java` (@SpringBootTest + MockMvc + Testcontainers)
- `src/test/java/com/dunowljj/board/e2e/SecurityConfigE2EIT.java` (@SpringBootTest + MockMvc + Testcontainers)

### B 영역 — Post.author 마이그레이션

수정 (production):
- `src/main/java/com/dunowljj/board/domain/post/Post.java` — `author: String` → `authorId: Long`
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostJpaEntity.java` — `author` 컬럼 → `author_id`
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostMapper.java` — 매핑 갱신
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostJpaRepository.java` — `authorNickname` join projection 추가
- `src/main/java/com/dunowljj/board/adapter/in/web/PostController.java` — `Authentication` 받아 actorUserId 전달
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/request/CreatePostRequest.java` — `author` 필드 제거
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/response/PostResponse.java` — `authorId` + `authorNickname` 로 갱신
- `src/main/java/com/dunowljj/board/application/service/PostCommandService.java` — actorUserId 처리 + 소유권 검증
- `src/main/java/com/dunowljj/board/application/port/in/CreatePostUseCase.java` — Command record 갱신
- `src/main/java/com/dunowljj/board/application/port/in/UpdatePostUseCase.java` — `UpdatePostCommand` 에 actorUserId 추가
- `src/main/java/com/dunowljj/board/application/port/in/DeletePostUseCase.java` — `DeletePostCommand(Long id, Long actorUserId)` 로 변경
- `src/main/java/com/dunowljj/board/application/port/in/result/AuditedPostResult.java` — `authorId` + `authorNickname` 필드 마이그레이션
- `src/main/java/com/dunowljj/board/application/port/in/result/PostListResult.java` — list item result 변경 전파 확인
- `src/main/java/com/dunowljj/board/application/common/PostPage.java` — `AuditedPost` 변경 전파 확인
- `src/main/java/com/dunowljj/board/application/port/out/result/AuditedPost.java` — `authorNickname` 포함
- `src/main/java/com/dunowljj/board/application/port/out/LoadPostPort.java` — join projection 반환 계약 명시
- `src/main/resources/db/seed/local-data.sql` — admin User 추가 + Post.author_id

신규 (B 영역):
- `src/main/java/com/dunowljj/board/common/error/NotPostOwnerException.java`

수정 (test — B 영역):
- `src/test/java/com/dunowljj/board/domain/post/PostTest.java`
- `src/test/java/com/dunowljj/board/domain/post/PostFixtures.java` — `DEFAULT_AUTHOR_ID`
- `src/test/java/com/dunowljj/board/application/service/PostCommandServiceTest.java`
- `src/test/java/com/dunowljj/board/application/service/PostQueryServiceTest.java`
- `src/test/java/com/dunowljj/board/adapter/in/web/PostControllerTest.java`
- `src/test/java/com/dunowljj/board/adapter/out/persistence/post/PostPersistenceAdapterTest.java`
- `src/test/java/com/dunowljj/board/e2e/PostE2EIT.java` — 인증 흐름 추가 (CSRF + login + cookie 보유)

신규 (test — B 영역):
- `src/test/java/com/dunowljj/board/e2e/PostOwnershipIT.java` (@SpringBootTest) — A 사용자 글을 B 사용자가 수정/삭제 시 403 + ACCESS_DENIED 검증

## Implementation Steps

**단일 PR 진행** (옵션 A). 분할 옵션은 Considered Alternatives 에서 거부.

순서는 *의존성 + 빌드 통과 단계* 기준. 본 PR 은 *단일 commit* 머지 (선례 PLAN-0006-C/D, PLAN-0007/0008/0009/0010).

#### A 영역 — User + Security infrastructure + Auth (먼저)

1. **`build.gradle` — `spring-boot-starter-security` + `spring-security-test` 의존성 추가**.
2. **`ErrorCategory` + `ErrorCode` 확장** (`UNAUTHORIZED` 카테고리 + 7 ErrorCode) + `ErrorCategoryHttpStatusMapper` 에 `UNAUTHORIZED -> 401` 추가. mapper 누락 시 enum switch compile fail.
3. **`BusinessException` 계열 신규** — `DuplicateEmailException` / `DuplicateNicknameException` / `InvalidUserContentException` / `UserNotFoundException` / `AuthenticationFailedException` / `NotPostOwnerException` (B 영역 활용).
4. **User 도메인 (`domain.user.`)** — User / Email / Nickname / PasswordHash + value object 검증.
5. **application outbound `PasswordHasherPort`** (Risk #5 해결) — `application.port.out.PasswordHasherPort` 정의 (Spring 의존 0). `adapter.out.security.BCryptPasswordHasherAdapter` 가 `PasswordEncoder` 위임.
6. **User application port + service** — RegisterUserUseCase / LoginUserUseCase / GetCurrentUserUseCase + UserCommandService / UserQueryService.
7. **User persistence adapter** — UserJpaEntity (audit 정합) / UserJpaRepository / UserPersistenceAdapter / UserMapper. `save` 는 DB unique constraint race 를 `DuplicateEmailException` / `DuplicateNicknameException` 으로 변환.
8. **Security config (`config.security.`)** — SecurityConfig (filter chain / passwordEncoder bean / securityContextRepository / CSRF / exceptionHandling) + ProblemDetailAuthenticationEntryPoint + ProblemDetailAccessDeniedHandler. `AuthenticationManager` 는 사용하지 않음 (login 은 `LoginUserUseCase` 경로).
9. **Auth endpoints (`adapter.in.web.auth.`)** — AuthController / UserController / CsrfController + DTO.
10. **application.yml / -local.yml / -prod.yml — session + cookie 속성**.

#### B 영역 — Post.author 마이그레이션 (이어서 같은 commit)

11. **`Post` 도메인 마이그레이션** — `author: String` → `authorId: Long`. `create` / `reconstitute` 시그니처 변경.
12. **`PostJpaEntity` 마이그레이션** — `author` → `author_id` 컬럼.
13. **`PostMapper` 갱신**.
14. **DTO / Command / Result 마이그레이션** — `CreatePostRequest` 의 `author` 제거 / `PostResponse` author 표현 (`authorId + authorNickname`) / `AuditedPostResult`, `AuditedPost`, `PostPage`, `PostListResult` 필드 / `CreatePostCommand` 의 actorUserId 추가.
15. **`PostCommandService` 갱신** — `actorUserId` 처리 + update/delete 시 소유권 검증 + `NotPostOwnerException` throw.
16. **`PostJpaRepository` / `PostPersistenceAdapter` 갱신** — `users` join projection 으로 `authorNickname` 포함. 상세 / 목록 / create-update 반환 경로 모두 동일 응답 계약 보장.
17. **`PostController` 갱신** — `Authentication` 인자 추출, actorUserId 전달.
18. **seed 갱신 (`local-data.sql`)** — admin User insert + Post.author_id (sub-query 또는 hardcoded id).

#### 테스트 (전체 통합)

19. **User 테스트** — 도메인 unit / value object EqualsVerifier / UserPersistenceAdapterTest (@DataJpaTest slice) / UserCommandServiceTest.
20. **Auth / Security 테스트** — AuthControllerTest (@WebMvcTest, `spring-security-test` 활용) / AuthE2EIT (@SpringBootTest, CSRF + login + me + logout 흐름) / SecurityConfigE2EIT (access matrix + CSRF 실패 경로).
21. **PostOwnershipIT 신규** — 본인 글 / 타인 글 (403 + ACCESS_DENIED) 분기 검증.
22. **기존 Post 테스트 갱신** — PostFixtures / PostTest / PostCommandServiceTest / PostQueryServiceTest / PostControllerTest / PostPersistenceAdapterTest / PostE2EIT (author 패턴 + 인증 흐름).
23. **`./gradlew check` 통과 확인** — `test` (Domain unit + ArchUnit + slice) + `integrationTest` (E2E + smoke) 모두 green.

## Acceptance Criteria

### A 영역
- `User` 도메인 + value object (Email / Nickname / PasswordHash) 생성 / 검증 동작.
- `Email.canonical()` = `trim().toLowerCase(Locale.ROOT)`.
- `Nickname.display()` = `NFC + trim`, `Nickname.canonical()` = `NFKC + lowerCase + trim`.
- `PasswordHash` 는 *plain password 보유 안 함*.
- DB schema — `users` 테이블, `email` unique (`uk_users_email`), `nickname_canonical` unique (`uk_users_nickname_canonical`), `nickname` *unique 없음*.
- `POST /api/auth/register` — 정상 201 + 응답 body (id, email, nickname display).
- `POST /api/auth/register` — email 중복 409 + `DUPLICATE_EMAIL` ProblemDetail.
- `POST /api/auth/register` — nicknameCanonical 중복 409 + `DUPLICATE_NICKNAME` (Alice / alice 차단 검증).
- `POST /api/auth/login` — 정상 시 JSESSIONID cookie 발급 + SecurityContext session 저장 + 다음 요청 인증 유지.
- `POST /api/auth/login` — 실패 시 401 + `AUTHENTICATION_FAILED` ProblemDetail (catch-all 500 회귀 없음).
- `POST /api/auth/logout` — session invalidate + JSESSIONID 만료.
- `GET /api/csrf` — anonymous OK + XSRF-TOKEN cookie 발급 (HttpOnly=false).
- XSRF-TOKEN cookie — `Path=/`, `SameSite=Lax`, `Secure` 는 active profile 의 `server.servlet.session.cookie.secure` 와 동일.
- `GET /api/users/me` — 인증 시 200, 비인증 시 401 + `AUTHENTICATION_REQUIRED`.
- mutation endpoint (POST/PUT/DELETE) 호출 시 CSRF token 없으면 403.
- AuthenticationEntryPoint / AccessDeniedHandler 가 ProblemDetail 직렬화 (text/* 아님, application/problem+json).
- `ErrorCategoryHttpStatusMapper` 가 `UNAUTHORIZED` 를 401 로 매핑.
- application.yml + profile 별 session / cookie 속성 명시.
- ArchUnit 11 규칙 모두 통과.
- `./gradlew check` BUILD SUCCESSFUL (test + integrationTest).

### B 영역
- `Post.authorId: Long` (String 부재).
- `PostJpaEntity` 의 `author_id` 컬럼.
- `CreatePostRequest` 에 `author` 필드 부재.
- `PostResponse` / list item 응답이 `authorId` + `authorNickname` 을 반환.
- 상세 / 목록 / 생성 / 수정 응답 모두 `users` join 또는 동등 조회로 author nickname 누락 없음.
- `POST /api/posts` 호출 시 인증된 사용자 id 자동 도출 (request body 의 author 같은 spoofing 차단).
- `PUT /api/posts/{id}` 본인 글 200, 타인 글 403 + `ACCESS_DENIED`.
- `DELETE /api/posts/{id}` 본인 글 204, 타인 글 403 + `ACCESS_DENIED`.
- `local-data.sql` 의 admin User + Post.author_id 정합.
- 기존 Post 테스트 모두 green (`author: String` 가정 제거).
- PostOwnershipIT — A 글 B 수정 / B 삭제 모두 403.
- `./gradlew check` BUILD SUCCESSFUL.

## ADR Required

**no** — ADR-0011 가 권위. 본 PLAN 은 ADR-0011 §1~§9 의 *직접 구현* + ADR 이 *PLAN 영역* 으로 deferred 한 자리들 결정. 새 시스템 boundary 결정 0.

본 PLAN 에서 박는 *결정 자리* (ADR 가 PLAN 영역으로 위임):
- **nickname 길이 / 허용 문자** — 길이 2-20 자, 허용 문자 = alphanumeric + 한글 (`\p{IsHangul}`) + 한정 기호 (`_`, `-`). 결정.
- **비밀번호 길이** — 최소 8 자. 최대 = UTF-8 기준 72 bytes (BCrypt truncation 한계 정합). web DTO 는 `@Size(max = 72)` 로 1차 방어, application service 가 byte 길이로 최종 방어.
- **ErrorCode 최종 이름** — ADR-0011 §4b 의 후보 5 종 그대로 채택 + User 도메인용 `USER_NOT_FOUND`, `INVALID_USER_CONTENT` 추가.
- **SecurityContextRepository** — `HttpSessionSecurityContextRepository` bean 등록 + `SecurityConfig.securityContext(...)` 명시. **저장은 인증 필터 체인의 `successfulAuthentication` 이 자동 수행** (수동 `saveContext` 불필요 — F-b).
- **Login 인증 처리 방식 (F-b, ADR-0011 §4 amended)** — custom authentication filter `JsonUsernamePasswordAuthenticationFilter` (`AbstractAuthenticationProcessingFilter` 확장). `attemptAuthentication`: JSON 파싱 → `LoginUserUseCase.login()` 직접 호출(검증 실패 시 use case 가 `AuthenticationFailedException` throw) → 성공 시 `UsernamePasswordAuthenticationToken.authenticated(actorUserId, null, List.of())` 반환. 베이스가 `SessionAuthenticationStrategy`(session fixation)·`SecurityContextRepository`(context 영속) 처리. `AuthenticationManager` 는 trivial pass-through(`auth -> auth`) — 베이스 `afterPropertiesSet` 의 non-null 요구 충족용, 실제 인증은 use case. 성공 `JsonLoginSuccessHandler` → 204, 실패 `JsonLoginFailureHandler` → 401 ProblemDetail(`AUTHENTICATION_FAILED`). 사유: 프레임워크가 session/context 보장 + application 으로 Spring Security 의존 전파 0(use case 는 Long 만 반환).
- **CSRF token endpoint 응답 본문 형식** — `{"headerName": "X-CSRF-TOKEN", "parameterName": "_csrf", "token": "..."}`. cookie name 은 `XSRF-TOKEN` 으로 명시하고, header name 은 Spring Security 기본 `X-CSRF-TOKEN` 을 사용한다. E2E 는 `/api/csrf` 응답의 cookie + header 조합으로 실제 mutation 성공까지 검증.
- **cookie path / domain** — `path=/`, `domain` 미지정 (기본 — 요청 host).
- **Post.author 마이그레이션 시 기존 데이터** — 현재 dev seed 만 영향. prod 미운영. *마이그레이션 SQL 불요* (schema 신규 생성).
- **PostResponse author 표현** — `authorId: Long` + `authorNickname: String` (display 형) 두 필드. join 시 효율 — `JOIN users ON posts.author_id = users.id` (단일 query, Persistence adapter 영역).
- **Security 테스트 의존성** — `spring-security-test` 추가. WebMvc slice 에서 `csrf()` / 인증 principal mocking 을 명시 사용하고, E2E 는 실제 `/api/csrf` + session cookie 흐름 검증.

## Considered Alternatives

### 분할 옵션 — 거부

- **옵션 B (PLAN-0011-A + PLAN-0011-B 분할)** — 거부.
  - 안: User + Security infrastructure 와 Post.author 마이그레이션을 *두 sequential PR* 로 분리.
  - 거부 사유: (a) ADR-0011 §9 의 *임시 hybrid 시간 0* 결정과 충돌 — 사용자가 *옵션 2a (함께)* 명시 선택. (b) PR 1 머지 후 main 에 *User 존재 + Post.author=String* 의 *어색한 상태* 잠시 존재. (c) 두 PR *의존성 의식 비용* (연속 머지 강제 / 다른 PR 끼우지 않기) 누적. (d) User 도입과 Post.author 마이그레이션이 *완전 독립 의제 아님* — User 도입 *목적 자체* 가 *Post.author spoofing 차단 + Comment/Like user 식별*, *같은 결정 흐름*.
  - 채택 안 한 trade-off: PR 규모 ~30+ 파일이라 *리뷰 부담 ↑*. *완화*: 본 PR 의 *영역별 (A/B) 명시 구조* + Codex/scout 리뷰 패턴이 *영역별 검토* 자연 지원.

- **옵션 C (3-way 분할)** — 거부. PR 너무 작음, 의식 비용 ↑. 옵션 B 보다 *더 큰 단점*.

## Risks

1. **ArchUnit `application_spring_narrow` 위반 위험** — Spring Security 의 `Authentication` / `SecurityContext` 같은 의존이 *application* 패키지로 흘러 들어가면 위반. *완화*: controller (adapter.in.web) 에서 추출, actorUserId (Long) 만 application 으로 전달. PLAN 의 모든 service 시그니처 의식.

2. **SecurityContext 영속 (F-b 로 위험 감소)** — custom authentication filter 가 `AbstractAuthenticationProcessingFilter` 를 확장하므로 `successfulAuthentication` 이 `SecurityContextRepository` 저장 + session fixation 전략을 *프레임워크 차원* 수행 → 수동 `saveContext` 누락 회귀 자체가 제거됨. *잔여 위험*: `SecurityConfig.securityContext(...)` repository 설정 누락 시 저장 안 됨. *완화*: SecurityConfig 명시 설정 + AuthE2EIT 의 *login → me 호출* 검증.

3. **CSRF 흐름 의식** — `POST /api/auth/login` 도 CSRF token 필요. 클라이언트가 *`GET /api/csrf` 먼저 호출* 흐름 강제. *완화*: AuthE2EIT 의 첫 step 이 CSRF 획득.

4. **Login 실패 처리 (F-b)** — 인증 필터는 `DispatcherServlet` 밖이라 `GlobalExceptionHandler` 가 못 잡음. `LoginUserUseCase` 는 Spring-free 한 `AuthenticationFailedException`(BusinessException) 을 throw 하므로, **필터의 `attemptAuthentication` 이 이를 catch → Spring `AuthenticationException`(예: `BadCredentialsException`) 으로 재throw** → `JsonLoginFailureHandler` 가 401 ProblemDetail(`AUTHENTICATION_FAILED`) 직렬화. 경계 번역으로 application 은 Spring Security 의존 0 유지. AuthE2EIT 가 실패 login 401 검증.

5. **BCrypt 처리 위치 — `PasswordHasherPort` outbound port 채택 (Implementation Step 5)** — `PasswordEncoder` bean 이 *security config 영역*. application service 가 직접 주입받으면 *Spring 의존 한 자리 추가* → ArchUnit `application_spring_narrow` 위반. **해결: `application.port.out.PasswordHasherPort` 정의 (Spring 의존 0) + `adapter.out.security.BCryptPasswordHasherAdapter` 가 `PasswordEncoder` 위임**. ArchUnit whitelist 확장 회피 + Hexagonal port 패턴 정합.

6. **PostResponse 응답 본문 변경 — 클라이언트 호환성** — `author: String` → `authorId + authorNickname`. 현 시점 *클라이언트 없음* (REST API only) 이라 영향 0. *완화*: 본 PR description 에 명시.

7. **DB schema 자동 생성** — `ddl-auto: create-drop` (local) / `create` (test) 이라 *마이그레이션 SQL 불요*. 단 운영 시점 (`validate`) 에서는 *Flyway / Liquibase* 필요 — 별도 의제 (운영 ADR).

8. **CSRF token 의 *동일 cookie* 발급 정책** — Spring `CookieCsrfTokenRepository` 가 *첫 요청* 시 자동 발급 vs *명시 endpoint* 호출 시. 두 흐름 공존 가능. *완화*: `GET /api/csrf` 가 *명시적 발급 자리*. 첫 호출 패턴 강제 (E2E 검증).

9. **seed BCrypt 해시 사전 계산** — `local-data.sql` 안에 BCrypt 해시 hardcoded. dev 한정이라 보안 영향 0. *주의*: 운영 시점 *환경변수 / vault* 패턴 별도.

### Pre-resolved

- **TimeConfig + AuditingEntityListener** — User entity 도 동일 패턴 자동 정합 (`AuditingEntityListener` 가 `createdAt` / `updatedAt` 자동 채움). 별도 작업 없음.
- **PostgresTestcontainersConfig** — 새 통합 테스트도 동일 `@Import` 패턴.
- **TestStrategyArchitectureTest `springboottest_is_localized`** — 새 `..e2e..` 자리는 정합. 다른 위치에 `@SpringBootTest` 추가하면 위반. AuthE2EIT 도 `..e2e..` 패키지 배치.

## Implementation Hints

### `User.java` (도메인)

```java
package com.dunowljj.board.domain.user;

public class User {
    private Long id;
    private Email email;
    private Nickname nickname;
    private PasswordHash passwordHash;

    private User(Long id, Email email, Nickname nickname, PasswordHash passwordHash) {
        this.id = id;
        this.email = email;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
    }

    public static User register(Email email, Nickname nickname, PasswordHash passwordHash) {
        return new User(null, email, nickname, passwordHash);
    }

    public static User reconstitute(Long id, Email email, Nickname nickname, PasswordHash passwordHash) {
        if (id == null) throw new IllegalArgumentException("Id must not be null");
        return new User(id, email, nickname, passwordHash);
    }

    public Long getId() { return id; }
    public Email getEmail() { return email; }
    public Nickname getNickname() { return nickname; }
    public PasswordHash getPasswordHash() { return passwordHash; }
}
```

### `Nickname.java` (value object)

```java
public final class Nickname {
    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9\\p{IsHangul}_-]+$");

    private final String display;
    private final String canonical;

    public Nickname(String input) {
        if (input == null) throw new InvalidUserContentException("nickname");
        String trimmed = input.trim();
        if (trimmed.isEmpty() || trimmed.length() < 2 || trimmed.length() > 20) {
            throw new InvalidUserContentException("nickname");
        }
        String normalizedDisplay = Normalizer.normalize(trimmed, Form.NFC);
        if (!ALLOWED.matcher(normalizedDisplay).matches()) {
            throw new InvalidUserContentException("nickname");
        }
        this.display = normalizedDisplay;
        this.canonical = Normalizer.normalize(trimmed, Form.NFKC).toLowerCase(Locale.ROOT);
    }
    
    public String display() { return display; }
    public String canonical() { return canonical; }
}
```

### `SecurityConfig.java` (skeleton)

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, 
                                            SecurityContextRepository scr,
                                            @Value("${server.servlet.session.cookie.secure:false}") boolean secure,
                                            ProblemDetailAuthenticationEntryPoint authEntry,
                                            ProblemDetailAccessDeniedHandler accessDenied) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieName("XSRF-TOKEN");
        csrfTokenRepository.setCookiePath("/");
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secure));

        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/*", "/api/posts/*/comments").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .anyRequest().authenticated()
            )
            .csrf(c -> c.csrfTokenRepository(csrfTokenRepository))
            .securityContext(c -> c.securityContextRepository(scr))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(authEntry)
                .accessDeniedHandler(accessDenied)
            )
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .build();
    }
}
```

### `AuthController.login` (skeleton)

```java
@PostMapping("/api/auth/login")
public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
    Long actorUserId = loginUserUseCase.login(new LoginCommand(request.email(), request.password()));
    if (httpRequest.getSession(false) != null) {
        httpRequest.changeSessionId();
    }
    Authentication auth = UsernamePasswordAuthenticationToken.authenticated(actorUserId, null, List.of());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, httpRequest, httpResponse);
    return ResponseEntity.noContent().build();
}
```

### `PostController.create` (B 영역 후)

```java
@PostMapping
public ResponseEntity<PostResponse> create(@Valid @RequestBody CreatePostRequest request,
                                             Authentication authentication) {
    Long actorUserId = (Long) authentication.getPrincipal();  // or via UserDetails
    var result = createPostUseCase.create(new CreatePostCommand(request.title(), request.body(), actorUserId));
    return ResponseEntity.status(201).body(PostResponse.from(result));
}
```

## Execution Notes

<!-- 실행 중 비자명한 결정만 시간순 append. 사소한 구현 디테일은 적지 않는다. -->

- 2026-05-28: PostControllerTest 의 actorUserId 전달 검증 자리 제거. `@WebMvcTest(addFilters = false)` 환경에서 `@AuthenticationPrincipal` resolver 가 SecurityContextHolder 를 못 읽음 (security autoconfig 부분 미로드). 실제 actor 전달 검증은 `PostOwnershipIT` (E2E) 가 담당 — 슬라이스 테스트는 *나머지 필드 / 응답* 만.
- 2026-05-28: CSRF header name = `X-CSRF-TOKEN` 으로 확정. `CookieCsrfTokenRepository` 는 cookie name 만 `XSRF-TOKEN` 으로 명시하고 header name 은 Spring Security 기본값을 따른다. `CsrfController` 가 `XSRF-TOKEN` Set-Cookie 를 명시 발급하고, AuthE2EIT 가 `/api/csrf` 응답의 `headerName` 값 + `XSRF-TOKEN` cookie + token header 조합으로 실제 mutation 성공까지 검증.
- 2026-05-28: AuthController login 에 session fixation 방어 추가 — `httpRequest.changeSessionId()` 명시 호출. Spring Security 기본 흐름의 `SessionAuthenticationStrategy` 미실행이라 수동 회수. AuthE2EIT 에 *로그인 전후 session id 변경* 검증 추가.
- 2026-05-28: 비밀번호 byte 길이 검증 추가 — BCrypt 의 실질 72 byte 한계 정합. UTF-8 byte 기준 (`password.getBytes(StandardCharsets.UTF_8).length > 72`) 검증 + `RegisterRequest.password @Size(max = 72)` 동기화. 100 char 였던 PLAN 본문 결정은 *truncation 위험* 으로 정정.
- 2026-05-28: `AuditedPostResult.from(AuditedPost)` 메서드 제거 — `port.in.result` 가 `port.out.result` 를 직접 의존하지 않는다는 ADR-0008 §5.1 정신 정합. service 들이 `from(post, nickname, createdAt, updatedAt)` 시그니처로 풀어 조립.
- 2026-06-07: **login 흐름 컨트롤러 → custom authentication filter (F-b) 전환** (ADR-0011 §4 amended). `AuthController.login` 제거 → `JsonUsernamePasswordAuthenticationFilter`(`AbstractAuthenticationProcessingFilter` 확장)가 `LoginUserUseCase` 직접 호출, 성공/실패 핸들러로 204 / 401 ProblemDetail. session fixation·SecurityContext 저장을 필터 베이스가 보장(수동 `saveContext`/`changeSessionId` 제거). `AuthenticationManager` 는 trivial pass-through, application 의 `AuthenticationFailedException` 은 필터에서 `BadCredentialsException` 으로 경계 번역. 미shipped·in-flight 초기 결정이라 supersede ADR 대신 ADR-0011 제자리 개정. 거부한 대안: F-a(커스텀 AuthenticationProvider/UserDetailsService) — 단일 인증 방식이라 ceremony 과다, 기존 use case 와 역할 중복. dead `LoginRequest` DTO 제거(필터가 자체 record 사용). E2E(AuthE2EIT 7, SecurityConfigE2EIT 6, PostOwnershipIT 3, PostE2EIT 2) 전부 그린.
- 2026-06-07: 머지 전 리뷰 픽스 — Lombok 일관성(F1: UserCommandService/UserQueryService/UserController `@RequiredArgsConstructor`; Auth·Csrf 는 `@Value` 때문에 명시 유지), JPQL FQN→엔티티 단순명(F2: PostJpaRepository). 입력검증 에러 계약 통일(F3)은 ADR-0005 에러 모델 진화로 커져 보류 → ADR-0005 "향후 ADR 후보"에 Known-Limitation 기록.
- 2026-06-07: 머지 전 리뷰 픽스 (Risk #5 영역). (1) unique constraint 이름을 `UserJpaEntity.EMAIL_CONSTRAINT/NICKNAME_CONSTRAINT` 단일 출처로 통합 — `@UniqueConstraint` 와 변환 분기가 같은 상수 참조(이름만 바뀌면 race fallback 이 조용히 깨지던 잠재 버그 제거). (2) adapter `save()` 의 try/catch + 변환을 `UserStore.saveUnique()` 로 캡슐화하고 재사용 메커니즘 `UniqueViolationGuard` 분리 — adapter 에는 비즈니스 흐름만 남김. 거부한 대안: 호출부 inline guard(`.on().execute()` 가 비즈니스 메서드에 매핑 노출), Spring Data fragment(CRUD `saveAndFlush` 재사용 불가 → `EntityManager` 수기). 사용처 1개라 `UserStore` 는 User 전용 — 2번째 어댑터가 같은 패턴 요구 시 공유 래퍼로 승격.
