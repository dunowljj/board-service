# PLAN-0010: 로컬 dev + test DB 인프라 구현 — Docker + Testcontainers + PostgreSQL/pgvector

ADR-0010 (로컬 dev + test DB 인프라) 의 구현 단위. ADR-0010 이 *의도 (decision)* 만 박고 구체 artifact 좌표 / property key / SQL / 코드 패턴 / lifecycle 등을 본 PLAN 으로 deferred 한 자리들을 *모두 박는다*.

## Goal

ADR-0010 §1/§2/§3/§4 의 *직접 구현* + Open Questions 의 본 PLAN 영역 (Testcontainers 라이프사이클 / 공통 import 전략 / host port) 결정. 핵심:

1. `compose.yaml` 신규 — `pgvector/pgvector:pg16` PostgreSQL-compatible service (env / port / label)
2. `build.gradle` — H2 의존성 제거 + `org.postgresql:postgresql` runtime + `spring-boot-docker-compose` developmentOnly + Testcontainers 3 종 testImplementation + `bootRun` systemProperty
3. `application.yml` — H2 datasource / h2.console 블록 제거
4. `application-local.yml` 신규 — local profile 의 SQL init 3 속성
5. `src/main/resources/db/seed/local-data.sql` 신규 — audit columns 명시한 INSERT
6. `PostgresTestcontainersConfig` (`@TestConfiguration`) 신규 — `@Bean(destroyMethod = "") @ServiceConnection PostgreSQLContainer` (`pgvector/pgvector:pg16`)
7. `@Import(PostgresTestcontainersConfig.class)` 를 3 테스트 자리에 부착: `BoardServiceApplicationTests`, `PostE2EIT`, `PostPersistenceAdapterTest`
8. `ADR-0006` §1 표 / §2 / Open Questions 갱신 — H2 → PostgreSQL via Testcontainers 표현
9. `./gradlew check` BUILD SUCCESSFUL + 모든 기존 테스트 green 유지

## Scope

### Included

#### production code 변경

- **`build.gradle`**:
  - `runtimeOnly 'com.h2database:h2'` 제거
  - `implementation 'org.springframework.boot:spring-boot-h2console'` 제거
  - `runtimeOnly 'org.postgresql:postgresql'` 추가 (Spring Boot 4.0.2 BOM 으로 버전 관리)
  - `developmentOnly 'org.springframework.boot:spring-boot-docker-compose'` 추가
  - `testImplementation 'org.springframework.boot:spring-boot-testcontainers'` 추가
  - `testImplementation 'org.testcontainers:testcontainers-postgresql'` 추가
  - `testImplementation 'org.testcontainers:testcontainers-junit-jupiter'` 추가
  - Testcontainers 버전은 Spring Boot 4.0.2 BOM 선언값과 같은 `2.0.3` 사용 (`org.testcontainers:testcontainers-bom` 명시 import)
  - `tasks.named('bootRun') { systemProperty 'spring.profiles.default', 'local' }` 추가 — *기본 profile* 자리 박음 (ADR-0010 §1). `spring.profiles.default` 채택 사유: `spring.profiles.active` 는 *system property 우선순위로 env / IDE 의 `SPRING_PROFILES_ACTIVE` 를 덮음* → ADR-0010 의 *"IDE / 환경변수는 보조 경로"* 의도와 충돌. `default` 는 *active 가 어디에도 안 박혔을 때만* 적용되어 *override 자연 허용*
- **`compose.yaml`** (repo root, 신규):
  - service `db`: `image: pgvector/pgvector:pg16`
  - `environment`: `POSTGRES_DB=board`, `POSTGRES_USER=board`, `POSTGRES_PASSWORD=board`
  - `ports: ["5432:5432"]` — host port 고정 (dev 친숙성 + 다른 dev DB 충돌은 *현 자리* 부재로 위험 0)
  - `labels: ["org.springframework.boot.service-connection=postgres"]` — pgvector/pgvector 가 *커스텀 이미지명* 이라 Spring Boot Docker Compose 의 image name 매칭 미작동 (ADR-0010 §1)
- **`src/main/resources/application.yml`**:
  - `spring.datasource.url=jdbc:h2:mem:board` 블록 제거
  - `spring.datasource.driver-class-name` 제거
  - `spring.datasource.username` / `password` 제거
  - `spring.h2.console` 블록 제거
  - **`spring.jpa.hibernate.ddl-auto` 도 제거** — 전역 default 부재로 운영 profile 의 schema drop 위험 차단 (ADR-0010 §1 의 *안전 default*)
  - 나머지 (`observability.query.value-allowlist`, `logging.structured`) 유지
- **`src/main/resources/application-local.yml`** (신규):
  - `spring.jpa.hibernate.ddl-auto: create-drop` — dev profile 한정 (전역 default 부재 결정의 짝)
  - `spring.sql.init.mode: always` — PostgreSQL 에서 SQL init 활성화 (default 가 *embedded 한정* 이라 명시 필요)
  - `spring.jpa.defer-datasource-initialization: true` — Hibernate `create-drop` 으로 테이블 생성 *후에* seed 적재
  - `spring.sql.init.data-locations: classpath:db/seed/local-data.sql` — local 전용 경로
- **`src/main/resources/application-prod.yml`** (수정):
  - `spring.jpa.hibernate.ddl-auto: validate` 추가 — entity ↔ schema 일치 검증. 운영 결정 전이라도 *안전 default* (운영 DB 결정 / migration tool 도입은 별도 ADR)
  - 기존 `logging.structured.format.console: ecs` 유지
- **`src/main/resources/db/seed/local-data.sql`** (신규):
  - `INSERT INTO posts (title, body, author, created_at, updated_at) VALUES (...)` 형태
  - audit columns 명시 (`AuditingEntityListener` 는 raw SQL insert 에 작동 안 함)
  - 2~3 sample posts — 수동 시험 흐름 충분, 결정성 있는 fixture

#### test code 변경

- **`src/test/java/com/dunowljj/board/config/PostgresTestcontainersConfig.java`** (신규):
  ```java
  @TestConfiguration(proxyBeanMethods = false)
  public class PostgresTestcontainersConfig {

      static final PostgreSQLContainer CONTAINER =
          new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"));

      static {
          CONTAINER.start();
      }

      @Bean(destroyMethod = "")
      @ServiceConnection
      PostgreSQLContainer postgresContainer() {
          return CONTAINER;
      }
  }
  ```
  - **`public class`** 필수 — `@Import` 가 *cross-package* (e2e / adapter.out.persistence.post 패키지에서 참조). package-private 시 컴파일 실패
  - **Testcontainers 2.x PostgreSQL module 사용** — `org.testcontainers.postgresql.PostgreSQLContainer` 는 `pgvector/pgvector` 를 지원 이미지로 포함하므로 `asCompatibleSubstituteFor("postgres")` 불필요. 구형 `org.testcontainers.containers.PostgreSQLContainer` 는 deprecated 라 사용 금지
  - **컨테이너 라이프사이클** = *static singleton (Gradle test worker JVM 당 1 개)* (ADR-0010 Open Questions 결정 영역).

    *문제*: `@DataJpaTest`, `@SpringBootTest`, `@SpringBootTest + @AutoConfigureMockMvc` 는 *서로 다른 Spring context cache key*. 게다가 `@Import` 의 클래스 조합 (테스트 대상 도메인 / 서비스 / config) 이 다르면 *같은 annotation 조합* 안에서도 cache key 가 갈라짐. `@Bean` *만* 사용하면 *context 마다 컨테이너 신규 생성* → 컨테이너 N 개 (테스트 슬라이스 늘수록 N 증가) → Docker 자원 폭주.

    *해결*: `static` 필드 + `static {}` block — 같은 Gradle test worker JVM 안에서는 *클래스 로드 시점 단 1 회 실행*. `@Bean postgresContainer()` 는 *모든 context 에서 같은 static 인스턴스 반환*. **결과: 같은 test worker JVM 안에서 context N 개 ↔ container 1 개**. 단, `test` / `integrationTest` 는 별도 Gradle `Test` task 라 worker JVM 이 분리될 수 있음 → `./gradlew check` 전체 기준 컨테이너 1 개를 보장하지 않음. `@ServiceConnection` 은 mapped port 등 connection info 를 *같은 컨테이너* 에서 검출.

    *Spring bean destroy 회피*: static 공유 컨테이너를 여러 Spring context 에 bean 으로 등록하므로, 각 context 종료 시 같은 컨테이너를 닫으면 다른 context 가 깨질 수 있음. `@Bean(destroyMethod = "")` 로 Spring 의 bean destroy lifecycle 에서 제외하고, 컨테이너 종료는 Testcontainers/Ryuk 및 JVM 종료 흐름에 맡김.

    *Hibernate delayed drop 회피*: 테스트 context 는 static 컨테이너를 공유하고 Gradle worker JVM shutdown hook 순서가 Spring context close 보다 먼저 올 수 있으므로, 테스트에서는 `HibernatePropertiesCustomizer` 로 `hibernate.hbm2ddl.auto=create` 를 적용한다. main `application.yml` 의 `ddl-auto: create-drop` 은 dev 기본값으로 유지. 테스트 격리는 `@DataJpaTest` rollback 과 E2E cleanup 이 책임진다.

    *남은 비용*: context 수 자체는 *@Import 조합마다 증가* 가능 (startup time / heap). 본 단계 (3 자리) 에서는 *수용 가능*. 향후 확장 시 완화 옵션 (현 PLAN 범위 밖):
      - `abstract IntegrationTestBase` — 공통 `@SpringBootTest + @Import(PostgresConfig.class)` 부착해 자식 테스트 cache key 공유
      - `spring.test.context.cache.maxSize` (default 32) — LRU 한계
      - `@DirtiesContext` 명시적 회피 (현 코드 부재 — 확인 완료)
  - **reuse mode** (`testcontainers.reuse.enable`) 는 *현 단계 미활성* — cross-JVM run 재사용은 *환경 의식* 추가, 본 단계 YAGNI
- **`@Import(PostgresTestcontainersConfig.class)`** 부착 3 자리:
  - `BoardServiceApplicationTests` — `@SpringBootTest` 의 contextLoads 가 datasource 검출 시점에 필요
  - `PostE2EIT` — `@SpringBootTest @AutoConfigureMockMvc` 의 full context
  - `PostPersistenceAdapterTest` — 기존 `@Import({PostPersistenceAdapter.class, TimeConfig.class, TestAuditConfig.class})` 에 `PostgresTestcontainersConfig.class` 추가
- **공통 import 전략** = *직접 `@Import` 부착* (ADR-0010 Open Questions 결정 영역). 사유: (a) 명시성 (어느 테스트가 컨테이너 의존하는지 코드에서 즉시 읽힘), (b) 기존 `PostPersistenceAdapterTest` 의 `@Import` 패턴과 정합, (c) 자리 3 곳뿐이라 *meta-annotation* 추상화 가치 < 직접 명시 가치 (4 자리 이상 늘어나면 그때 meta-annotation 도입 검토)

#### docs 변경

- **`docs/adr/0006-test-strategy.md`** §1 표 / §2 / Open Questions 갱신:
  - line 31 (§1 표 Driven Adapter 행): `H2(임시) → Testcontainers + 운영 DB(목표)` → `PostgreSQL via Testcontainers (ADR-0010)`
  - line 34 (§1 표 End-to-End 행): 동일 정정
  - line 45 (§2): *H2 는 임시 다리, Testcontainers 가 목표* → *PostgreSQL via Testcontainers (ADR-0010 §2). H2 한정 동작 회피 의식은 유지 — 미래 다른 dialect 전환 시 재사용*
  - line 312 (Open Questions "Testcontainers 전환 시점"): *해소 (ADR-0010 + PLAN-0010)* 명시 + Open Question 항목 자체 제거 또는 closed 표시
- **`docs/adr/0010-local-and-test-db-infrastructure.md`** Consequences §부정적 (line 108): *(PLAN-0010 에서 갱신)* → *(PLAN-0010 에서 갱신 완료)* 또는 표현 정리

### Not Included

- **pgvector extension 활성화** (`CREATE EXTENSION IF NOT EXISTS vector`) — ADR-0010 Rejected (YAGNI, 사용자 결정 2026-05-20). vector 컬럼 도입 시점 별도 PLAN
- **운영 DB 결정** — ADR-0010 §5 명시 분리. `application-prod.yml` 은 현 빈 상태 유지 (logging 만)
- **migration tool 도입** (Flyway / Liquibase) — ADR-0010 Open Questions. 운영 시점 별도 결정
- **`@Column(name = "created_at")` 명시 매핑** — *현 Hibernate physical naming strategy 기본값* 으로 `createdAt` → `created_at` 자동. seed SQL 도 이 컬럼명 가정. *명시 매핑 도입* 은 본 PLAN 범위 밖 — 본 PLAN 의 *Risks* 에 *naming strategy 변경 시 seed 갱신 의식* 명시
- **Testcontainers reuse mode** (`~/.testcontainers.properties` 의 `testcontainers.reuse.enable=true`) — 현 단계 미도입. 첫 시작 비용 (~10초 pull + start) 완화 가치 있지만 *개발자 환경 의식* 추가 — 도입 시 별도 PLAN
- **CI workflow 추가** — `.github/workflows` 부재 확인 완료. CI 도입은 별도 PLAN
- **h2-console 대체 admin tool** (psql CLI / pgAdmin / DBeaver) — README prerequisites 안내 추가 정도, 도구 채택 결정은 별도

## Non-goals

- 운영 인프라 결정.
- 도메인 / Application Service / Web Adapter 코드 변경 (datasource 외).
- 신규 도메인 기능.
- ArchUnit 규칙 추가 / 변경 — `TestStrategyArchitectureTest` 의 `springboottest_is_localized` 규칙이 *현 자리 3 곳* 모두 정합 (변경 불요).

## Related ADRs

- **ADR-0010** (로컬 dev + test DB 인프라) — 본 PLAN 권위. §1/§2/§3/§4 직접 구현 + Open Questions 의 일부 결정 (라이프사이클 / import 전략 / host port).
- **ADR-0006** (테스트 전략) §1 표 / §2 / Open Questions "Testcontainers 전환 시점" — 본 PLAN 이 H2 → PostgreSQL via Testcontainers 전환 시점 완료. 본 PLAN 에서 갱신.
- **ADR-0008** (감사 데이터 정책) — `AuditingEntityListener` 가 raw SQL insert 에 작동 안 함 → seed DML 의 audit columns 명시 의식 근거.
- **ADR-0004** (골격 단계 정책) — *후행 비용이 큰 자리 우선 박기* 정신.

## Files to Inspect

- `docs/adr/0010-local-and-test-db-infrastructure.md` — 본 PLAN 권위.
- `docs/adr/0006-test-strategy.md` §1 표 (line 31, 34) / §2 (line 45) / Open Questions (line 312) — 본 PLAN 에서 갱신 대상.
- `build.gradle` — 의존성 / `tasks.named('bootRun')` 또는 신규 추가 자리 확인.
- `src/main/resources/application.yml` — H2 블록 제거 대상.
- `src/main/resources/application-prod.yml` — 현 빈 상태 확인 (logging 만), 본 PLAN 범위 밖.
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostJpaEntity.java` — 컬럼 명세 (audit columns 의 NOT NULL 제약 확인 완료).
- `src/test/java/com/dunowljj/board/BoardServiceApplicationTests.java` — `@SpringBootTest` 자리.
- `src/test/java/com/dunowljj/board/e2e/PostE2EIT.java` — `@SpringBootTest @AutoConfigureMockMvc` 자리.
- `src/test/java/com/dunowljj/board/adapter/out/persistence/post/PostPersistenceAdapterTest.java` — `@DataJpaTest` 자리 + 기존 `@Import` 패턴.
- `src/test/java/com/dunowljj/board/IntegrationSmokeTest.java` — `@Tag("integration")` 만, Spring 안 띄움 → 본 PLAN 영향 없음.
- `src/test/java/com/dunowljj/board/architecture/TestStrategyArchitectureTest.java` — `springboottest_is_localized` 규칙 (`..e2e..` 또는 `BoardServiceApplicationTests`) — 본 PLAN 의 신규 `@TestConfiguration` 위치 (`..config..`) 영향 없음.

## Files to Touch

신규 (production):
- `compose.yaml`
- `src/main/resources/application-local.yml`
- `src/main/resources/db/seed/local-data.sql`

신규 (test):
- `src/test/java/com/dunowljj/board/config/PostgresTestcontainersConfig.java`

수정 (production):
- `build.gradle`
- `src/main/resources/application.yml`
- `src/main/resources/application-prod.yml`

수정 (test):
- `src/test/java/com/dunowljj/board/BoardServiceApplicationTests.java` — `@Import` 추가
- `src/test/java/com/dunowljj/board/e2e/PostE2EIT.java` — `@Import` 추가
- `src/test/java/com/dunowljj/board/adapter/out/persistence/post/PostPersistenceAdapterTest.java` — 기존 `@Import` 배열에 추가

수정 (docs):
- `docs/adr/0006-test-strategy.md` — §1 표 (line 31, 34) / §2 (line 45) / Open Questions (line 312)

삭제: 없음.

## Implementation Steps

순서는 *의존성 + 빌드 통과 단계* 기준. 본 PR 은 *단일 commit* 머지 (선례 PLAN-0006-C/D, PLAN-0007/0008/0009).

1. **`build.gradle` 의존성 갱신** — H2 의존성 2 종 제거 + PostgreSQL JDBC driver + spring-boot-docker-compose + Testcontainers 3 종 + `bootRun` systemProperty. 이 단계 단독 빌드는 *application.yml 의 H2 url 잔존* 으로 컨테이너 없으면 startup 실패 — 다음 단계까지 일괄.

2. **`compose.yaml` 신규** — pgvector service (image / env / ports / labels). repo root 에 배치 (Spring Boot Docker Compose 의 default lookup 자리).

3. **`application.yml` 의 H2 블록 제거** — datasource / h2.console 제거. 나머지 보존.

4. **`application-local.yml` 신규** — SQL init 3 속성 + (local 전용 datasource override 가 *필요 없음* — compose 가 `ConnectionDetails` 자동 제공).

5. **`db/seed/local-data.sql` 신규** — `posts` 테이블 INSERT 2~3 건, audit columns 명시.
   ```sql
   INSERT INTO posts (title, body, author, created_at, updated_at)
   VALUES ('환영합니다', '첫 번째 게시글입니다.', '관리자', NOW(), NOW());
   INSERT INTO posts (title, body, author, created_at, updated_at)
   VALUES ('두 번째 글', '내용 예시입니다.', '관리자', NOW(), NOW());
   ```

6. **`PostgresTestcontainersConfig` 신규** (`src/test/java/com/dunowljj/board/config/`):
   - `@TestConfiguration(proxyBeanMethods = false)`
   - `@Bean(destroyMethod = "") @ServiceConnection PostgreSQLContainer postgresContainer()`
   - `@Bean HibernatePropertiesCustomizer testHibernateDdlAutoCustomizer()` 로 test context 에서 `hibernate.hbm2ddl.auto=create` 적용
   - `new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"))`

7. **3 테스트 자리에 `@Import(PostgresTestcontainersConfig.class)` 부착**:
   - `BoardServiceApplicationTests` — `@Import` 단독 추가
   - `PostE2EIT` — `@Import` 단독 추가
   - `PostPersistenceAdapterTest` — 기존 `@Import({PostPersistenceAdapter.class, TimeConfig.class, TestAuditConfig.class})` 배열에 추가

8. **ADR-0006 갱신** — §1 표 (line 31, 34) / §2 (line 45) / Open Questions "Testcontainers 전환 시점" (line 312) 의 H2 표현 정정.

9. **`./gradlew check` 통과 확인** — `test` (Domain unit + ArchUnit + slice) + `integrationTest` (E2E + smoke) 모두 green. 첫 실행 시 컨테이너 pull (~10초). Docker daemon 실행 중 필요.

10. **(선택) `./gradlew bootRun` 으로 dev 환경 수동 확인** — local profile 자동 활성 + compose container 자동 up + seed 적재 + `GET /api/posts` 호출 시 seed 데이터 반환 확인.

## Acceptance Criteria

- `build.gradle` 에서 H2 의존성 2 종 부재, `org.postgresql:postgresql` runtime / `spring-boot-docker-compose` developmentOnly / Testcontainers 3 종 testImplementation 존재.
- `bootRun` task 가 `systemProperty 'spring.profiles.default', 'local'` 보유.
- `compose.yaml` 이 repo root 에 존재, pgvector/pgvector:pg16 image + 필수 env + ports + service-connection label 보유.
- `application.yml` 에 H2 datasource / h2.console / `ddl-auto` 블록 모두 부재 (전역 default 부재 — 운영 profile 의 schema drop 위험 차단).
- `application-local.yml` 가 4 속성 (`spring.jpa.hibernate.ddl-auto=create-drop`, `spring.sql.init.mode=always`, `spring.jpa.defer-datasource-initialization=true`, `spring.sql.init.data-locations=classpath:db/seed/local-data.sql`) 보유.
- `application-prod.yml` 가 `spring.jpa.hibernate.ddl-auto: validate` 보유 (안전 default).
- `db/seed/local-data.sql` 가 `posts` 테이블 INSERT 보유, audit columns 명시.
- `PostgresTestcontainersConfig` 가 `@TestConfiguration` + `@Bean(destroyMethod = "")` + `@ServiceConnection` + Testcontainers 2.x `PostgreSQLContainer` (`pgvector/pgvector:pg16`) + test 전용 `HibernatePropertiesCustomizer` 보유.
- 3 테스트 자리에 `@Import(PostgresTestcontainersConfig.class)` 부착 확인.
- `ADR-0006` 의 H2 4 자리 (line 31 / 34 / 45 / 312) 갱신 완료.
- `./gradlew check` BUILD SUCCESSFUL — 모든 기존 테스트 green.
- (선택) `./gradlew bootRun` 시 dev 환경 자동 완성 (compose up + local seed 적재 + `/api/posts` 응답에 seed 데이터 포함).
- ArchUnit 규칙 영향 없음 (현 11 규칙 통과 유지).

## ADR Required

**no** — ADR-0010 가 권위. 본 PLAN 은 §1/§2/§3/§4 의 *직접 구현* + Open Questions 의 *PLAN 영역 결정* (라이프사이클 / import 전략 / host port). 새 시스템 결정 0.

본 PLAN 에서 박는 *PLAN 영역 결정* (ADR 가 deferred 한 자리):
- **Testcontainers 라이프사이클** = *static singleton (Gradle test worker JVM 당 1 개)*. `@DataJpaTest` / `@SpringBootTest` / `@SpringBootTest + @AutoConfigureMockMvc` 가 서로 다른 context cache key 라 `@Bean` 만으로는 컨테이너 3 개 — `static` 필드 + `static {}` block 으로 *같은 worker JVM 안에서 클래스 로드 시점 단 1 회 기동* 강제. `test` / `integrationTest` 는 별도 `Test` task 라 `./gradlew check` 전체에서 컨테이너 1 개를 보장하지 않음. reuse mode 미활성.
- **공통 import 전략** = 직접 `@Import` 부착 (3 자리 — meta-annotation 가치 < 명시성 가치).
- **host port** = `5432:5432` 고정 (dev 친숙성, 현 dev DB 충돌 부재).
- **dev profile 활성화** = `bootRun` 에 `spring.profiles.default=local` 주입 (active 가 아닌 default — env / IDE 의 `SPRING_PROFILES_ACTIVE` override 자연 허용).

## Risks

1. **Spring Boot 4.0.2 BOM 의 Testcontainers 버전 호환성** — `spring-boot-testcontainers` / `org.testcontainers:testcontainers-postgresql` / `org.testcontainers:testcontainers-junit-jupiter` 좌표가 BOM 으로 버전 관리되는지 확인 필요. 확인 결과 Spring Boot 4.0.2 BOM 은 Testcontainers `2.0.3` 을 선언하지만, 현 Gradle dependency-management 구성에서는 versionless `org.testcontainers:*` 좌표에 중첩 BOM 이 전파되지 않음 — 같은 버전의 `org.testcontainers:testcontainers-bom:2.0.3` 을 명시 import.
2. **`pgvector/pgvector:pg16` 의 Testcontainers PostgreSQL module 동작** — Testcontainers 2.x 의 `org.testcontainers.postgresql.PostgreSQLContainer` 가 `pgvector/pgvector` 를 지원 이미지로 포함. 실패 시 *명시적 env / waitStrategy* 추가 필요.
3. **`spring-boot-docker-compose` 의 `ConnectionDetails` 자동 검출 실패** — service-connection label (`org.springframework.boot.service-connection=postgres`) 명시했지만 *실제 매칭 동작* 은 Spring Boot 4.0.2 의 구체 구현 의존. 실패 시 *명시적 connection properties* fallback 필요.
4. **`spring.sql.init.mode=always` + `defer-datasource-initialization=true` 의 작동 순서** — Hibernate `create-drop` → schema 생성 → seed 적재 순서가 *Spring Boot 4.0.2* 에서도 정합한지 확인. 실패 시 *seed 가 테이블 생성 전 실행* → NPE / FK 위반.
5. **Hibernate physical naming strategy 기본값** — `createdAt` → `created_at` 변환이 *기본 strategy* (`SpringPhysicalNamingStrategy`) 기준. seed SQL 도 이 컬럼명 가정. *명시적 `@Column(name=...)` 도입* 시 seed 갱신 의식.
6. **dev 시점 Docker daemon 부재** — 사용자 환경에 Docker (Desktop / Colima / Rancher Desktop) 미설치 시 `bootRun` 실패. README 의 prerequisites 갱신은 *후속 작업* — 본 PLAN 범위 밖이지만 *PR description 에 명시* 권장.
7. **첫 컨테이너 pull 비용** — pgvector/pgvector:pg16 multi-arch image 첫 다운로드 ~수십 MB + ~10초. *첫 `./gradlew check`* 시 시간 증가. 이후 캐시.
8. **port 5432 충돌** — 사용자 환경에 *이미 PostgreSQL 5432 점유* 시 compose up 실패. *현 자리 부재* 가정이라 dynamic port 미채택 — 충돌 시 PLAN 의 host port 결정 재검토.
9. **static 공유 컨테이너 + `ddl-auto: create-drop` 의 schema lifecycle** — 같은 worker JVM 안의 여러 Spring context 가 *동일 DB 인스턴스* 를 공유한다. 병렬 테스트 실행, `@DirtiesContext`, context cache eviction 이 섞이면 한 context 의 schema drop 이 다른 context 에 영향을 줄 수 있음. 현 단계는 Gradle 기본 직렬 실행 + `@DirtiesContext` 부재 + context 수 3 개 전제로 수용. 병렬화 도입 시 schema 분리 / container 분리 / migration 기반 초기화 재검토.

### Pre-resolved

- **`IntegrationSmokeTest` 의 Testcontainers 영향** — `@Tag("integration")` 만 보유, Spring 컨텍스트 / DB 미사용 → 본 PLAN 영향 0 (확인 완료).
- **ArchUnit 규칙 영향** — `springboottest_is_localized` 규칙이 `..e2e..` 또는 `BoardServiceApplicationTests` 한정. 본 PLAN 의 신규 `PostgresTestcontainersConfig` 는 `..config..` 패키지 (`@SpringBootTest` 미보유) → 영향 0 (확인 완료).
- **CI workflow 영향** — `.github/workflows` 부재 확인 완료. CI 도입은 별도 PLAN.
- **`application-prod.yml` 영향** — 현 빈 상태 (logging 만). 본 PLAN 범위 밖, 유지.

## Implementation Hints

### `compose.yaml`

```yaml
services:
  db:
    image: 'pgvector/pgvector:pg16'
    environment:
      POSTGRES_DB: board
      POSTGRES_USER: board
      POSTGRES_PASSWORD: board
    ports:
      - '5432:5432'
    labels:
      - 'org.springframework.boot.service-connection=postgres'
```

### `build.gradle` 변경 (개념적 diff)

```groovy
ext {
    testcontainersVersion = '2.0.3'
}

dependencyManagement {
    imports {
        mavenBom "org.testcontainers:testcontainers-bom:${testcontainersVersion}"
    }
}

dependencies {
    // 제거
    // implementation 'org.springframework.boot:spring-boot-h2console'
    // runtimeOnly 'com.h2database:h2'

    // 추가
    runtimeOnly 'org.postgresql:postgresql'
    developmentOnly 'org.springframework.boot:spring-boot-docker-compose'

    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:testcontainers-postgresql'
    testImplementation 'org.testcontainers:testcontainers-junit-jupiter'
}

tasks.named('bootRun') {
    systemProperty 'spring.profiles.default', 'local'
}
```

### `application-local.yml`

```yaml
spring:
  sql:
    init:
      mode: always
      data-locations: classpath:db/seed/local-data.sql
  jpa:
    hibernate:
      ddl-auto: create-drop
    defer-datasource-initialization: true
```

### `application-prod.yml` (수정)

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate

logging:
  structured:
    format:
      console: ecs
```

### `db/seed/local-data.sql`

```sql
INSERT INTO posts (title, body, author, created_at, updated_at)
VALUES ('환영합니다', '첫 번째 게시글입니다.', '관리자', NOW(), NOW());

INSERT INTO posts (title, body, author, created_at, updated_at)
VALUES ('두 번째 글', '내용 예시입니다.', '관리자', NOW(), NOW());
```

### `PostgresTestcontainersConfig.java`

```java
package com.dunowljj.board.config;

import org.hibernate.cfg.SchemaToolingSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfig {

    static final PostgreSQLContainer CONTAINER =
        new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"));

    static {
        CONTAINER.start();
    }

    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return CONTAINER;
    }

    @Bean
    HibernatePropertiesCustomizer testHibernateDdlAutoCustomizer() {
        return properties -> properties.put(SchemaToolingSettings.HBM2DDL_AUTO, "create");
    }
}
```

### `PostPersistenceAdapterTest` `@Import` 갱신

```java
@DataJpaTest
@Import({PostPersistenceAdapter.class, TimeConfig.class, TestAuditConfig.class, PostgresTestcontainersConfig.class})
class PostPersistenceAdapterTest {
    // ...
}
```

### ADR-0006 §1 표 갱신 (line 31 / 34)

```markdown
| Driven Adapter (Persistence) | ... | `@DataJpaTest` | slice | PostgreSQL via Testcontainers (ADR-0010) |
| End-to-End | ... | `@SpringBootTest` + MockMvc | full | PostgreSQL via Testcontainers (ADR-0010) |
```

## Execution Notes

<!-- 실행 중 비자명한 결정만 시간순 append. 사소한 구현 디테일은 적지 않는다. -->

- 2026-05-20: Risk #1 확인 — Spring Boot 4.0.2 BOM 이 Testcontainers `2.0.3` 을 선언하지만, 현 Gradle dependency-management 구성에서는 중첩 BOM 이 versionless `org.testcontainers:*` 좌표에 전파되지 않아 `org.testcontainers:testcontainers-bom:2.0.3` 명시 import 필요. 최초 구현의 `1.20.4` 는 Boot 선언값과 불일치라 `2.0.3` 으로 정정. Testcontainers 2.x artifact 는 `testcontainers-postgresql` / `testcontainers-junit-jupiter`, container class 는 deprecated 된 `org.testcontainers.containers.PostgreSQLContainer` 대신 `org.testcontainers.postgresql.PostgreSQLContainer` 사용.
- 2026-05-20: shutdown 시점 `HHH000478: Unsuccessful: drop table if exists posts cascade` 노이즈 관측. Risk #9 예상 증상 — *static 공유 container + create-drop + multi-context shutdown order* 조합. test 전용 `HibernatePropertiesCustomizer` 로 `hibernate.hbm2ddl.auto=create` 적용해 delayed drop 제거. main `application.yml` 의 dev `ddl-auto: create-drop` 은 유지.
- 2026-05-20: `./gradlew check --rerun-tasks` BUILD SUCCESSFUL. Testcontainers 2.0.3 전환 후 deprecated API compile warning 없음, shutdown drop 오류 없음.
- 2026-05-20: `./gradlew bootRun` 확인 — default profile `local`, Spring Boot Docker Compose 로 `board-service-db-1` 기동, `/api/posts` 응답에 seed 2 건 반환. 종료 후 `docker compose ps` 에 running container 없음.
- 2026-05-20: Codex P1 명중 — `application.yml` 의 `ddl-auto: create-drop` 가 *전역 default* 라 운영 profile 로 띄울 때 *운영 schema drop 위험* 잠재. 해결: `application.yml` 에서 `ddl-auto` 자체 제거 (전역 default 부재), `application-local.yml` 에 `create-drop` 명시 (dev 한정), `application-prod.yml` 에 `validate` 명시 (안전 default). test 는 이미 `HibernatePropertiesCustomizer` 로 `create` override 하므로 영향 0. Decision §1 + Consequences 와 Acceptance Criteria 동기화.
