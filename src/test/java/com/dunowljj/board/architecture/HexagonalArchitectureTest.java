package com.dunowljj.board.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ADR-0006 §3 의 의존성 경계를 빌드 시점에 강제한다.
 * 8 개 규칙 — 자세한 결정 근거와 *왜* 는 ADR-0006 §3 / §11.3 참조.
 *
 * `DoNotIncludeTests` — 본 규칙은 *프로덕션 코드 한정*. 테스트 코드는 의도적으로
 * mock·fixture·@SpringBootTest 등 경계 너머를 사용하므로 ArchUnit 강제 대상이 아니다.
 */
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

    /**
     * Application 의 외부 라이브러리 의존을 *whitelist* 로 강제한다.
     * 허용 — `org.springframework.stereotype..`, `org.springframework.transaction..`, `jakarta.transaction..`.
     * 금지 — Spring·jakarta 의 다른 모든 하위 패키지.
     * blacklist 로 구현하면 `context`/`cache`/`scheduling`/`validation` 등 신규 의존이 통과한다.
     */
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

    @ArchTest
    static final ArchRule common_error_framework_neutral =
        noClasses().that().resideInAPackage("..common.error..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "jakarta.persistence..",
                "org.hibernate..", "jakarta.servlet..", "jakarta.validation..",
                "..adapter..", "..domain..", "..application.."
            );

    @ArchTest
    static final ArchRule adapter_in_no_adapter_out =
        noClasses().that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..");

    @ArchTest
    static final ArchRule port_in_application =
        classes().that().haveSimpleNameEndingWith("Port")
            .should().resideInAPackage("..application.port..");

    /**
     * Driving Adapter 가 Input Port 의 nested *Result* 타입을 import 하면 위반.
     * nested *Command* record 는 허용 (clean-architecture.md §"Port Result DTO Layout").
     * 현재 위반 0 건이 정상 — 미래 회귀 방지용 예방 규칙.
     */
    private static final DescribedPredicate<JavaClass> NESTED_PORT_IN_NON_COMMAND =
        new DescribedPredicate<>("nested type in ..application.port.in.. that is not a Command record") {
            @Override
            public boolean test(JavaClass cls) {
                if (!cls.getPackageName().startsWith("com.dunowljj.board.application.port.in")) {
                    return false;
                }
                if (cls.getEnclosingClass().isEmpty()) {
                    return false;   // top-level types are allowed
                }
                // 허용 — Command record 만. 이름만 Command 인 일반 class 는 위반.
                boolean isCommandRecord = cls.isRecord() && cls.getSimpleName().endsWith("Command");
                return !isCommandRecord;
            }
        };

    @ArchTest
    static final ArchRule driving_adapter_no_nested_result_leak =
        noClasses().that().resideInAPackage("..adapter.in.web..")
            .should().dependOnClassesThat(NESTED_PORT_IN_NON_COMMAND);

    @ArchTest
    static final ArchRule no_cycles =
        slices().matching("com.dunowljj.board.(*)..").should().beFreeOfCycles();
}
