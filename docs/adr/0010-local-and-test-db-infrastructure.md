# ADR-0010: 로컬 개발 / 테스트용 DB 인프라 — Docker + Testcontainers + PostgreSQL/pgvector

## Status
Proposed

## Date
2026-05-19

## Context

현 시점 (2026-05-19) 의 DB 인프라:

- dev / test 모두 **H2 in-memory** (`jdbc:h2:mem:board`, `ddl-auto: create-drop`)
- `runtimeOnly 'com.h2database:h2'` + `spring-boot-h2console` 의존성

ADR-0006 (테스트 전략) §1 표 / §2 가 이미 *"H2 (임시) → Testcontainers + 운영 DB (목표)"* 로 박았고, Open Questions §"Testcontainers 전환 시점" 가 결정 의제로 남아 있다 — *운영 DB 확정 시점* vs *H2 한계 처음 드러나는 시점* 중 빠른 쪽.

이 자리에서 다음 부담이 누적된다.

- **운영 DB 미결정** — 운영 인프라 결정이 다른 의제 (보안 / 비용 / 운영 방식) 와 묶여 *현 단계에서 결정하기 어렵다*. 운영 결정을 기다리는 동안 H2 가 지속되면 *vector 검색 미래 시도* 같은 의제가 dev/test 에서 검증 불가.
- **vector 검색 미래 시도** — H2 는 vector 검색을 지원하지 않는다. PostgreSQL + pgvector 가 *단일 store 에서 vector + 관계형* 을 다루는 가장 성숙한 조합.
- **dev 와 test 의 인프라 분리 부재** — 현 H2 는 dev 와 test 가 같은 인스턴스에 묶여 *test 격리 가치* 가 흐려진다. 본 단계는 *현 dev 데이터 장기 보존 의제 부재* — dev 도 매 실행마다 *결정성 있는 seed* 로 초기화하는 흐름이 더 자연.
- **ADR-0006 §"Testcontainers 전환 시점"** — 운영 결정 전에도 *H2 한계가 드러나는 시점* (vector 의제) 로 전환 정당화 가능.

본 ADR 은 **운영 DB 결정 전까지 잠정 dev/test 인프라만 박는다**. 운영 DB 결정은 별도 ADR 시점.

## Decision

### 1. 로컬 dev DB — Docker 자동 흐름

- repo root 의 `compose.yaml` 에 `pgvector/pgvector:pg16` 기반 PostgreSQL-compatible service 정의
  - DB name / user / password 는 명시한다 — Spring Boot connection wiring 과 local seed 가 같은 DB 를 안정적으로 가리키게 하기 위함
  - container port `5432` 는 host 에 매핑한다 — Spring Boot Docker Compose 의 connection wiring 이 *mapped port 기준*. host port 고정 vs dynamic 매핑은 PLAN-0010 에서 결정
- **Spring Boot service-connection label 명시** — `pgvector/pgvector` 가 *커스텀 이미지명* 이라 Spring Boot Docker Compose 의 기본 이미지명 매칭 (`postgres` prefix) 이 작동하지 않음. compose service 에 `labels: ["org.springframework.boot.service-connection=postgres"]` 박아 PostgreSQL 로 자동 인식
- `developmentOnly 'org.springframework.boot:spring-boot-docker-compose'` 의존성 — `./gradlew bootRun` 시 *자동 `docker compose up`* + `ConnectionDetails` 자동 제공 (compose service 의 image name / labels 로 service type 판정 후 connection 정보 빈 등록). `@ServiceConnection` 어노테이션은 *Testcontainers 쪽* 의 메커니즘으로 본 항목과 무관 — §2 참조
- **앱 종료 시 자동 `docker compose stop`** (Spring Boot Docker Compose 기본 동작) — 다음 `bootRun` 에서 *같은 컨테이너 재사용*. `down` 은 *컨테이너 / 네트워크 제거* (볼륨은 `-v` 옵션 시) 라 재사용 측면에서 의도와 어긋남 — Spring Boot Docker Compose 기본 lifecycle 정합
- `application.yml` 의 H2 datasource 블록 제거 — compose container 에서 connection info 자동 검출
- **dev `ddl-auto: create-drop` + local 전용 seed 데이터 초기화** — dev 데이터 장기 보존 의제 부재. `application-local.yml` 이 PostgreSQL SQL init 활성화, Hibernate schema 생성 이후 seed 적재 순서, local 전용 seed 경로를 책임진다. `src/main/resources/data.sql` 같은 *기본 경로* 사용 금지 — 테스트 DB 가 의도치 않게 seed 되는 위험 회피
- **seed DML 은 audit columns 를 명시한다** — JPA `AuditingEntityListener` (ADR-0008) 는 *raw SQL insert* 에 작동하지 않음. `PostJpaEntity` 의 `createdAt` / `updatedAt` 은 `nullable = false` 라 seed insert 가 *값 미명시* 시 NULL 제약 위반으로 실패. 정확한 SQL 과 컬럼명 고정 방식은 PLAN-0010 에서 결정
- **local profile 활성화 — 기본은 Gradle `bootRun` 주입** — `./gradlew bootRun` 이 *dev 환경 완성* 결정 정합. IDE run config / 환경변수 `SPRING_PROFILES_ACTIVE` 는 보조 경로
- *테스트는 seed 자동 주입 없음* — 각 테스트가 fixture 를 직접 만들어 격리 보장
- *수동 `docker compose up`* 경로 거부 — *임시 인프라* 의도와 자동 흐름 정합 (의식 비용 0). 디버깅 필요 시 `docker compose ps` / `docker logs` 명시적 가능

### 2. 테스트 DB — Testcontainers + `@ServiceConnection`

- 의존성 구성 (구체 artifact 좌표는 PLAN-0010 에서 Spring Boot 4.0.2 BOM 기준으로 확정):
  - Spring Boot 의 Testcontainers 통합 (`spring-boot-testcontainers`)
  - Testcontainers PostgreSQL module
  - Testcontainers JUnit Jupiter integration
- `@TestConfiguration` 의 `PostgreSQLContainer<?>` bean + `@ServiceConnection` 어노테이션 — datasource 자동 wiring
- **공통 import 전략 필요** — top-level `@TestConfiguration` 은 *auto component scan 대상 아님*. PLAN-0010 은 `@DataJpaTest` slice 와 `@SpringBootTest` integration 테스트 양쪽에 같은 Testcontainers config 를 적용하는 방식을 정한다
- 적용 대상: **`@DataJpaTest` (Persistence slice)** + **모든 `@SpringBootTest` integration 테스트** (E2E + `BoardServiceApplicationTests.contextLoads` 포함) — H2 제거 후 *datasource 가 필요한 모든 자리* 전환. ADR-0006 §1 표의 *H2 임시* 자리 모두 해소
- 컨테이너 라이프사이클 (singleton vs per-class vs per-method) 결정은 *별도 PLAN* — 첫 시작 비용 완화 (~10초 pull + start) vs 격리 trade-off

### 3. DB 이미지 — `pgvector/pgvector:pg16`

- PostgreSQL 16 + pgvector extension 일체형 (multi-arch — amd64 + arm64 — Mac M-series 정합)
- 사유:
  - vector 검색 미래 시도 정합 — *준비된 인프라* (vector 컬럼 도입은 본 ADR 범위 밖, 별도 PLAN)
  - PostgreSQL 자체가 *운영 DB 후보 1순위* — 미래 운영 결정이 PostgreSQL 계열이면 이미지만 교체
  - 운영 DB 결정 *강제* 아님 — 미래 운영이 PostgreSQL 외 선택 시 본 ADR superseded

### 4. H2 완전 제거 + PostgreSQL JDBC driver 추가

- `build.gradle`:
  - `runtimeOnly 'com.h2database:h2'` 제거
  - `implementation 'org.springframework.boot:spring-boot-h2console'` 제거 (h2-console UI 사라짐 — 운영 결정 시점에 별도 admin tool 결정)
  - **`runtimeOnly 'org.postgresql:postgresql'` 추가** — `spring-boot-docker-compose` / Testcontainers 모두 *connection details* 만 제공하고 *JDBC driver* 는 제공하지 않음. 본 의존성이 없으면 애플리케이션 / 테스트 실행 시 datasource 생성 실패 (런타임 — 빌드 시점 아님)
- `src/main/resources/application.yml` 의 H2 datasource / h2.console 블록 제거
- *부분 유지* (예: archunit 한정) 거부 — unit / archunit 은 DB 무관, slice / integration 이 모두 Testcontainers → 잔존 가치 0

### 5. 운영 DB 결정 — 명시적 분리

본 ADR 은 *현 시점 dev/test 인프라* 만 결정. 운영 DB 결정 자리:

- **시기**: 운영 인프라 의제 진입 시점 (별도 ADR)
- **영향**: 운영 DB 가 PostgreSQL 계열이면 *이미지 + connection 정보만 교체*, 그 외 선택 시 본 ADR superseded
- **시사 vs 강제**: 본 결정의 PostgreSQL+pgvector 채택이 운영 결정을 *시사* 는 하지만 *강제* 는 아님

## Considered Alternatives

- **현 상태 유지 (H2)** — 거부 사유: ADR-0006 §"Testcontainers 전환 시점" 의 *H2 한계 처음 드러나는 시점* 이 vector 의제 진입 시점에 가까움. 운영 결정 후 *test 재구성 부담* 누적 회피.
- **수동 `docker compose up`** — 거부 사유: 의식 비용. 자동 흐름이 *임시 인프라* 의도와 정합. 디버깅이 필요한 자리는 `docker compose ps` / `docker logs` 로 *명시적 확인* 가능.
- **Embedded Postgres** (`zonky-test-embedded-postgres-binaries`) — 거부 사유: macOS arm64 호환성 이슈 + 활발 개발 정지 방향 + Testcontainers 만큼 *prod-equiv* 보장 못 함 (custom binary 경로).
- **Cloud ephemeral DB** (Neon branch / Supabase preview) — 거부 사유: 로컬 격리 안 됨 + CI 비용 + 외부 네트워크 의존성 (실패 시 test red).
- **MySQL / MariaDB / 다른 RDB** — 거부 사유: vector 검색 정합 약함 (MySQL 9.x 의 vector 는 신생). *단일 store 에서 vector* 답으로 PostgreSQL+pgvector 가 가장 성숙.
- **Dedicated vector DB** (qdrant / milvus / weaviate / chroma) — 거부 사유: bulletin board 규모에 *별도 인프라 과잉*. cross-store sync 부담. 단일 store 에서 시작 → 규모 확장 시 분리 의제 (별도 ADR).
- **MongoDB Atlas Vector Search** — 거부 사유: managed 한정 + lock-in + 로컬 dev/test 인프라 의도와 마찰.

## Rejected Suggestions

본 ADR 설계 과정에서 *실제로 제안되었으나 거부된* 안.

- **DB 선택 (PostgreSQL+pgvector) 과 테스트 인프라 (Testcontainers) 를 분리 ADR (ADR-0010 + ADR-0011)** — 거부. 사용자 결정 (2026-05-19): *단일 ADR 통합*. 두 결정이 *같은 미래 의도* (vector 검증 prod-equiv) 를 지향, 결정 응집도 한 자리.
- **본 ADR 에 운영 DB 결정 포함** — 거부. 사용자 결정 (2026-05-19) — *"운영환경은 아직 결정하지 않았기 때문에, 로컬에서 docker를 사용해 db를 띄울거야"*. 운영 결정은 다른 의제 (보안 / 비용 / 운영 방식) 와 묶여 *현 단계에서 분리*.
- **H2 일부 유지** (unit test 또는 archunit 한정) — 거부. 사용자 결정 (2026-05-19): *완전 제거*. unit / archunit 은 DB 무관, slice / integration 이 모두 Testcontainers — 잔존 가치 0.
- **본 ADR/PLAN-0010 에 pgvector extension 자동 활성화 (`CREATE EXTENSION IF NOT EXISTS vector`) 포함** — 거부. 사용자 결정 (2026-05-20): *현재 vector 관련 기능이 필요 없으니, 도입할 때 추가*. extension 활성화는 *준비된 인프라* 가치 있지만 *YAGNI* — vector 의제 진입 시점에 extension 활성화 + JPA mapping 묶음으로 결정 (Open Questions §"pgvector extension 활성화 + JPA mapping").
- **수동 `docker compose up` + `application.yml` 의 explicit datasource url** — 거부. 자동 흐름의 의식 비용 0 가치가 *명시성* 가치보다 큼 (임시 인프라 의도).

## Consequences

**긍정적 영향**

- dev / test dialect 가 *PostgreSQL* 로 통일 — vector 미래 시도가 *Testcontainers 위에서* prod-equiv 검증 가능
- 운영 결정이 PostgreSQL 계열이면 *이미지 + connection 정보만 교체* — application 코드 변경 0
- `compose.yaml` 이 *환경 정의 자체* — 신규 개발자 setup 의식 ↓ (Docker daemon 만 있으면 `./gradlew bootRun` 으로 dev 환경 완성)
- ADR-0006 §"Testcontainers 전환 시점" Open Question 해소
- ADR-0006 §1 표의 *H2 (임시)* 자리 → *PostgreSQL via Testcontainers* 로 명시 (PLAN-0010 에서 갱신)

**부정적 영향 / 트레이드오프**

- **Docker daemon 의존성** — Mac / Linux / Windows 신규 개발자 환경 setup 추가. 보강: README prerequisites 갱신 (Docker Desktop / Colima / Rancher Desktop 중 하나 권장)
- **첫 test 실행 컨테이너 pull** (~10초) — reuse mode 또는 GitHub Actions docker layer cache 로 완화 (별도 PLAN 결정)
- **운영 DB 가 *최종 결정 시 PostgreSQL 외* 선택 시** — 본 ADR superseded + dev/test 재구성. 단 *PostgreSQL+pgvector 채택* 자체가 운영 후보로 시사 강함
- **h2-console UI 사라짐** — dev 시점 데이터 확인 도구 부재. 운영 결정 시점에 별도 admin tool 결정 (psql CLI / pgAdmin / DBeaver 등)
- **`hibernate.ddl-auto: create-drop` 유지 (dev + test schema 전략 통일)** — dev 는 local profile seed 로 시작 상태를 고정하고, test 는 seed 없이 테스트별 fixture 로 격리. *데이터 장기 보존 부재* 명시. migration tool (Flyway / Liquibase) 도입은 운영 시점 별도 결정

## Open Questions

- **운영 DB 선택** — 별도 ADR 시점. 본 ADR 의 PostgreSQL+pgvector 채택이 시사 강함이지만 강제 아님.
- **DB migration tool** (Flyway / Liquibase / `ddl-auto` 유지) — 현재 `create-drop`. 운영 시점 별도 결정.
- **pgvector extension 활성화 + JPA mapping** — 본 ADR 범위 밖. pgvector 이미지에 *extension 설치만* 되어 있고 *DB 에 활성화* (`CREATE EXTENSION IF NOT EXISTS vector`) 는 미실행 상태. vector 컬럼 도입 시점 별도 PLAN 에서 *extension 활성화 + JPA mapping* 묶음 결정 — migration / init strategy (Flyway / Liquibase / `ddl-auto` 등) 와 함께 결정.
- **Testcontainers 컨테이너 라이프사이클** (singleton / per-class / per-method, reuse mode 활성화 여부) — 첫 시작 비용 완화 vs 격리 trade-off — PLAN-0010 단계 결정.
- **CI Docker daemon 보장** — GitHub Actions `ubuntu-latest` 는 docker 기본 제공. 다른 CI runner 도입 시 영향.

## Related

- **ADR-0006 (테스트 전략)** §1 표 (Driven Adapter / End-to-End 행) / §2 / §"Testcontainers 전환 시점" — 본 ADR 이 *H2 (임시) → Testcontainers + PostgreSQL* 전환 결정 회수. ADR-0006 본문의 H2 표현 갱신은 PLAN-0010 (H2 실제 제거 시점) 에 동반.
- **ADR-0004 (골격 단계 정책)** — *후행 비용이 큰 자리 우선 박기* 정신 — dev/test dialect 결정은 *후행 비용 큰 자리* 로 운영 결정 전이라도 박는 가치.
- **ADR-0008 (감사 데이터 정책)** — `AuditingEntityListener` 의 `@CreatedDate` / `@LastModifiedDate` 가 PostgreSQL `timestamp` 타입 자동 매핑. dialect 전환 영향 없음.
- **PLAN-0010 (예정)** — 본 ADR 의 구현 단위. compose.yaml / build.gradle / application.yml / Testcontainers config / ADR-0006 §1·§2 표 갱신.
