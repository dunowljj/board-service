package com.dunowljj.board.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.boot.test.context.SpringBootTest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ADR-0006 §1 / §2 의 *테스트 전략* 을 빌드 시점에 강제한다.
 *
 * `HexagonalArchitectureTest` 는 ADR-0003 의 *프로덕션 경계* 를, 본 클래스는
 * ADR-0006 의 *테스트 의식* 을 본다. 두 ArchUnit 의 use-case 가 분리되어
 * 각자 클래스로 분기.
 *
 * 본 클래스는 `DoNotIncludeTests` 를 *적용하지 않아* 테스트 바이트코드를
 * 스캔한다. 모든 규칙은 forward-defense — 현재 매칭 subject 가 0 건이라도
 * 후속 Plan(-B/-C/-D) 이 테스트를 추가할 때 즉시 강제된다. ArchUnit 0.23+
 * 의 empty-should 기본 fail 동작 회피를 위해 `.allowEmptyShould(true)`.
 */
@AnalyzeClasses(packages = "com.dunowljj.board")
class TestStrategyArchitectureTest {

    /**
     * Domain 테스트는 POJO. Spring·Mockito·JPA 의존 금지. ADR-0006 §2:
     * "Domain 테스트는 절대 Spring 을 띄우지 않는다."
     */
    @ArchTest
    static final ArchRule domain_tests_are_pojo =
        noClasses().that().resideInAPackage("..domain..")
            .and().haveSimpleNameEndingWith("Test")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "org.mockito..",
                "jakarta.persistence..", "org.hibernate.."
            )
            .allowEmptyShould(true);

    /**
     * Application Service 테스트는 `@SpringBootTest` 금지. ADR-0006 §1·§2:
     * Application 은 Output Port mock 으로 단위 테스트. full Spring context 는
     * E2E 한정.
     */
    @ArchTest
    static final ArchRule application_service_tests_no_springboottest =
        noClasses().that().resideInAPackage("..application.service..")
            .and().haveSimpleNameEndingWith("Test")
            .should().beAnnotatedWith(SpringBootTest.class)
            .allowEmptyShould(true);

    /**
     * `@SpringBootTest` 는 *명시 지정 패키지* 또는 `BoardServiceApplicationTests`
     * 에만. 느린 통합테스트가 슬라이스 의식을 가진 위치에 흩어지지 않게.
     * 허용 — `..e2e..` 패키지, `BoardServiceApplicationTests`(Spring Initializr
     * 가 만든 contextLoads).
     */
    @ArchTest
    static final ArchRule springboottest_is_localized =
        classes().that().areAnnotatedWith(SpringBootTest.class)
            .should().resideInAPackage("..e2e..")
            .orShould().haveSimpleName("BoardServiceApplicationTests")
            .allowEmptyShould(true);
}
