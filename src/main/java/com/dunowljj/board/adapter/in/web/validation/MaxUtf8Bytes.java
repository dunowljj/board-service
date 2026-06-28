package com.dunowljj.board.adapter.in.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * UTF-8 byte 길이 상한 경계 검증. password 의 BCrypt 72 byte truncation 한계를 1차 방어한다
 * (ADR-0005 §5.1, ADR-0011 §5). null/blank 는 {@code @NotBlank} 가 처리하도록 통과.
 *
 * <p>{@code @Size} 는 char 기준이라 멀티바이트 입력에서 byte 한계를 못 막는다 → byte 기준 별도 제약.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "허용 byte 길이를 초과했습니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
