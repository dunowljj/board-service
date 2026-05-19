# PLAN-0009: PostContent 의 instanceof equals + final 적용

ADR-0009 (equals/hashCode 코드 컨벤션 — `instanceof` pattern matching 통일) 의 구현 단위. PostContent 의 `equals` 를 *Java 17 표준 패턴 (instanceof pattern matching)* 으로 전환 (hashCode 는 변경 없음) + class 에 `final` 부착 + EqualsVerifier 호출 단순화.

## Goal

ADR-0009 §1/§2/§3 의 *직접 구현*. 구체적으로:

1. `PostContent.java` — `public class` → `public final class` (§2).
2. `PostContent.equals(Object)` — `getClass()` 비교 → `instanceof` pattern matching (§1). `null` 검사 제거.
3. `PostContent.hashCode()` — *변경 없음*. `Objects.hash(title, body)` 그대로.
4. `PostContentTest.equals_and_hashCode_contract` — `EqualsVerifier.forClass(...).usingGetClass().withOnlyTheseFields(...).verify()` 에서 `.usingGetClass()` 제거 (§3).
5. `./gradlew check` BUILD SUCCESSFUL + ArchUnit 11 규칙 유지.

## Scope

### Included

- **`PostContent.java`** — 두 변경:
  - 클래스 선언: `public class PostContent` → `public final class PostContent`
  - `equals(Object o)` 메서드:
    - 기존: `if (o == null || getClass() != o.getClass()) return false; PostContent that = (PostContent) o;`
    - 변경: `if (!(o instanceof PostContent that)) return false;`
- **`PostContentTest.java`** — `equals_and_hashCode_contract` 메서드의 `.usingGetClass()` 호출 1 줄 제거. 다른 builder 체인(`.withOnlyTheseFields("title", "body")`) 은 그대로 유지.

### Not Included

- **`PostContent` → record 전환** — ADR-0010 슬롯 (별도 ADR + Plan).
- **`Post` Aggregate equality 정책** — PLAN-0004 의 보류 결정 회수, 별도 ADR.
- **`PostJpaEntity` equality 정책** — Hibernate proxy 호환성 묶임, 별도 ADR.
- **ADR-0003 `§"Equality Policy"` 부재 정정** — 문서 유지보수 영역, 별도 작업.
- **EqualsVerifier 의 다른 호출 자리 (현재 0 자리)** — `PostContent` 외에 EqualsVerifier 사용 없음. 미래 도메인 추가 시 본 ADR-0009 패턴 자연 적용.

## Non-goals

- 신규 의존성 추가.
- ArchUnit 규칙 변경.
- 다른 도메인 / Service / Adapter 파일 수정.

## Related ADRs

- **ADR-0009** (equals/hashCode 코드 컨벤션) — 본 Plan 권위. §1 (instanceof 패턴), §2 (VO 는 final 선언), §3 (EqualsVerifier.usingGetClass 제거).
- **ADR-0006** (테스트 전략) §4 — *"테스트는 문서다"*. EqualsVerifier + 손 어서트 병행 패턴 유지.
- **PLAN-0006-B Execution Notes (2026-05-08)** — *PostContent 의 record 마이그레이션 + instanceof 전환 deferred* 결정. 본 Plan 이 *instanceof + final 부분* 회수.

## Files to Inspect

- `docs/adr/0009-equals-instanceof-convention.md` — 본 Plan 권위. §1/§2/§3 직접 구현.
- `docs/plans/done/PLAN-0006-B-domain-application-tests.md` — PostContentTest 컨벤션 (`@DisplayName` 한국어, EqualsVerifier 사용 의도). Execution Notes 의 deferred 결정.
- `src/main/java/com/dunowljj/board/domain/post/PostContent.java` — *변경 대상*. 현재 `public class` + `getClass()` 기반 equals.
- `src/test/java/com/dunowljj/board/domain/post/PostContentTest.java` — *변경 대상*. line 63-66 의 EqualsVerifier 체인.

## Files to Touch

수정:
- `src/main/java/com/dunowljj/board/domain/post/PostContent.java` (클래스 선언 + equals 메서드)
- `src/test/java/com/dunowljj/board/domain/post/PostContentTest.java` (EqualsVerifier 체인 1 줄 제거)

신규 / 삭제: 없음.

## Implementation Steps

순서는 *의존성* 기준. 본 PR 은 *단일 commit* 머지.

1. **`PostContent` 클래스 선언에 `final` 부착** — `public class PostContent` → `public final class PostContent`. 컴파일 통과 (다른 파일에 `PostContent` 상속 자리 없음 확인 — PLAN 영향 확인 완료).
2. **`PostContent.equals(Object)` 갱신** — `getClass()` 비교 + cast 두 줄 → `instanceof` pattern matching 한 줄. `null` 검사 제거 (`instanceof` 자동 처리).
3. **`PostContentTest.equals_and_hashCode_contract` 갱신** — `.usingGetClass()` 호출 줄 제거.
4. **`./gradlew check` 통과 확인** — `test` (Domain unit + ArchUnit + slice) + `integrationTest` (E2E + smoke) 모두 green.

## Acceptance Criteria

- `PostContent.java` 의 클래스 선언이 `public final class`.
- `PostContent.equals(Object)` 가 `if (!(o instanceof PostContent that)) return false;` 패턴 사용. `getClass()` 호출 0.
- `PostContent.hashCode()` 변경 없음.
- `PostContentTest` 의 `EqualsVerifier.forClass(PostContent.class)` 체인에 `.usingGetClass()` 호출 *없음*. `.withOnlyTheseFields("title", "body")` + `.verify()` 는 유지.
- `./gradlew check` BUILD SUCCESSFUL.
- ArchUnit 11 규칙 통과 유지.
- 신규 의존성 추가 없음.

## ADR Required

no — ADR-0009 가 권위. 본 Plan 은 §1/§2/§3 의 *직접 구현*. 새로운 시스템 결정 없음.

## Risks

1. **`PostContent` 의 외부 상속 자리 부재 확인** — `final` 부착 시 *현재 subclass 존재 여부* 가 컴파일 영향. `grep -rn "extends PostContent" src/` 결과 0 (확인 완료). production / test 어디서도 상속 안 함 — *영향 없음*.
2. **EqualsVerifier 의 `.usingGetClass()` 제거 시 검증 동작 변화** — 기본 동작이 *`instanceof` 패턴 기준* 으로 전환. `final class` 이므로 EqualsVerifier 의 상속 관련 검증 요구와 정합 — subclass test 자동 skip.
3. **PostContent fixture / mock 영향** — `Post.create` / `Post.reconstitute` / `Post.updateContent` 경로에서 `new PostContent(...)` 호출, `PostContentTest` 도 직접 생성. 생성자 호출부는 `final` 부착에 영향 없음. 상속 자리 / mock 사용 모두 없음 확인 완료.
4. **EqualsVerifier 가 final class 와 호환되는 검증 동작** — EqualsVerifier 3.17.1 은 `final class` 에 대해 *subclass test 자동 skip*. `withOnlyTheseFields("title", "body")` + 기본 검증 그대로 통과 예상.

### Pre-resolved

- **ADR-0009 §4 의 "순수 도메인 VO 한정"** — 본 Plan 의 적용 대상이 *PostContent* 1 자리. JPA entity / persistence layer equality 미포함 명시.
- **record 전환 미포함** — ADR-0010 슬롯.

## Implementation Hints

### `PostContent.java` 변경 후

```java
public final class PostContent {

    private final String title;
    private final String body;

    public PostContent(String title, String body) {
        if (title == null || title.isBlank()) {
            throw new InvalidPostContentException("title");
        }
        if (body == null) {
            throw new InvalidPostContentException("body");
        }
        this.title = title;
        this.body = body;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostContent that)) return false;
        return Objects.equals(title, that.title) && Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, body);
    }
}
```

### `PostContentTest.equals_and_hashCode_contract` 변경 후

```java
@Test
@DisplayName("equals 와 hashCode 는 title·body 기준 계약을 만족한다")
void equals_and_hashCode_contract() {
    EqualsVerifier.forClass(PostContent.class)
            .withOnlyTheseFields("title", "body")
            .verify();
}
```

## Execution Notes

<!-- 실행 중 비자명한 결정만 시간순 append. 사소한 구현 디테일은 적지 않는다. -->
