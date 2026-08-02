package com.dunowljj.board.adapter.in.web.error;

import com.dunowljj.board.adapter.in.web.validation.MaxUtf8Bytes;
import com.dunowljj.board.adapter.in.web.validation.ValidEmail;
import com.dunowljj.board.adapter.in.web.validation.ValidNickname;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.springframework.context.MessageSourceResolvable;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;

/**
 * {@code errors[].code} 어휘 — 필드 검증 실패 *종류*의 안정적 머신 식별자 (ADR-0005 §5.2).
 *
 * <p><b>{@code common/error/ErrorCode} 와 축이 다르다.</b> 최상위 {@code code} 는 *응답 1건의 분류*
 * ({@code VALIDATION_FAILED}), 본 enum 은 *제약 1건의 실패 종류*다. 섞지 않는다.
 *
 * <p><b>안정성 계약</b>: 여기 이름은 클라이언트가 분기에 쓰는 식별자다. **이름 변경·삭제는 breaking
 * change** 이므로 하지 않는다(추가만 하위 호환). 새 제약은 *기존 코드에 매핑하는 것이 기본*이고, 새 코드는
 * 클라이언트가 다르게 *행동*해야 할 때만 만든다 — "문구가 다르다"는 새 코드 사유가 아니다(그건
 * {@code reason} 의 일).
 *
 * <p>표시 문구({@code reason})는 애너테이션의 {@code message} 가 소유하고, 본 코드는 제약 애너테이션
 * *타입*에서 파생한다 — 제약 선언에 code 를 적지 않으므로 새 DTO·제약이 자동으로 커버된다.
 */
public enum ValidationErrorCode {

    /** 값이 없음. {@code @NotNull}(null) / {@code @NotBlank}(null·빈문자·공백만). */
    REQUIRED,
    /** 하한 미달. {@code @Size(min)}. */
    TOO_SHORT,
    /** 상한 초과. {@code @Size(max)}(char 수) / {@code @MaxUtf8Bytes}(바이트). */
    TOO_LONG,
    /** 수치 범위 밖. {@code @Min} / {@code @Max}. */
    OUT_OF_RANGE,
    /** 형식 불일치. {@code @ValidEmail} / {@code @ValidNickname}. */
    INVALID_FORMAT,
    /** 매핑되지 않은 제약의 fallback. 실사용 제약이 여기 떨어지면 exhaustiveness 테스트가 빌드를 깬다. */
    INVALID;

    /**
     * 애너테이션 타입 → 코드 단일 출처. {@code @Size} 는 min/max *방향 판정*이 필요해 이 표에 없다
     * ({@link #supports}가 별도로 인정).
     */
    private static final Map<Class<? extends Annotation>, ValidationErrorCode> BY_TYPE = Map.of(
            NotNull.class, REQUIRED,
            NotBlank.class, REQUIRED,
            Min.class, OUT_OF_RANGE,
            Max.class, OUT_OF_RANGE,
            ValidEmail.class, INVALID_FORMAT,
            ValidNickname.class, INVALID_FORMAT,
            MaxUtf8Bytes.class, TOO_LONG);

    /**
     * 위반 1건 → 코드. **fallback 사다리**(ADR-0005 §5.2):
     * <ol>
     *   <li>{@code violation} 이 있으면 애너테이션 타입 + 속성으로 정밀 파생 (주경로)</li>
     *   <li>없으면 {@code error.getCodes()} 의 제약 simple name 으로 이름 기반 파생 —
     *       {@code @Size} 는 속성이 없어 방향을 못 가르므로 보수적으로 {@link #INVALID}</li>
     *   <li>그것도 실패하면 {@link #INVALID}</li>
     * </ol>
     * 어느 단계에서도 예외를 던지지 않는다 — 코드 파생 실패가 에러 *응답 자체*를 깨뜨리면 안 된다.
     *
     * @param violation Bean Validation 위반. unwrap 실패 시 {@code null} 허용
     * @param error     Spring 이 감싼 표현 (이름 기반 fallback 용)
     */
    public static ValidationErrorCode from(ConstraintViolation<?> violation, MessageSourceResolvable error) {
        if (violation != null) {
            ValidationErrorCode code = fromDescriptor(violation.getConstraintDescriptor(),
                    violation.getInvalidValue());
            if (code != INVALID) {
                return code;
            }
        }
        return fromConstraintName(constraintName(error));
    }

    /** 이 애너테이션 타입이 non-fallback 코드로 매핑되는가. exhaustiveness 가드가 사용. */
    public static boolean supports(Class<? extends Annotation> annotationType) {
        return BY_TYPE.containsKey(annotationType) || annotationType == Size.class;
    }

    private static ValidationErrorCode fromDescriptor(ConstraintDescriptor<?> descriptor, Object invalidValue) {
        Class<? extends Annotation> type = descriptor.getAnnotation().annotationType();
        if (type == Size.class) {
            return sizeDirection(descriptor, invalidValue);
        }
        return BY_TYPE.getOrDefault(type, INVALID);
    }

    /**
     * {@code @Size} 는 한 애너테이션이 두 방향 실패를 낸다 — 타입만으로는 못 가른다. 제약 속성
     * {@code min}/{@code max} 와 거부값 길이를 비교해 판정한다.
     *
     * <p>거부값이 {@code null} 이면 {@code @Size} 는 애초에 발화하지 않는다(JSR-380 null-pass) — 존재
     * 검사는 {@code @NotNull}/{@code @NotBlank} 가 별도 엔트리로 담당(ADR-0005 §5.1 aggregation 계약).
     */
    private static ValidationErrorCode sizeDirection(ConstraintDescriptor<?> descriptor, Object invalidValue) {
        Integer length = lengthOf(invalidValue);
        if (length == null) {
            return INVALID;
        }
        Map<String, Object> attributes = descriptor.getAttributes();
        int min = intAttribute(attributes, "min", 0);
        int max = intAttribute(attributes, "max", Integer.MAX_VALUE);
        if (length < min) {
            return TOO_SHORT;
        }
        if (length > max) {
            return TOO_LONG;
        }
        return INVALID;
    }

    private static int intAttribute(Map<String, Object> attributes, String name, int defaultValue) {
        return (attributes.get(name) instanceof Integer value) ? value : defaultValue;
    }

    private static Integer lengthOf(Object value) {
        if (value instanceof CharSequence text) {
            return text.length();
        }
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        return null;
    }

    /**
     * 이름 기반 fallback — {@link #BY_TYPE} 를 그대로 재사용해 매핑이 두 벌로 갈라지지 않게 한다
     * (상수 공유만으로는 divergence 를 막지 못한다는 PLAN-0012-B 의 교훈).
     */
    private static ValidationErrorCode fromConstraintName(String constraintName) {
        if (constraintName == null) {
            return INVALID;
        }
        return BY_TYPE.entrySet().stream()
                .filter(entry -> entry.getKey().getSimpleName().equals(constraintName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(INVALID);
    }

    /**
     * Spring 의 {@code DefaultMessageCodesResolver} 규약상 {@code getCodes()} 의 *마지막* 원소가 제약
     * simple name 이다 (예: {@code ["Size.createPostRequest.title", …, "Size"]}).
     */
    private static String constraintName(MessageSourceResolvable error) {
        if (error == null) {
            return null;
        }
        String[] codes = error.getCodes();
        return (codes == null || codes.length == 0) ? null : codes[codes.length - 1];
    }
}
