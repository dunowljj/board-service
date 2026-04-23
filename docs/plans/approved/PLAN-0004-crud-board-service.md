# PLAN-0004: CRUD 게시판 서비스 구현

## Goal
Clean/Hexagonal Architecture + DDD + CQRS 구조에 따라 게시글(Post) CRUD 기능을 구현한다.
장기 프로젝트의 토대가 될 **최소 CRUD + 엄격한 아키텍처 뼈대**를 이번 Plan에서 확립한다.
이후 다른 도메인이 추가될 때 동일한 패키지/포트/어댑터 패턴을 그대로 따를 수 있도록 하는 것이 목적이다.

## Scope
- Post 도메인 모델 (Aggregate Root + Value Object) — 생성/재구성 모두에서 동등한 불변식 검증
- Application Port (Input/Output)
  - Input Port UseCase의 result는 **top-level DTO**로 분리 (Port 내부 중첩 record 금지)
  - Output Port는 역할별 분리 + 페이지 조회용 result DTO 별도
- Application Service (UseCase 구현) — Command/Query 분리, 존재 검증은 경량 API 사용
- Driving Adapter (REST Controller + DTO) — 예외 처리/에러 DTO를 Web Adapter 하위에 위치
- Driven Adapter (JPA Entity + Repository + PersistenceAdapter + Mapper) — 페이지 조회 시 단일 쿼리(count 중복 제거)
- build.gradle 의존성 활성화 (JPA, H2)
- application 설정 (DB, JPA)

## Non-goals
이번 Plan의 범위가 "최소 CRUD + 장기 확장을 위한 아키텍처 구조 확립"이라는 점을 유지하기 위해 명시적으로 제외한 항목만 기록한다. (프로젝트에 아직 없는 먼 기능들은 나열하지 않는다.)

- 자동화 테스트 작성 (도메인/서비스/컨트롤러/영속성) — 테스트 전략은 별도 Plan에서 정의
- Bean Validation(`@Valid`, `@NotBlank` 등) 도입 및 `IllegalArgumentException` 400 매핑 세분화 — 별도 Plan
- `PostNotFoundException`의 `common/` → `domain/post/` 이동 — 별도 Plan
- `Post` Aggregate의 `equals/hashCode` 정책 — 설계 결정 보류
- Update 경로의 JPA `merge`로 인한 추가 SELECT 해소 (dirty checking 전환 등) — 별도 논의

## Related ADRs
- ADR-0002: Java 17 + Spring Boot 4.x
- ADR-0003: Clean/Hexagonal Architecture + DDD + CQRS

## Files to Inspect
- `build.gradle`
- `src/main/resources/application.properties`
- `src/main/java/com/dunowljj/board/BoardServiceApplication.java`
- `docs/adr/0003-clean-architecture-ddd-hexagonal.md`
- `.claude/skills/clean-architecture.md`
- `.claude/skills/api-standards.md`
- `.claude/skills/db-standards.md`

## Files to Touch

### build.gradle
- `spring-boot-starter-data-jpa` 주석 해제
- `h2` 데이터베이스 추가 (runtimeOnly + testRuntimeOnly)
- `spring-boot-starter-security` 제거 (이번 Plan 범위 외)

### application 설정
- `src/main/resources/application.yml` (properties → yml 전환)

### Domain Layer
- `src/main/java/com/dunowljj/board/domain/post/Post.java` — Aggregate Root
- `src/main/java/com/dunowljj/board/domain/post/PostContent.java` — Value Object (title + body)

### Application Layer — Input Port
- `src/main/java/com/dunowljj/board/application/port/in/CreatePostUseCase.java` — 입력 Command record는 중첩 허용
- `src/main/java/com/dunowljj/board/application/port/in/GetPostUseCase.java`
- `src/main/java/com/dunowljj/board/application/port/in/UpdatePostUseCase.java` — 입력 Command record는 중첩 허용
- `src/main/java/com/dunowljj/board/application/port/in/DeletePostUseCase.java`
- `src/main/java/com/dunowljj/board/application/port/in/ListPostsUseCase.java` — 중첩 result record 금지
- `src/main/java/com/dunowljj/board/application/port/in/result/PostListResult.java` — Input Port가 반환하는 top-level result (`posts, page, size, totalElements, totalPages`)

### Application Layer — Output Port
- `src/main/java/com/dunowljj/board/application/port/out/LoadPostPort.java`
  - `Optional<Post> findById(Long id)`
  - `PostPage findPage(int page, int size)` — 페이지 조회는 items + totalElements를 한 번에 반환 (count 중복 제거)
  - `count()` 메서드는 두지 않는다
  - 별도의 `existsById`는 두지 않는다 — 삭제 경로는 delete 자체의 행-수 피드백으로 존재 판단, 업데이트 경로는 이미 `findById`로 애그리거트를 읽는다
- `src/main/java/com/dunowljj/board/application/port/out/SavePostPort.java`
- `src/main/java/com/dunowljj/board/application/port/out/DeletePostPort.java`
  - `int deleteById(Long id)` — 삭제된 행 수를 반환. `0`이면 대상 없음을 뜻하며, Service가 `PostNotFoundException`으로 매핑

### Application Layer — Paging Primitive (Application이 소유)
- `src/main/java/com/dunowljj/board/application/common/PostPage.java` — `(List<Post> items, long totalElements)`. 페이징은 유스케이스 계약이므로 Output Port DTO가 아닌 Application 공용 위치에 둔다.

### Application Layer — Service
- `src/main/java/com/dunowljj/board/application/service/PostCommandService.java` — Create, Update, Delete
- `src/main/java/com/dunowljj/board/application/service/PostQueryService.java` — Get, List

### Adapter (Driving) — Web
- `src/main/java/com/dunowljj/board/adapter/in/web/PostController.java`
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/request/CreatePostRequest.java`
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/request/UpdatePostRequest.java`
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/response/PostResponse.java`
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/response/PostListResponse.java` — `PostListResult`에서 매핑
- `src/main/java/com/dunowljj/board/adapter/in/web/dto/response/ErrorResponse.java` — 표준 에러 DTO (web 관심사)
- `src/main/java/com/dunowljj/board/adapter/in/web/exception/GlobalExceptionHandler.java` — `@RestControllerAdvice` (web 관심사)

### Adapter (Driven) — Persistence
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostJpaEntity.java`
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostJpaRepository.java` — Spring Data interface
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostPersistenceAdapter.java` — Output Port 구현
- `src/main/java/com/dunowljj/board/adapter/out/persistence/post/PostMapper.java` — Domain ↔ JPA Entity 변환

### Common
- `src/main/java/com/dunowljj/board/common/exception/PostNotFoundException.java` — 도메인 예외 (위치 재검토는 이후 Plan)
- ~~`common/exception/GlobalExceptionHandler.java`~~ → Web Adapter로 이동 (위 Adapter 섹션 참조)
- ~~`common/dto/ErrorResponse.java`~~ → Web Adapter로 이동 (위 Adapter 섹션 참조)

## Implementation Steps

### Step 1: 빌드 설정
1. `build.gradle` — JPA 주석 해제, H2 추가, Security 제거
2. `application.properties` → `application.yml` 전환 (H2 + JPA 설정)

### Step 2: Domain Layer
1. `Post` — Aggregate Root. `id`, `PostContent`, `author`, `createdAt`, `updatedAt` 필드. `create`, `reconstitute` 팩토리 메서드, `updateContent` 변경 메서드.
   - 불변식 검증은 `create`와 `reconstitute`에서 **동등하게** 수행한다.
     - `author`: null/blank 금지 (`validateAuthor` 헬퍼로 추출 공유)
     - `reconstitute`에서는 추가로 `id`, `createdAt`, `updatedAt` null 금지
     - `title`, `body`는 `PostContent` 생성자가 검증
   - 검증 실패 시 `IllegalArgumentException` (도메인 전용 예외 분리는 이후 Plan에서)
2. `PostContent` — Value Object. `title`, `body`. 불변(immutable), equals/hashCode 정의.

### Step 3: Application Layer — Port
1. Input Port 5개 인터페이스 정의 (Create, Get, Update, Delete, List).
   - Command 입력 record는 Port 내부 중첩 유지 (Adapter → Application 단방향 결합이라 허용).
   - **List의 result는 `application/port/in/result/PostListResult`로 top-level 분리** — Driving Adapter가 Port 내부 record에 의존하지 않도록.
2. Output Port 3개 인터페이스 정의 (Load, Save, Delete). 역할별 분리(ISP).
   - `LoadPostPort`에는 `findById`, `findPage(int,int)`만 둔다. `count()`, `existsById`는 두지 않는다.
   - `DeletePostPort.deleteById(Long)`는 `int`(영향받은 행 수)를 반환한다. 삭제는 자신이 존재 검증의 역할을 겸한다.
   - `PostPage`는 `application/common/`에 둔다. 페이징은 Output Port의 기술적 DTO가 아니라 유스케이스 계약이므로 Application이 소유한다.

### Step 4: Application Layer — Service
1. `PostCommandService` — CreatePostUseCase, UpdatePostUseCase, DeletePostUseCase 구현.
   - `delete(Long id)`는 `deletePostPort.deleteById(id)`를 호출하고 반환값이 `0`이면 `PostNotFoundException`을 던진다. 선행 존재 조회(`findById`, `existsById`) 금지 — 삭제 실행 자체가 존재 검증을 겸한다.
2. `PostQueryService` — GetPostUseCase, ListPostsUseCase 구현.
   - `list(...)`는 `loadPostPort.findPage(...)`가 반환한 `PostPage`에서 `totalElements`를 그대로 사용. `count()` 호출 없음.
   - `totalPages` 계산은 Service 책임 (`size == 0` 방어는 Controller 기본값으로 커버되지만, Service에서도 guard).

### Step 5: Adapter — Driven (Persistence)
1. `PostJpaEntity` — JPA 엔티티. `@Entity` 등 JPA 애노테이션은 여기에만.
2. `PostJpaRepository` — Spring Data JpaRepository 인터페이스.
   - `Page<PostJpaEntity> findAllByOrderByCreatedAtDesc(Pageable)`
   - `@Modifying @Query("delete from PostJpaEntity p where p.id = :id") int deletePostById(Long id)` — 기본 `JpaRepository.deleteById`는 SELECT + DELETE 2회 쿼리이므로 사용하지 않는다. `@Modifying` DELETE로 단일 쿼리 + 영향 행 수 반환.
3. `PostMapper` — Domain `Post` ↔ `PostJpaEntity` 변환.
4. `PostPersistenceAdapter` — `LoadPostPort`, `SavePostPort`, `DeletePostPort` 구현. `@Repository`.
   - `findPage(...)`는 Spring Data의 `Page` 객체에서 `getContent()` + `getTotalElements()`를 한 번에 꺼내 `PostPage`로 반환 → count 중복 쿼리 없음.
   - `deleteById(Long)`은 `postJpaRepository.deletePostById(id)`의 반환값을 그대로 전달 → 단일 DELETE, 선행 SELECT 없음.

### Step 6: Adapter — Driving (Web)
1. Request/Response DTO 정의. `PostListResponse.from(PostListResult)` — Port 내부 record가 아닌 top-level result를 수령.
2. `PostController` — REST endpoints. Input Port만 의존.
   - `POST /api/posts` → 201 Created
   - `GET /api/posts/{id}` → 200 OK
   - `PUT /api/posts/{id}` → 200 OK
   - `DELETE /api/posts/{id}` → 204 No Content
   - `GET /api/posts` → 200 OK (간단한 페이징: page, size 파라미터)

### Step 7: Web Exception Handling
1. `PostNotFoundException` — 도메인 예외. 본 Plan에서는 `common/exception/`에 둔다 (도메인 계층 이동은 별도 Plan).
2. `adapter/in/web/exception/GlobalExceptionHandler` — `@RestControllerAdvice`. API 표준 에러 응답. Web 관심사이므로 Driving Adapter 하위에 위치.
3. `adapter/in/web/dto/response/ErrorResponse` — 표준 에러 DTO (`code`, `message`, `timestamp`). Web 응답 DTO와 같은 위치.
4. `IllegalArgumentException` → 400 일률 매핑은 이번 범위에서 유지. 세분화는 별도 Plan.

## Acceptance Criteria
1. `POST /api/posts` — 게시글 생성 후 201 응답
2. `GET /api/posts/{id}` — 존재하는 게시글 조회 시 200, 없으면 404
3. `PUT /api/posts/{id}` — 게시글 수정 후 200, 없으면 404
4. `DELETE /api/posts/{id}` — 게시글 삭제 후 204, 없으면 404
5. `GET /api/posts` — 게시글 목록 조회 (page, size) 200
6. Domain Layer에 Spring/JPA 의존성 없음
7. 모든 Port는 Application Layer에 위치
8. Output Port가 역할별로 분리됨 (Load, Save, Delete)
9. Domain Entity ≠ JPA Entity (Mapper로 변환)
10. `GlobalExceptionHandler`, `ErrorResponse`는 `adapter/in/web/` 하위에 위치하며, `common/`에 존재하지 않는다.
11. `ListPostsUseCase`에 중첩 result record가 없으며, 결과 타입은 `application/port/in/result/PostListResult`이다.
12. `LoadPostPort`에 `findById`, `findPage`만 있으며 `count()`, `existsById`는 없다. 페이지 조회는 `findPage` → `PostPage`(`application/common/`에 위치)를 통해 이루어진다.
13. `DeletePostPort.deleteById(Long)`는 `int`를 반환하며, `PostCommandService.delete(...)`는 반환값 `0`일 때만 `PostNotFoundException`을 던진다. 선행 존재 조회(`findById`/`existsById`)가 코드에 나타나지 않는다.
14. `PostQueryService.list(...)`의 구현 본문에서 `LoadPostPort.count()` 호출이 존재하지 않는다 (Port에 메서드 자체가 없음).
15. `Post.reconstitute(...)`는 `create(...)`와 동등한 불변식 검증(author null/blank, PostContent 검증)을 수행하며, 추가로 id/createdAt/updatedAt null을 거부한다.

## ADR Required
No — 기존 ADR-0003 (Clean/Hexagonal Architecture + DDD + CQRS)의 구현이며, 새로운 아키텍처 결정 없음.

## Risks
- Spring Boot 4.x는 비교적 최신이라 일부 라이브러리 호환성 이슈 가능성
- Security starter 제거 시 기존 auto-configuration에 의존하던 부분(기본 `BoardServiceApplicationTests.contextLoads` 포함)이 깨질 수 있음 — 구동 확인 필요
- Port result를 Input/Output 양쪽에서 top-level로 분리하면서 매핑 단계가 하나 추가됨 — 장기적 엄격 구조 방침에 따른 의도된 비용
- `LoadPostPort`에서 `count()`를 제거하고 `findPage`로 통합 → 기존(이미 staged된) 구현체 시그니처를 재정비해야 함
- 자동화 테스트가 없는 상태로 구조 리팩토링 → 회귀는 수동 구동(`./gradlew bootRun` + API 호출)으로만 검증 가능
