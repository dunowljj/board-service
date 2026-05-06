# PLAN-0006-A: 테스트 인프라 골격 + ArchUnit

ADR-0006의 첫 실행 단위. 후속 Plan(-B Domain/Application, -C Web/Persistence, -D E2E)이 깔끔한 테스트 골격 위에서 시작할 수 있도록 *인프라와 경계 강제*만 담당한다. 테스트 케이스 작성 자체는 본 Plan 범위 밖.

## Goal

ADR-0006 의 계층별 테스트 의식을 도입하기 위한 *공통 골격*을 마련한다. 구체적으로:

1. ArchUnit 의존성과 규칙 8종을 도입해 ADR-0003 의 의존성 경계가 빌드 시점에 강제되도록 한다.
2. 통합테스트와 빠른 테스트의 실행 분리(`@Tag("integration")` + Gradle `integrationTest` task)를 도입한다.
3. 후속 Plan 들이 올라설 테스트 패키지 구조를 정리한다.

## Scope

- ArchUnit 의존성 추가 (`build.gradle`).
- ArchUnit 규칙 클래스 *2개* 작성 (ADR-0006 §3 의 두 use-case 분리):
  - `HexagonalArchitectureTest` — production 경계, `DoNotIncludeTests`. 8 규칙.
  - `TestStrategyArchitectureTest` — 테스트 의식, 테스트 바이트코드 스캔 포함. 3 규칙 (forward-defense, `.allowEmptyShould(true)`).
- Gradle test task 분리 — 기본 `test` 는 `integration` 태그 제외, 신규 `integrationTest` 는 해당 태그 전용. `integrationTest` task 는 default test sourceSet 의 `testClassesDirs` 와 `runtimeClasspath` 를 명시 연결.
- 기존 `BoardServiceApplicationTests` (`@SpringBootTest contextLoads`) 에 `@Tag("integration")` 부착 — 빠른 `test` 가 full Spring context 를 띄우지 않도록.
- 통합테스트 실행을 검증하는 smoke test 1개 (`@Tag("integration")` placeholder).
- 테스트 패키지 구조 — `architecture/` 디렉토리 신설. 다른 디렉토리(`domain/`, `application/`, `adapter/`)는 후속 Plan 에서 생기므로 본 Plan 에서는 만들지 않는다.

## Non-goals

- Domain·Application·Web·Persistence·E2E 테스트 케이스 작성 (PLAN-0006-B/C/D).
- Clock 주입 리팩터링 (별도 ADR/Plan, ADR-0006 §5).
- ArchUnit 규칙이 *실패하는* 코드 정리 — 발견 시 본 Plan 범위 밖. 별도 fix/ Plan.
- CI 워크플로(GitHub Actions 등) 도입.
- Testcontainers 도입.

## Related ADRs

- ADR-0006 (테스트 전략) — 본 Plan 의 권위.
- ADR-0003 (Clean/Hexagonal) — ArchUnit 강제 대상 경계.
- ADR-0005 (예외/에러) — `common.error..` shared kernel 경계.

## Acceptance Criteria

- `./gradlew test` 실행 시 ArchUnit 11 규칙(production 8 + test strategy 3) 이 모두 평가되고 통과한다.
- `./gradlew integrationTest` 실행 시 `@Tag("integration")` smoke test 와 태그된 `BoardServiceApplicationTests.contextLoads` 가 통과한다.
- `./gradlew test` 는 `integrationTest` 태그 테스트(`BoardServiceApplicationTests` 포함) 를 *실행하지 않는다* — 빠른 `test` 가 full Spring context 를 띄우지 않음을 보장.
- `HexagonalArchitectureTest` 의 production 규칙 8종 — `domain_pure`, `application_no_adapter`, `application_spring_narrow` (whitelist 의미), `common_error_framework_neutral`, `adapter_in_no_adapter_out`, `port_in_application`, `driving_adapter_no_nested_result_leak`, `no_cycles`.
- `TestStrategyArchitectureTest` 의 test 의식 규칙 3종 — `domain_tests_are_pojo` (Domain 테스트가 Spring/Mockito/JPA 의존 금지), `application_service_tests_no_springboottest`, `springboottest_is_localized` (`..e2e..` 또는 `BoardServiceApplicationTests` 만 허용). 모두 forward-defense, `.allowEmptyShould(true)`.
- `application_spring_narrow` 는 *whitelist* 로 구현 — `..application..` 이 `org.springframework..` 에 의존할 때 `stereotype`·`transaction` *외* 모든 하위 패키지 금지. blacklist 로 구현하지 않는다.
- `driving_adapter_no_nested_result_leak` 는 현재 위반할 코드가 없어 *위반 0건* 으로 통과해야 정상. ArchUnit 의 `allowEmptyShould(true)` 동작과 표현 정확성을 검증.
- 본 Plan 도입 후 main 빌드(`./gradlew build`)가 통과한다. ArchUnit 위반 발생 시 본 Plan 을 *중단*하고 별도 fix/ Plan 으로 위반을 정리한 뒤 재개.

## ADR Required

no — ADR-0006 이 권위.

## Risks

1. **ArchUnit 이 기존 코드의 숨겨진 위반을 처음 드러낼 수 있다.** 가장 가능성 있는 영역: `common.error..` 의 framework 의존, Application 의 좁은 Spring 허용 범위 위반(예: `org.springframework.web` 또는 `data` 직접 의존). 발견 시 본 Plan 중단 → fix/ 브랜치에서 정리 → 본 Plan 재개. 한 PR 에 인프라 도입과 위반 정리를 묶지 않는다.
2. **`integrationTest` task 가 default test sourceSet 의 classes/classpath 를 자동 상속하지 않는다.** Gradle 의 `tasks.register(..., Test)` 는 `testClassesDirs` 와 `classpath` 를 자동으로 잇지 않아, 명시 누락 시 task 가 *어떤 테스트도 발견하지 못한다*. 본 Plan 의 build.gradle 에서 두 속성을 명시한다(아래 Implementation Hints).
3. **ArchUnit 버전 vs JUnit 5 호환** — 1.3.x 라인 사용. Spring Boot 4 + Java 17 환경에서 정상 동작 확인 필요.
4. **`@Tag` 필터링이 의도대로 동작하지 않을 가능성** — `useJUnitPlatform { excludeTags("integration") }` 설정 누락 시 기본 `test` 가 통합테스트까지 같이 돌릴 수 있음. 검증 — smoke test 와 `BoardServiceApplicationTests` 가 `./gradlew test` 에서는 *실행되지 않아야* 한다.
5. **ArchUnit 의 empty-should 기본 동작은 *실패*다.** ArchUnit 은 `should()` 절이 매칭 0 건이면 기본적으로 *fail* 한다(0.23+). 통과시키려면 rule 단위 `.allowEmptyShould(true)` 또는 `archunit.properties` 의 `archRule.failOnEmptyShould=false` 명시가 필요. `driving_adapter_no_nested_result_leak` 의 경우 *subject* 측(`..adapter.in.web..`) 에 클래스가 존재하므로 empty-should 가 트리거되지 않아 안전하지만, 향후 subject 가 없어지는 시나리오 대비해 동작 방향(통과 아님 — *실패*) 을 정확히 인지한다.

## Required Reading

- `docs/adr/0006-test-strategy.md` — 본 Plan 권위, 특히 §3·§5·§10·§11.3.
- `docs/adr/0003-clean-architecture-ddd-hexagonal.md` — 의존성 규칙 출처.
- `docs/adr/0005-exception-error-response-policy.md` — error 패키지 경계.
- `.claude/skills/clean-architecture.md` — 레이어·Port·common.error 정책.
- `.claude/skills/plan-lifecycle.md` — 본 Plan 의 형식·archival 규약.
- `build.gradle` — 의존성 추가 위치.
- `src/main/java/com/dunowljj/board/` 전체 — ArchUnit 이 평가할 대상 패키지 구조 확인.

## Files to Touch

신규:

- `src/test/java/com/dunowljj/board/architecture/HexagonalArchitectureTest.java` — production 경계 8 규칙. `DoNotIncludeTests`.
- `src/test/java/com/dunowljj/board/architecture/TestStrategyArchitectureTest.java` — 테스트 의식 3 규칙. forward-defense.
- `src/test/java/com/dunowljj/board/IntegrationSmokeTest.java` — `@Tag("integration")` placeholder.

수정:

- `build.gradle` — ArchUnit 의존성 (`com.tngtech.archunit:archunit-junit5:1.3.x`) + `integrationTest` task (`testClassesDirs`/`classpath` 명시) + `test` task 의 `excludeTags("integration")`.
- `src/test/java/com/dunowljj/board/BoardServiceApplicationTests.java` — `@Tag("integration")` 추가.

## Implementation Hints

ArchUnit 의존성 (Spring Boot 의 dependency-management 가 버전을 제공하지 않으므로 명시):

```gradle
testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
```

Gradle test task 분리 (Groovy DSL). 커스텀 `Test` task 는 default test sourceSet 의 classes/classpath 를 자동 상속하지 않으므로 *반드시* 명시:

```gradle
tasks.named('test') {
    useJUnitPlatform {
        excludeTags 'integration'
    }
}

tasks.register('integrationTest', Test) {
    description = 'Runs slow integration tests tagged with @Tag("integration")'
    group = 'verification'
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
    useJUnitPlatform {
        includeTags 'integration'
    }
    shouldRunAfter tasks.named('test')
}

tasks.named('check') {
    dependsOn tasks.named('integrationTest')
}
```

ArchUnit 규칙 클래스 골격. ADR-0006 §11.3 가 6종 예시이고, 본 Plan 에서 §3 본문에 명시된 2종(`adapter_in_no_adapter_out`, `port_in_application`)과 nested Result 누출 방지 1종을 합쳐 **8종**으로 확장:

```java
package com.dunowljj.board.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.dunowljj.board")
class HexagonalArchitectureTest {
    // 1. domain_pure
    // 2. application_no_adapter
    // 3. application_spring_narrow              ← whitelist 의미 (아래 주석 참고)
    // 4. common_error_framework_neutral
    // 5. adapter_in_no_adapter_out              (신규: adapter.in.web.. → adapter.out.. 금지)
    // 6. port_in_application                    (신규: Port 인터페이스가 ..application.port.. 에만 선언됨)
    // 7. driving_adapter_no_nested_result_leak  (신규: ..adapter.in.web.. 가
    //    ..application.port.in.. 의 nested 타입을 import 시, *Command 로 끝나는
    //    record 가 아니면* 위반. 현재 위반 0건이 정상 — allowEmptyShould 검증 필요.)
    // 8. no_cycles

    // application_spring_narrow — *whitelist* 강제:
    //   `..application..` 이 `org.springframework..` 에 의존할 때
    //   `stereotype`·`transaction` *외* 모든 하위 패키지 금지.
    //   blacklist (특정 몇 개만 금지) 로 구현하면 org.springframework.context /
    //   cache / scheduling / validation 같은 신규 의존이 통과해 ADR-0006 §3
    //   "좁게 허용" 의도와 어긋난다.
    //
    //   표현 (ArchUnit predicate 합성):
    //     noClasses().that().resideInAPackage("..application..")
    //       .should().dependOnClassesThat(
    //           resideInAPackage("org.springframework..")
    //             .and(not(resideInAnyPackage(
    //                 "org.springframework.stereotype..",
    //                 "org.springframework.transaction.."
    //             )))
    //       );
    //   jakarta 도 동일 구조 — `jakarta.transaction..` 만 허용, 그 외 `jakarta..`
    //   모두 금지.
}
```

Smoke test 골격:

```java
package com.dunowljj.board;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class IntegrationSmokeTest {
    @Test
    void integration_test_task_runs_this_test() {
        assertThat(true).isTrue();
    }
}
```

기존 `BoardServiceApplicationTests` 태깅 — `@SpringBootTest` 는 full context 를 띄우므로 *통합 테스트* 로 분류:

```java
@SpringBootTest
@Tag("integration")   // ← 추가
class BoardServiceApplicationTests {
    @Test
    void contextLoads() {}
}
```

## Execution Notes

<!-- 실행 중 비자명한 결정만 시간순 append. 사소한 구현 디테일은 적지 않는다. -->
