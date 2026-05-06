# ADR-0006: 테스트 전략 — 계층별 책임 분리

## Status
Proposed

## Date
2026-05-03

## Context

ADR-0003에서 Clean/Hexagonal + DDD + CQRS 구조와 의존성 규칙("외부 계층이 내부 계층에 의존하고 Domain은 바깥을 모른다" — source dependencies point inward: Adapter → Application → Domain)을 채택했고, PLAN-0003·PLAN-0004로 Post 도메인 CRUD 골격까지 구축됐다. 그러나 테스트 영역에는 아직 구조 규약이 없다. 이 상태로 다음 도메인이 추가되면 다음 붕괴 패턴이 반복될 가능성이 높다.

- **계층 무관 테스트** — 모든 검증을 `@SpringBootTest` 한 가지로 작성. 시작은 쉽지만 실행이 느려지고, 한 테스트가 여러 계층의 실패 원인을 동시에 안고 있어 회귀 시 원인 추적이 어렵다.
- **과도한 mock 단위 테스트** — 모든 계층을 mock으로 포장. 컴파일과 분기 커버리지는 채워지지만 실제 동작(JPA 매핑, HTTP 계약, 트랜잭션)은 검증되지 않아 false safety를 만든다.
- **계층 경계 위반 테스트** — Domain 테스트가 Spring을 띄우거나, Service 테스트가 실제 JPA를 부르거나, Controller 테스트가 DB를 건드리는 식으로 ADR-0003의 의존성 규칙이 테스트에서 무너진다. 한 번 무너지면 운영 코드의 경계도 같이 흐려진다.

이 영역은 *지금* 정책이 없으면 도메인이 늘어날 때마다 비용이 기하급수로 증가하는 자리다. 후행 도입의 비용이 큰 경계(API 계약·테스트 의식)는 ADR-0004 §1의 "단단한 골격 우선" 원칙에 따라 골격 단계에서 결정한다.

## Decision

테스트는 ADR-0003의 계층 구분과 동일한 경계로 *책임을 분리*한다. 각 계층의 테스트는 그 계층의 실패 모드만 검증하고, 다른 계층은 신뢰 가능한 경계(Port, slice)로 대체한다. 전체 의식은 *layer-aware test pyramid* 형태를 따른다.

### 1. 계층별 테스트 책임

| 계층 | 검증 대상 | 도구 | Spring | 외부 자원 |
|---|---|---|---|---|
| Domain | Aggregate 불변식, VO 동등성, `create`/`reconstitute` 일치, 상태 전이, 도메인 예외 | JUnit 5 + AssertJ | ✗ | ✗ |
| Application Service (Command) | Use Case 분기, Output Port 호출, **저장 시 도메인 상태 변화** (ArgumentCaptor) | JUnit 5 + Mockito | ✗ | ✗ (Port mock) |
| Application Service (Query) | Use Case 분기, Output Port → result DTO 매핑 | JUnit 5 + Mockito | ✗ | ✗ (Port mock) |
| Driving Adapter (Web) | HTTP 계약, 성공 응답, 검증 오류, JSON 바인딩 오류, path/query 파라미터, `@RestControllerAdvice`, `ProblemDetail` 스키마, MDC/필터 | `@WebMvcTest` + MockMvc | slice | ✗ (Input Port mock) |
| Driven Adapter (Persistence) | JPA Entity ↔ Domain 매핑(behavior 있는 경우), Output Port 계약, auditing, dirty checking, CRUD, 페이지네이션·정렬·쿼리 | `@DataJpaTest` | slice | H2(임시) → Testcontainers + 운영 DB(목표) |
| ↳ Mapper *(Persistence 종속, 단독 테스트 선택)* | JPA Entity ↔ Domain 변환 중 *정책이 있는* 매핑: VO 조립/해체, enum/code 변환, null/default, embedded, soft delete, auditing, version, id/생성·수정시각 보존, `reconstitute` 의미 보존 | JUnit 5 + AssertJ | ✗ | ✗ |
| Architecture (ArchUnit) | ADR-0003 의존성 규칙·Port 결과 DTO 누출·도메인 에러의 HTTP 의존 금지 | ArchUnit | ✗ | ✗ |
| End-to-End | 골든 플로우 1–2개 (Create → Get → Update → Delete) | `@SpringBootTest` + MockMvc | full | H2(임시) → Testcontainers + 운영 DB(목표) |

### 2. 핵심 규칙

- **Domain 테스트는 절대 Spring을 띄우지 않는다.** `@Entity` 의존이 없으니 테스트도 프레임워크 없이 돌아야 한다(ADR-0003 §10).
- **Application Service 테스트는 Output Port를 mock한다.** JPA 동작을 시뮬레이션하지 않는다 — 그건 Driven Adapter 계층의 책임.
- **Application Command 테스트는 *상태 변화*를 우선 검증한다.** Command Use Case 의 핵심 행동은 Port 호출 자체가 아니라 *도메인 상태를 올바르게 변경한 뒤 저장 경계로 전달*하는 것이다. 따라서 호출 검증은 *보조 신호*이고, 저장되는 도메인 객체를 `ArgumentCaptor` 로 잡아 필드·불변식·상태 전이를 직접 어서트하는 것을 *우선*으로 둔다. 단, 호출 검증도 의미를 가질 때가 있다 — 예: not-found 일 때 `savePort.save()` 가 *호출되지 않음* 을 검증하는 것은 정당한 계약이다. 호출 검증을 *대체*가 아닌 *보완*으로 사용한다.
- **CQRS 분리 — Command와 Query는 테스트 클래스를 분리한다.** ADR-0003 §"CQRS Rules" 와 §"CQRS Coupling Boundary" 에 맞춰. 단, 순수 도메인 fixture builder(예: `PostFixtures.aValidPost()`)는 *공유 허용*. 공유 금지 대상은 *mutable setup, mock stubbing, 테스트 간 상태 의존성* — 두 서비스의 테스트가 같은 setup 변경에 결합되어 한쪽이 깨지면 다른 쪽도 깨지는 구조를 만들지 않는다.
- **Web 테스트는 `@WebMvcTest` slice로 격리.** Input Port를 mock하고, Controller·DTO·`@RestControllerAdvice` 를 검증. `TestRestTemplate` 은 E2E 전용.
- **필터 테스트는 분리 — slice 자동 포함 여부에 *의존하지 않는다*.** 현재 `TraceIdFilter` 는 `FilterRegistrationBean` 기반으로 등록된다. `@WebMvcTest` slice 가 이 설정을 자동 포함하는지는 등록 방식·controller 범위·`MockMvc` 의 `addFilters` 설정에 따라 달라진다. 따라서 다음 중 하나를 *명시적으로* 선택한다 — (a) 필터 자체 단위 테스트(`MockFilterChain` 로 직접 호출, MDC 검증), (b) `@WebMvcTest(...) @Import(TraceIdFilterConfig.class)` 로 슬라이스에 명시 적재. 자동 포함을 *가정* 한 Web 계약 테스트는 환경에 따라 필터 없이 통과할 수 있다.
- **Persistence 테스트는 `@DataJpaTest` slice.** 실제 SQL 실행 결과를 검증. Service나 Controller를 끌어들이지 않는다.
- **H2 는 *임시 다리*, Testcontainers 가 목표.** H2 는 운영 DB(PostgreSQL 등)와 SQL 방언·격리 수준·인덱스/락 동작에 차이가 있어 *방언 의존 행동*은 검증할 수 없다. 운영 DB가 확정되거나 H2-only 한계가 처음 드러나는 시점(예: window function, advisory lock, JSON 컬럼)에 Testcontainers 로 전환한다. 새 Persistence 테스트는 이 전환을 가정하고 *H2 한정 동작에 의존하지 않게* 작성한다(예: `IDENTITY` 시퀀스 동작 가정 금지, H2 전용 함수 금지).
- **Mapper 는 Driven Adapter (Persistence) 의 기본 책임이다. 단독 테스트는 *정책이 있을 때만* 작성한다.** 단순 1:1 필드 복사는 `@DataJpaTest` slice 의 Output Port round-trip(save → load → 같은 도메인) 검증으로 충분하다. round-trip 이 *놓치는* 변환만 단독 테스트 대상 — 입력 정규화(trim·lowercase 처럼 두 다른 입력이 같은 저장 형태가 되어 round-trip 으로 구별되지 않는 경우), VO 조립/해체, enum/code 변환, null/default, embedded, soft delete 상태 결정, auditing/version 우선순위, `reconstitute` 의미 보존. *"round-trip 으로 구별 가능한가"* 가 판정 질문.
- **E2E는 희소하게.** 각 도메인당 골든 플로우 1–2개. 비즈니스 규칙의 1차 안전망이 되어선 안 된다 — 그건 위 계층의 합산 책임.
- **E2E·Persistence 통합테스트는 *분리 실행 가능*하게.** 빠른 단위·slice 테스트와 별도로 호출할 수 있어야 빌드 피드백 루프가 짧아진다. 분리 수단은 JUnit 5 `@Tag("integration")` + Gradle test task 분기, 또는 별도 sourceSet 중 하나. 구체 수단은 PLAN-0006 에서 결정.

### 3. 아키텍처 테스트 (ArchUnit)

ArchUnit 은 **두 가지 use-case** 를 갖고, 본 프로젝트는 둘을 *별도 클래스* 로 분리한다.

- **`HexagonalArchitectureTest`** — ADR-0003 의 *프로덕션 경계* 강제. `importOptions = ImportOption.DoNotIncludeTests.class` 로 production 코드만 스캔.
- **`TestStrategyArchitectureTest`** — 본 ADR 의 *테스트 의식* 강제. 테스트 바이트코드를 스캔하므로 `DoNotIncludeTests` 적용하지 않음. forward-defense 규칙은 현재 매칭 subject 가 0 건이므로 `.allowEmptyShould(true)` 로 ArchUnit 0.23+ 의 empty-should 기본 fail 회피.

테스트 코드를 production 규칙에 묶지 않는 이유 — 테스트는 의도적으로 mock·fixture·`@SpringBootTest` 등 *경계 너머* 를 사용한다. 한 클래스에서 두 use-case 를 섞으면 production 변경 없이 테스트 추가만으로 ArchUnit 이 빨개지는 함정이 생김.

#### 3.1 `HexagonalArchitectureTest` — 프로덕션 경계

ADR-0003의 의존성 규칙을 빌드 시점에 강제한다. ArchUnit으로 다음을 검증:

- `domain..` 패키지가 `org.springframework..`, `jakarta.persistence..`, `org.hibernate..`, `jakarta.servlet..`, `jakarta.validation..`, `..adapter..`, `..application..` 를 import하지 않는다. (Application·Port는 외부이고 Domain은 내부 — 역방향 의존 금지.)
- `common.error..` (`BusinessException` / `ErrorCode` / 도메인 예외 거주지) 도 framework-neutral shared kernel 로 취급한다. 금지 — `org.springframework..`, `jakarta.persistence..`, `org.hibernate..`, `jakarta.servlet..`, `jakarta.validation..`, `..adapter..`, `..domain..`, `..application..`. shared kernel 은 *어느 쪽도 모르는* 자리. HTTP 매핑은 `adapter.in.web.exception..` 의 책임 (ADR-0005 §"Web Exception Handling Location").
- `application..` 패키지가 `..adapter..` 를 import하지 않는다.
- **Application 의 Spring 의존 — 명시적으로 좁게 허용한다.** 현재 `PostCommandService`/`PostQueryService` 는 `@Service`, `@Transactional` 을 사용한다. 이는 *의도된 실용적 양보* — 트랜잭션 경계와 Bean 등록은 Application 책임이라 분리 비용 대비 가치가 낮다. 허용 범위는 `org.springframework.stereotype..`, `org.springframework.transaction..`, `jakarta.transaction..` 로 제한. 금지 — `org.springframework.web..`, `org.springframework.data..`, `jakarta.persistence..`, `jakarta.servlet..`. 향후 순수 Application 으로 리팩터링하려면 별도 ADR.
- `adapter.in.web..` 가 `adapter.out..` 를 import하지 않는다 (Driving → Driven 직접 의존 금지).
- Port 인터페이스는 `application.port..` 에만 선언된다.
- Driving Adapter가 Input Port에 선언된 nested **Result** 타입을 import하지 않는다. nested **Command** record (예: `CreatePostUseCase.CreatePostCommand`)는 허용 — Adapter → Application 단방향 의존이라 무해(clean-architecture.md §"Port Result DTO Layout"). Result 타입은 `application/port/in/result/` 의 top-level 로만 허용.
- 패키지 간 cyclic dependency 없음.

이 테스트는 운영 코드의 경계를 *문서가 아닌 빌드*로 지킨다.

#### 3.2 `TestStrategyArchitectureTest` — 테스트 의식

ADR-0006 §1 의 계층별 테스트 책임 표가 *현실에서 지켜지는가* 를 빌드로 강제한다. 사람이 prose 로만 지키기 어려운 부분을 좁게 자동화한다. 다음을 검증:

- **Domain 테스트는 POJO** — `..domain..` 의 `*Test` 클래스가 `org.springframework..`, `org.mockito..`, `jakarta.persistence..`, `org.hibernate..` 의존 금지. ADR-0006 §2 ("Domain 테스트는 절대 Spring 을 띄우지 않는다") 의 빌드 강제.
- **Application Service 테스트는 `@SpringBootTest` 금지** — `..application.service..` 의 `*Test` 가 `@SpringBootTest` 어노테이션 부착 금지. Application 은 Output Port mock 으로 단위 테스트되어야 한다.
- **`@SpringBootTest` 는 `..e2e..` 패키지 또는 `BoardServiceApplicationTests` 한정** — 느린 통합테스트가 슬라이스 의식을 가진 위치(domain·application·adapter)에 흩어지지 않게.

prescriptive 한 규칙(예: "Web 테스트는 반드시 `@WebMvcTest` 사용") 은 *제외* — 필터 단위 테스트, 매퍼 단위 테스트 같은 정당한 예외가 있어 너무 경직됨. 그런 규칙은 §2 prose 로 충분하고, 강제 가치가 없음.

규칙들은 forward-defense — 현재 subject 가 0 건이라도 PLAN-0006-B/C/D 가 테스트를 추가할 때 즉시 강제된다.

### 4. 테스트는 문서다 (Tests as Documentation)

테스트는 단순 검증 도구가 아니라 *시스템이 무엇을 약속하는가*를 코드로 진술하는 living specification 이다. 실패하면 회귀를 알리고, 읽으면 도메인 규칙을 가르친다. 이 원칙은 다음 작성 규약의 *근거*다.

- **테스트 이름이 명세 문장이 되도록.** `@DisplayName` 한국어 권장. `create_saves_post()` 보다 `@DisplayName("create 시 도메인이 올바른 상태로 저장된다")` 가 우선.
- **하나의 테스트 = 하나의 약속.** 한 테스트가 여러 행동을 어서트하지 않는다. 실패 시 *어떤 약속이 깨졌는가*가 즉시 보여야 한다.
- **Given/When/Then 구조 가시화.** 빈 줄로 분리하거나 주석으로 구획. 읽는 사람이 setup·행동·검증을 한눈에 분별.
- **arbitrary 한 매직 넘버 금지.** 도메인 의미 있는 값을 fixture·상수로 명명.
- **Domain·Application 테스트는 도메인 언어로 쓴다.** 인프라 단어(`save`, `findById` 같은 Port 메서드명)에 의존하지 말고, 도메인 행동(`등록한다`, `찾을 수 없다`)으로 표현.

테스트 작성 시 "이 테스트가 6개월 뒤 처음 보는 사람에게 *규칙*을 설명해 주는가"를 자문한다. 그렇지 않으면 그 테스트는 검증으로도 약하다.

따라서 테스트는 구현 세부가 아니라 *외부에서 관찰 가능한 규칙과 계층별 계약*을 설명해야 한다. 이 원칙이 §2(상태 검증 우선), §6(명명 규약), §8(private 메서드·구현 세부 mock 제외)의 공통 근거다.

### 5. 결정성 (Determinism)

- **시간 의존 로직은 `Clock` 주입을 *목표*로 한다 — 단, 별도 ADR/Plan.** 현재 `Post.create` / `Post.updateContent` 는 `LocalDateTime.now()` 를 직접 호출한다. PLAN-0006 은 *현 구조를 전제로* 허용 범위·상태 변화·도메인 규칙만 검증한다. Clock 주입 리팩터링은 본 ADR 범위 밖이며 별도 ADR/Plan 으로 진행한다(ADR-0004 §3 의 시간 주입 ADR 후보 슬롯). 테스트와 도메인 시간 정책 변경을 한 실행 단위에 섞지 않는다.
- 무작위성(UUID 등)은 별도 Provider 인터페이스로 분리해 테스트에서 고정값을 주입한다 (현재 `Post`에는 해당 없음, 향후 도메인 추가 시 적용).
- **테스트 간 상태 누수 방지 — 슬라이스별 정책이 다르다.**
  - `@DataJpaTest` — 트랜잭션 롤백 기본 적용.
  - `@SpringBootTest` (E2E) — 자동 롤백 *없음*. 다음 중 하나를 명시: (a) 클래스/메서드에 `@Transactional` (단, 비동기·multi-thread 경로에선 부정확), (b) `@Sql` 로 setup/teardown, (c) 각 테스트가 고유 데이터를 만들고 자체 cleanup 책임. PLAN-0006 에서 도메인별로 정책을 결정해 기록한다.
  - 외부 상태(파일, 환경변수 등) 의존 금지.

### 6. 명명·구조 규약

§4의 구체적 표현형. *왜* 는 §4 참조.

- 클래스: `<ProductionClass>Test` (단위) / `<ProductionClass>IT` (Integration·E2E).
- 메서드: 동작 중심 + 한국어 `@DisplayName`. 예: `@DisplayName("작성자 공백이면 예외")`.
- 픽스처: 도메인별 `<Aggregate>Fixtures` 정적 빌더.
- 패키지: `src/test/java` 는 운영 패키지와 1:1 미러.

### 7. 커버리지 정책 (목표가 아닌 가드)

- 라인 커버리지 숫자를 목표로 두지 않는다 — 목표는 *각 계층의 실패 모드가 그 계층 테스트로 잡히는가*.
- Domain 불변식과 Application Service 의 의미 있는 분기는 *누락 없이* 테스트한다. 이 영역은 코드 양이 적고 변경 영향이 크므로, 커버리지 수치보다 *빠진 규칙이 없는지* 를 우선 검토한다.

### 8. 테스트하지 않는 것 (Non-goals)

테스트 작성 비용 대비 가치가 없거나 *오히려 회귀 신호를 흐리는* 항목은 명시적으로 제외한다.

- **getter/setter** — 자명한 접근자.
- **Lombok이 생성한 코드** (`@Getter`, `@Builder`, `@EqualsAndHashCode` 등) — 라이브러리의 책임.
- **private 메서드 직접 테스트** — public API 행동으로 간접 검증. 직접 테스트는 리팩터링 저항성을 깨뜨린다.
- **구현 세부를 복제하는 mock 테스트** — `verify(port).save(any())` 만 외치는 테스트. 실제 행동(상태 변화)을 검증하지 않으면 의미 없음.
- **자명한 1:1 매퍼** — 정책이 없는 필드 복사. Mapper 테스트는 §2에 정의한 *정책 있는 변환*에만 작성.

### 9. 보류 — Spring Security 도입 시 활성화

다음 영역은 *현재 보류*다. Spring Security 가 미도입이고, 인증·인가 의식이 코드에 없으므로 검증 대상이 없다. 보안 도구가 도입되는 ADR 시점에 본 ADR을 갱신하거나 별도 ADR로 분리한다.

- 인증 흐름 테스트 (로그인, 토큰 발급, 만료, 갱신)
- 인가 테스트 (`@WithMockUser`, `@PreAuthorize`, 메서드 시큐리티)
- CSRF / CORS 정책 테스트
- Security Filter Chain 통합 테스트
- 권한 누락에 대한 401/403 응답 일관성 테스트

이 보류는 *결정*이다 — 미정의가 아니라 "도입 전까지 작성하지 않는다"가 명시된 상태. Spring Security 도입 시 본 절을 활성화하고 §1 표에 Security 행을 추가한다.

### 10. CI 전략 (최소 분리는 PLAN-0006 필수)

§2 의 "통합테스트 분리 실행 가능" 원칙을 닫는다. PLAN-0006 은 **최소한 다음 분리**를 도입한다 — 빠른 단위·slice 테스트는 기본 `test` task, 느린 통합테스트(`@SpringBootTest` E2E·Persistence 일부)는 `@Tag("integration")` + 별도 `integrationTest` task. 별도 sourceSet 로 갈지는 빌드 시간 추이를 보고 결정.

테스트 카테고리별 실행 시점:

| 카테고리 | 실행 시점 | task |
|---|---|---|
| Domain / Application unit | 매 커밋 | `test` |
| ArchUnit | 매 PR | `test` |
| Web slice / Persistence slice (빠른 슬라이스) | 매 PR | `test` |
| E2E / 느린 통합테스트 | 매 PR, 머지 전 | `integrationTest` |

CI 워크플로 자체(GitHub Actions 등) 도입은 빌드 시간·횟수가 문제가 되는 시점에 별도 Plan.

### 11. 예시 — 본 프로젝트 특수성이 있는 패턴만

자명한 패턴(POJO Domain JUnit, `@WebMvcTest` + MockMvc 기본형, JUnit 5 일반)은 생략. 본 프로젝트의 boundary·CQRS·Spring Boot 4 결합 지점에서 *틀리기 쉬운* 셋만 기록.

#### 11.1 Application Command — ArgumentCaptor 로 상태 검증

`verify(port).save(any())` 만으로는 회귀가 잡히지 않는다. 저장되는 도메인을 잡아 필드·불변식을 직접 어서트한다.

```java
@ExtendWith(MockitoExtension.class)
class PostCommandServiceTest {
    @Mock LoadPostPort loadPostPort;
    @Mock SavePostPort savePostPort;
    @Mock DeletePostPort deletePostPort;
    @InjectMocks PostCommandService sut;

    @Test
    @DisplayName("게시글을 등록하면 입력값 그대로 저장된다")
    void create_saves_post_with_expected_state() {
        var captor = ArgumentCaptor.forClass(Post.class);
        when(savePostPort.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        sut.create(new CreatePostUseCase.CreatePostCommand("title", "content", "author"));

        Post saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("title");
        assertThat(saved.getAuthor()).isEqualTo("author");
    }
}
```

#### 11.2 Persistence — `@DataJpaTest` + `@Import` 로 어댑터 슬라이스

`@DataJpaTest` 는 `JpaRepository` 빈만 등록한다. Output Port 구현체(`PostPersistenceAdapter`)는 `@Import` 로 직접 끼워야 슬라이스 안에서 검증 가능.

```java
@DataJpaTest
@Import(PostPersistenceAdapter.class)
class PostPersistenceAdapterTest {
    @Autowired PostPersistenceAdapter adapter;

    @Test
    @DisplayName("존재하지 않는 ID 삭제 시 row-count 0을 돌려준다")
    void deleteById_returns_zero_when_not_found() {
        assertThat(adapter.deleteById(9_999L)).isZero();   // CommandService 의 not-found 신호 (clean-architecture.md §"CQRS Coupling Boundary")
    }
}
```

#### 11.3 ArchUnit — 본 프로젝트 boundary 규칙

```java
// DoNotIncludeTests — ArchUnit 의 ADR 경계 강제는 *프로덕션 코드 한정*.
// 테스트 코드는 의도적으로 mock·fixture·@SpringBootTest 등 경계 너머를 사용하므로
// (예: application/service 테스트가 Mockito 외 Spring test helper 를 쓰는 경우)
// ArchUnit 강제 대상이 아니다.
@AnalyzeClasses(
    packages = "com.dunowljj.board",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class HexagonalArchitectureTest {
    @ArchTest
    static final ArchRule domain_pure =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "jakarta.persistence..",
                "org.hibernate..", "jakarta.servlet..", "jakarta.validation..",
                "..adapter..", "..application.."
            );

    @ArchTest
    static final ArchRule application_no_adapter =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    // application_spring_narrow — *whitelist*. Spring 은 stereotype·transaction 만,
    // jakarta 는 transaction 만 허용. blacklist 로 구현하면 신규 Spring 의존
    // (context, cache, scheduling, validation 등) 이 통과해 의도와 어긋난다.
    @ArchTest
    static final ArchRule application_spring_narrow =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat(
                resideInAPackage("org.springframework..")
                    .and(not(resideInAnyPackage(
                        "org.springframework.stereotype..",
                        "org.springframework.transaction..")))
                    .or(resideInAPackage("jakarta..")
                        .and(not(resideInAPackage("jakarta.transaction.."))))
            );
    // imports: import com.tngtech.archunit.core.domain.JavaClass;
    //          import static com.tngtech.archunit.base.DescribedPredicate.not;
    //          import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;

    @ArchTest
    static final ArchRule common_error_framework_neutral =
        noClasses().that().resideInAPackage("..common.error..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "jakarta.persistence..",
                "org.hibernate..", "jakarta.servlet..", "jakarta.validation..",
                "..adapter..", "..domain..", "..application.."
            );

    @ArchTest
    static final ArchRule domain_exception_no_http =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework.http..", "org.springframework.web.."
            );

    @ArchTest
    static final ArchRule no_cycles =
        slices().matching("com.dunowljj.board.(*)..").should().beFreeOfCycles();
}
```

## Considered Alternatives

- **모든 테스트를 `@SpringBootTest`로 통일** — 진입 장벽 낮고 빠르게 시작 가능. 그러나 실행 시간이 도메인 수에 따라 선형으로 누적되고, 한 테스트가 여러 계층 실패를 동시에 떠안아 회귀 추적이 흐려진다. 골격 단계에서 채택할 방향이 아님.
- **계층 무관 mock-heavy 단위 테스트** — 모든 의존성을 mock하면 매우 빠르지만 JPA 매핑 오류·HTTP 계약 변경·트랜잭션 경계 같은 *계층 간 결합 결함*은 잡지 못한다. false safety.
- **테스트 전략을 ADR이 아닌 위키/스킬에 두기** — 가벼워 보이지만 ADR이 아닌 문서는 후속 도메인 작성 시 *결정의 무게*가 약해 ad-hoc 변형이 누적된다. 테스트 책임은 시스템 경계 결정이라 ADR이 정합.

## Rejected Suggestions

본 ADR 설계 과정에서 *실제로 제안되었으나 거부된* 안. 외부 리뷰(Codex)와 사용자 피드백 중 의미 있는 분기점만 기록한다.

- **테스트 패턴을 별도 `.claude/skills/test-strategy.md` 로 분리** — 거부. 사유: skill 분리는 ADR/skill 양쪽에 같은 정책이 흩어져 *결정의 단일 소스*를 깨뜨린다. 또 "skills 는 짧아야 한다"는 사용자 운영 원칙과 충돌. 채택: ADR 본문 §11 에 *비자명 패턴 3종(ArgumentCaptor, `@DataJpaTest` + `@Import`, ArchUnit 규칙)* 만 두고, 자명한 예시는 생략.
- **CQRS 테스트에서 fixture·setup 일체 공유 금지** — 거부. 사유: 강한 격리는 실용성을 해친다. 순수 도메인 fixture builder(예: `PostFixtures.aValidPost()`)는 Command/Query 양쪽이 공유해도 결합을 만들지 않는다. 채택: 공유 금지 대상을 *mutable setup, mock stubbing, 테스트 간 상태 의존성* 으로 좁힘 (§2).
- **Coverage "Domain 불변식과 Application 분기는 사실상 100% 커버"** — 거부. 사유: "100%" 표현이 시간이 지나면 숫자 압박으로 변질되어 *의미 없는 테스트 양산* 압력이 된다. 채택: "누락 없이 테스트, 빠진 규칙 우선 검토" 로 약화 (§7).
- **PLAN-0006 범위에 Clock 주입 리팩터링 동반 옵션 포함** — 거부. 사유: 테스트 도입과 도메인 시간 정책 변경을 한 실행 단위에 섞으면 리뷰 초점이 흐려지고 회귀 위험이 누적된다. ADR-0004 §3 의 시간 주입 ADR 후보 슬롯을 살린다. 채택: PLAN-0006 은 *현 `LocalDateTime.now()` 전제* 로 작성, Clock 주입은 별도 ADR/Plan (§5).
- **ArchUnit 도입 시점을 Open Question 으로 열어둠** — 거부. 사유: §1 표·§3 본문이 ArchUnit 을 *결정된 채택 사항* 으로 다루는데 Open Question 에서 시점을 미뤄두면 정책이 흔들린다. 채택: PLAN-0006-A 필수 범위로 close.
- **"외부 계층이 외부 계층에 의존" — `Domain → Application → Adapter`** 표현 (초기 본문 문구) — 거부. 사유: source dependency 방향이 거꾸로 적혀 Clean/Hexagonal 의 핵심을 오역할 수 있고, 면접·리뷰어 신뢰도를 직접 깎는다. 채택: `Adapter → Application → Domain` (source dependencies point inward) 로 정정 (§Context).
- **Driving Adapter 의 nested 타입 import 일반 금지** (초기 §3 표현) — 거부. 사유: nested **Command** record 는 Adapter → Application 단방향 의존이라 무해하고 실제로 `PostController` 가 `CreatePostUseCase.CreatePostCommand` 를 사용한다. 일반 금지는 현 코드와 충돌. 채택: nested **Result** 만 금지, Command 는 허용 (§3).
- **`common.error..` 의 framework 의존만 금지** (초기 §3 표현) — 거부. 사유: shared kernel 이라면 *어느 쪽도 모르는* 자리여야 한다. framework 만 막고 `..domain..`, `..application..` 역방향 의존을 허용하면 shared kernel 정의가 깨진다. 채택: framework + 역방향 양쪽 모두 금지 (§3).
- **Application 의 Spring 의존 허용 여부 미결정** (초기 ArchUnit 규칙에서 빠진 부분) — 거부. 사유: 미결정 상태로 ArchUnit 도입 시 *현재 구조를 우연히 정당화*하거나, 추후 강한 규칙 추가 시 대량 수정이 발생한다. 채택: stereotype·transaction·jakarta.transaction 만 명시 허용, web·data·persistence·servlet 금지 (§3).
- **§1 표의 Mapper 행 — 초기 표현 "Mapper (선택적)"** — 거부. 사유: "선택적" 의 *대상*이 모호하다. 매퍼 자체가 선택인지(틀림 — Driven Adapter 의 당연한 책임), *단독 테스트*가 선택인지 표만 봐서는 판정 불가. 또 Persistence 와 같은 들여쓰기에 두면 *peer 계층* 처럼 읽혀 종속 관계가 흐려진다. 동시에 *행을 아예 제거*하는 안도 거부 — 매퍼의 존재와 테스트 위치가 표에서 사라지면 implementer 가 헤맨다. 채택: Persistence 바로 아래로 이동 + `↳` 마커 + *"(Persistence 종속, 단독 테스트 선택)"* 표기. §2 규칙도 "round-trip 으로 구별 가능한가" 라는 *판정 질문*으로 환원 (§1, §2).

## Consequences

**긍정적 영향**
- 각 계층의 실패가 그 계층 테스트로 *국소화*되어 회귀 원인 추적이 빨라진다.
- Domain·Service 테스트가 Spring을 띄우지 않아 피드백 루프가 짧다(전체 빌드 시간의 대부분이 slice/E2E에 집중).
- ArchUnit 테스트로 ADR-0003 의존성 규칙이 *문서가 아닌 빌드*에서 강제된다.
- 계층 경계가 테스트에서도 명시적이라 신규 도메인 추가 시 테스트 작성 위치가 자명해진다.

**부정적 영향 / 트레이드오프**
- 초기 인프라(slice 설정, 픽스처, ArchUnit, Clock 주입)에 보일러플레이트가 추가된다.
- 4계층 + ArchUnit + E2E 의식이 모두 굴러가야 안전망이 완성됨 — 도입을 한 번에 다 하지 않으면 효과가 약하다.
- ADR-0003 §"Equality Policy"에 따라 Entity의 `equals`/`hashCode`가 미정의라 `assertThat(entity).isEqualTo(...)` 사용 시 식별자 기반 비교가 안 됨 — 테스트는 필드 단위 검증으로 작성해야 한다.

## Open Questions

<!-- ArchUnit 도입 시점은 닫힘: PLAN-0006-A 필수 범위. §1 표·§3 본문·§11.3 예시 일관. -->
- **Testcontainers 전환 시점**: §2에 *목표*로 박혔으므로 도입 여부는 결정됨. 남은 질문은 *언제* — 운영 DB 확정 시점 vs H2 한계가 처음 드러나는 시점 중 빠른 쪽. 전환은 별도 Plan으로.
- **Mutation testing (Pitest 등) 도입**: 현 단계 미고려. 골격이 안정된 이후 별도 ADR.
- **테스트 소스셋 분리** (`integrationTest` 별도 sourceSet): 빌드 시간이 문제가 될 때 별도 ADR.

## Related
- ADR-0003 (Clean/Hexagonal + DDD + CQRS) — 본 ADR의 계층 구분과 의존성 규칙의 출처
- ADR-0004 (단단한 골격 우선) — 후행 비용이 큰 경계를 골격 단계에 결정한다는 원칙
- ADR-0005 (예외/에러 응답 정책) — Web 테스트의 `ProblemDetail` 검증 대상
- PLAN-0006 (예정) — 본 ADR을 구현하는 테스트 인프라 + 기존 Post 도메인 테스트 작성
