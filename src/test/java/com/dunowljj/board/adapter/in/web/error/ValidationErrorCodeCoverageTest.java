package com.dunowljj.board.adapter.in.web.error;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.validation.Constraint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * exhaustiveness 가드 (PLAN-0012-C, ADR-0005 §5.2) — **실사용 중인 모든 제약 애너테이션이
 * {@code INVALID} fallback 이 아닌 코드로 매핑되는지**를 빌드 시점에 강제한다.
 *
 * <p>fallback 은 런타임 안전장치일 뿐 정답이 아니다. 새 제약을 도입하면서
 * {@link ValidationErrorCode} 매핑을 빠뜨리면 이 테스트가 깨진다.
 *
 * <p>수집 범위는 **`dto/request` 의 모든 record 컴포넌트 + 모든 {@code @RestController} 의 메서드
 * 파라미터**다. 특정 컨트롤러를 하드코딩하지 않는다 — 현재 컨트롤러가 4개이고 앞으로 늘어나므로,
 * 한 곳만 스캔하면 "전역" 주장이 도입 당일에만 참인 말이 된다(forward-defense).
 */
class ValidationErrorCodeCoverageTest {

    private static final String BASE_PACKAGE = "com.dunowljj.board";

    @Test
    @DisplayName("실사용 제약은 모두 non-fallback 코드로 매핑된다 (매핑 누락 시 빌드 실패)")
    void every_constraint_in_use_maps_to_a_code() {
        Set<Class<? extends Annotation>> constraints = constraintsInUse();

        assertThat(constraints)
                .as("스캔된 제약이 0건이면 스캔 자체가 고장난 것 — 최소한 @NotBlank/@Size 는 잡혀야 한다")
                .isNotEmpty();

        assertThat(constraints)
                .allSatisfy(constraint -> assertThat(ValidationErrorCode.supports(constraint))
                        .as("@%s 가 ValidationErrorCode 에 매핑되지 않았다 — "
                                + "BY_TYPE 에 추가하거나 새 코드를 정의하라 (ADR-0005 §5.2 확장 규약)",
                                constraint.getSimpleName())
                        .isTrue());
    }

    @Test
    @DisplayName("스캔이 실제로 두 출처(DTO record + 컨트롤러 파라미터)를 모두 훑는다")
    void scan_covers_both_sources() {
        assertThat(constraintNames(requestDtoConstraints()))
                .contains("NotBlank", "NotNull", "Size", "ValidEmail", "ValidNickname", "MaxUtf8Bytes");
        assertThat(constraintNames(controllerParameterConstraints()))
                .contains("Min", "Max");
    }

    private static Set<Class<? extends Annotation>> constraintsInUse() {
        Set<Class<? extends Annotation>> all = new LinkedHashSet<>(requestDtoConstraints());
        all.addAll(controllerParameterConstraints());
        return all;
    }

    /**
     * `dto/request` 패키지의 모든 record 컴포넌트에 붙은 제약.
     *
     * <p><b>{@code RecordComponent.getAnnotations()} 를 쓰지 않는다</b> — jakarta 제약들의
     * {@code @Target} 에 {@code RECORD_COMPONENT} 가 없어서 그 API 로는 0건이 나온다. 컴포넌트에 선언된
     * 애너테이션은 {@code @Target} 에 맞는 자리(필드/접근자/생성자 파라미터)로 *전파*되므로 그쪽을 훑는다.
     */
    private static Set<Class<? extends Annotation>> requestDtoConstraints() {
        Set<Class<? extends Annotation>> constraints = new LinkedHashSet<>();
        for (Class<?> dto : classesIn(BASE_PACKAGE + ".adapter.in.web.dto.request")) {
            for (Field field : dto.getDeclaredFields()) {
                collectConstraints(field.getAnnotations(), constraints);
            }
            for (Method accessor : dto.getDeclaredMethods()) {
                collectConstraints(accessor.getAnnotations(), constraints);
            }
            for (Constructor<?> constructor : dto.getDeclaredConstructors()) {
                for (Annotation[] parameterAnnotations : constructor.getParameterAnnotations()) {
                    collectConstraints(parameterAnnotations, constraints);
                }
            }
        }
        return constraints;
    }

    /** 모든 `@RestController` 의 메서드 파라미터에 붙은 제약 (컨트롤러 하드코딩 금지). */
    private static Set<Class<? extends Annotation>> controllerParameterConstraints() {
        Set<Class<? extends Annotation>> constraints = new LinkedHashSet<>();
        for (Class<?> controller : classesIn(BASE_PACKAGE)) {
            if (!controller.isAnnotationPresent(RestController.class)) {
                continue;
            }
            for (Method method : controller.getDeclaredMethods()) {
                for (Annotation[] parameterAnnotations : method.getParameterAnnotations()) {
                    collectConstraints(parameterAnnotations, constraints);
                }
            }
        }
        return constraints;
    }

    private static void collectConstraints(Annotation[] annotations,
                                           Set<Class<? extends Annotation>> sink) {
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (type.isAnnotationPresent(Constraint.class)) {
                sink.add(type);
            }
        }
    }

    private static Set<Class<?>> classesIn(String packageName) {
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(packageName);
        Set<Class<?>> classes = new LinkedHashSet<>();
        imported.forEach(javaClass -> {
            if (!javaClass.getName().contains("$")) {
                classes.add(javaClass.reflect());
            }
        });
        return classes;
    }

    private static Set<String> constraintNames(Set<Class<? extends Annotation>> constraints) {
        Set<String> names = new LinkedHashSet<>();
        constraints.forEach(constraint -> names.add(constraint.getSimpleName()));
        return names;
    }

}
