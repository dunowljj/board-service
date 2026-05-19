# ADR-0009: equals/hashCode 코드 컨벤션 — `instanceof` pattern matching 통일

## Status
Proposed

## Date
2026-05-19

## Context

현재 `PostContent` value object 의 `equals` / `hashCode` 가 `getClass()` 기반으로 작성되어 있다.

```java
// src/main/java/com/dunowljj/board/domain/post/PostContent.java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PostContent that = (PostContent) o;
    return Objects.equals(title, that.title) && Objects.equals(body, that.body);
}
```

이 자리에서 다음 부담이 누적된다.

- **`getClass()` 의 경직성** — subclass 인스턴스 가 *equality 동의* 를 못 가짐. *Liskov substitutability* 측면에서 *symmetric equals 보장* 위해 `getClass()` 를 채택한 자리지만, *subclass 자체를 차단* 하면 (즉 *VO 를 `final` 선언*) `instanceof` + `final` 조합이 *getClass() 동등 안전성* 보다 더 명료. `final` 가 *상속을 못 막은 채로* `getClass()` 만 두는 현 상태가 *애매한 절충*.
- **`EqualsVerifier` 와의 마찰** — PLAN-0006-B 의 `PostContentTest` 가 `.usingGetClass()` 호출로 *`getClass()` 가정* 명시 (EqualsVerifier 의 기본은 `instanceof` 가정). 이 호출이 *지속되는 한* `getClass()` 결정이 *지속*. PLAN-0006-B Execution Notes (2026-05-08) 가 이미 *"PostContent 의 record 마이그레이션 + instanceof 기반 equals 로 전환은 별도 작은 PR 로 분리"* 결정 — 본 ADR 이 그 deferred 결정의 *instanceof + final 부분* 을 회수 (record 부분은 §"Considered Alternatives" 에서 현 시점 미채택으로 정리).
- **Java 17 의 pattern matching `instanceof`** — `if (!(o instanceof PostContent that)) return false; ...` 한 줄로 *type check + cast + binding* 통합. *final VO 에서 same-type check 를 Java 17 문법으로 간결화* — `getClass()` 비교 + 별도 cast + null 검사 3 줄 → 1 줄.

이 영역은 *코드 작성 컨벤션* 결정으로, *시스템 결정 무게는 작지만* 후속 도메인 (Comment, 등) value object 추가 시 *일관 패턴* 박지 않으면 *getClass() 와 instanceof 가 혼재* 할 위험. 골격 단계에서 *컨벤션* 으로 결정한다.

## Decision

본 ADR 은 *코드 작성 컨벤션* 만 결정한다. *equality 가 *필요한 자리 자체* 의 결정* (예: Post Aggregate equality, PostJpaEntity equality) 은 *다른 ADR 영역* 으로 분리. *PostContent → record 전환* 은 §"Considered Alternatives" 에 *현 시점 미채택* 으로 정리한다 — 별도 ADR 슬롯 예약 아님.

### 1. `equals` / `hashCode` 작성 시 `instanceof` pattern matching 사용

- `getClass()` 비교 *금지*. `instanceof` pattern matching (Java 17+) 사용.
- 표준 패턴 (class 가 `final` 선언된 전제 — §2 참조):
  ```java
  public final class PostContent {
      // ...
      @Override
      public boolean equals(Object o) {
          if (this == o) return true;
          if (!(o instanceof PostContent that)) return false;
          return Objects.equals(title, that.title) && Objects.equals(body, that.body);
      }
  }
  ```
- `null` 비교는 `instanceof` 가 *자동 처리* — `instanceof` 가 `null` 에 대해 항상 false. 별도 `o == null` 검사 *불요*.
- `hashCode` 는 *기존 그대로* (`Objects.hash(...)`). 본 ADR 의 결정 영역 아님.

### 2. 적용 대상 + 상속 정책 — *VO 는 `final` 로 선언*

- *equality 가 *명시적으로 필요한* 자리* 에 한정.
- 현재 적용 대상: **`PostContent`** (유일).
- 미래 대상: 추가 value object / equality 가 필요한 도메인 자리. 본 ADR 의 패턴 동일 적용.
- **본 ADR 의 instanceof 패턴을 채택하는 VO 는 *`final` 로 선언*** — 사유: `instanceof` 가 *subclass equality 허용* 함의를 가지지만, *subclass 가 부모와 다른 equality 의식* 을 가지면 *symmetric equals 위반* 위험 (Liskov substitutability). `final` 가 *상속 자체 차단* 해 안전성 확보. EqualsVerifier 의 표준 요구 사항 (final class / final equals / canEqual 중 하나) 과도 정합 — `final class` 채택.
- **본 ADR 은 *순수 도메인 VO* 한정**. JPA entity / persistence layer 의 equality 는 *별도 ADR* 영역 (proxy 호환성 / transient id / hashCode 안정성 같은 *추가 의식* 필요).

### 3. `EqualsVerifier.usingGetClass()` 호출 제거

`PostContentTest` 의 `EqualsVerifier.forClass(PostContent.class).usingGetClass().verify()` 호출에서 `.usingGetClass()` *제거*. EqualsVerifier 의 기본 동작이 *`instanceof` 패턴 검증* 이고, §2 의 `final` 결정이 *상속 자체 차단* 해 Liskov 안전성 보장 — `.usingGetClass()` 명시 *불필요*.

### 4. 본 ADR 의 *결정 범위 경계*

본 ADR 은 **순수 도메인 value object 의 작성 패턴 컨벤션** 만 결정. 다음은 *별도 결정 영역* 으로 명시 분리:

- **`Post` Aggregate equality 정책** — PLAN-0004 의 *"Post 의 equals/hashCode 정책 보류"* 결정 회수. *id 기반 equality* vs *structural equality* 결정. 별도 ADR.
- **JPA entity / persistence layer equality** — `PostJpaEntity` 의 equality 가 필요해지는 시점의 별도 결정. *Hibernate proxy 호환성* + *transient id 처리* + *hashCode 안정성* (mutable id) + *getter 접근* 등 *여러 의식 묶임* — 단순 instanceof 패턴 일반화로 해결 불가. *별도 ADR*.
- **ADR-0003 의 `§"Equality Policy"` 부재 정정** — ADR-0006 line 307, PLAN-0004, PLAN-0006-B 등이 *ADR-0003 §"Equality Policy"* 를 cross-reference 하는데 ADR-0003 본문에 그 자리 *부재*. 문서 정합 깨짐 해소는 별도 ADR.

## Considered Alternatives

- **`getClass()` 유지** — 거부 사유: (a) value object 에 *subclass 가 등장할 *합리적 동기*가 없음* — VO 의 본질은 *불변 + 동등성 by 값* 이라 subclass 가 anti-pattern, *symmetric equals* 우려는 `final` 로 *상속 자체 차단* 해 해소 (§2), (b) `getClass()` + non-`final` 조합이 *애매한 절충* — `final` 가 없으면 subclass 가 생길 수 있는데 `getClass()` 가 그걸 막은 것처럼 보이는 *false safety*, (c) PLAN-0006-B 가 이미 *instanceof 전환* deferred 결정 — 본 ADR 이 그 회수.
- **Lombok `@EqualsAndHashCode`** — 거부 사유: *동등성 기준이 코드에서 안 읽힘*. PLAN-0006-B Execution Notes 의 *"테스트는 문서다"* 정신 (ADR-0006 §4) 과 마찰. 동등성 기준 필드를 *명시적으로 코드에 쓰는* 가치 > 보일러플레이트 절감.
- **`PostContent` 를 record 로 전환** — 현재는 채택하지 않는다. `final class + instanceof equals + EqualsVerifier` 로 ADR-0009 의 목적은 충분히 달성된다. record 전환은 accessor 변경 (`getTitle()` / `getBody()` → `title()` / `body()`) 과 VO 표현 방식 기본값 결정까지 동반하므로 현재 변경 대비 이득이 작다. 후속 VO 가 늘어나 반복 비용이 실제로 커질 때 다시 검토한다.
- **`AbstractValueObject` 베이스 클래스 + 추상 equals** — 거부 사유: *equality 기준 필드* 가 *VO 마다 다름* — abstract 패턴이 `protected abstract List<Object> equalityFields()` 같은 *반환 보일러플레이트* 만 누적, 결국 *코드에서 동등성 기준이 *list 반환 메서드 안* 으로 숨음* → "테스트는 문서다" (ADR-0006 §4) 정신 약화. equality 필드 명시는 각 VO 의 `equals` 본문에 직접 두는 게 코드에서 읽힌다.

## Rejected Suggestions

본 ADR 설계 과정에서 *실제로 제안되었으나 거부된* 안.

- **본 ADR 범위에 `PostContent → record` 전환 포함** — 거부 사유: record 전환은 *현 시점 미채택* 으로 결정 (2026-05-19). `final class + instanceof equals` 가 본 ADR 의 목적을 충분히 달성하며, record 도입은 accessor / API 변경과 VO 표현 방식 기본값 결정을 동반해 *현 단계 이득* 보다 변경 비용이 큼. §"Considered Alternatives" 의 *PostContent 를 record 로 전환* 항목 참조.
- **본 ADR 범위에 `Post` Aggregate equality 정책 포함** — 거부 사유: PLAN-0004 의 *보류 결정 회수* 는 *id 기반 vs structural* 같은 *시스템 결정 무게* 큼. 작성 패턴 컨벤션과 묶으면 ADR 응집도 ↓. 별도 ADR (ADR-0011 슬롯 추정) 로 분리.
- **ADR-0003 의 신규 절 추가 (`§"Equality Policy"`) 형태로 본 ADR 대체** — 거부 사유: 본 ADR 의 *결정 응집도* 가 *작성 패턴 컨벤션* 한 자리. ADR-0003 *cross-reference 부재 정정* 은 *문서 유지보수* 영역으로, *결정 변경* 이 아닌 *서술 보완* — 본 ADR (instanceof 채택) 의 결정 책임과 응집도 다름. *이번 ADR 범위 밖* 으로 두고 별도 작업 (ADR-0003 본문 갱신 또는 별도 정리 ADR) 으로 분리.

## Consequences

**긍정적 영향**

- `PostContent` 의 `equals` 가 *Java 17 표준 패턴* 으로 단순화. 행 수 1 줄 감소 (`o == null` 검사 제거 — `instanceof` 가 자동 처리). 의도 명료성 ↑.
- **`PostContent` 의 `final` 선언** — 상속 차단으로 *Liskov 안전성 명시*. EqualsVerifier 의 표준 요구 사항 정합.
- `EqualsVerifier.usingGetClass()` 호출 제거 — *제약 표시* 사라짐. EqualsVerifier 의 *기본 동작 (`instanceof`)* 으로 자연 검증.
- 후속 도메인 추가 시 *일관 패턴* — *getClass() vs instanceof 혼재* 위험 0.

**부정적 영향 / 트레이드오프**

- *적용 대상 1 자리* (`PostContent`) — 본 ADR 의 *작성 패턴 결정* 자체는 *시스템 결정 무게가 작음*. ADR 격상 가치 의문 가능. 그러나 *컨벤션 박지 않으면 후속 도메인 추가 시 *getClass() 가 잘못 도입될 수 있음* — 골격 단계 (ADR-0004) 의 *후행 비용이 큰 자리* 정신 정합.
- *final 결정 비용* — `PostContent` 의 `final` 부착이 *향후 inheritance 가 필요한* 시점에 *수정 부담*. 그러나 value object 의 *본질* 이 *불변 + 동등성 by 값* 이라 *subclass 요구는 anti-pattern* — `final` 가 *VO 본질 강화*.

## Open Questions

- **`Post` Aggregate equality 정책** — PLAN-0004 의 *보류 결정 회수*. *id 기반* vs *structural* 결정. 별도 ADR.
- **`PostJpaEntity` equality 정책** — JPA proxy 호환성 + Aggregate equality 결정에 따라 자동 정해짐. 별도 ADR 또는 Post equality ADR 의 일부.
- **ADR-0003 의 `§"Equality Policy"` 부재 정정** — 다른 문서들 (ADR-0006 line 307, PLAN-0004, PLAN-0006-B) 의 cross-reference 가 *존재하지 않는 섹션* 을 가리킴. ADR-0003 의 *신규 절 추가* 또는 *cross-reference 정리* 별도 ADR.

## Related

- ADR-0003 (Clean/Hexagonal + DDD + CQRS) — *§"Equality Policy"* 부재. 다른 문서들이 cross-reference 하는 자리. 본 ADR 의 *작성 패턴* 은 그 부재 섹션의 *일부* 가 될 후보. 정리는 Open Questions.
- ADR-0006 (테스트 전략) §11.3 / line 307 — *Entity equality 미정의* 가정. 본 ADR 의 *작성 패턴* 결정과 직접 영향 없음.
- PLAN-0004 §"equals/hashCode 정책 보류" — Post Aggregate equality 결정의 deferred 자리. 별도 ADR 슬롯.
- PLAN-0006-B Execution Notes (2026-05-08) — *PostContent record 마이그레이션 + instanceof equals 전환 deferred*. 본 ADR 이 *instanceof + final 부분* 회수. record 전환은 §"Considered Alternatives" 의 *현 시점 미채택* 으로 정리 (별도 ADR 슬롯 아님).
