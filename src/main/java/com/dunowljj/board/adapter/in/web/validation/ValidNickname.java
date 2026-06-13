package com.dunowljj.board.adapter.in.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * nickname 길이·허용 문자 경계 검증 (ADR-0005 §5.1). 검증 규칙은 도메인
 * {@code Nickname.isValidDisplay} 를 재사용해 VO 와 단일 출처를 공유한다. null/blank 는
 * {@code @NotBlank} 가 처리하도록 통과시킨다.
 */
@Documented
@Constraint(validatedBy = ValidNicknameValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidNickname {

    String message() default "닉네임은 한글·영문·숫자·_·- 로 2~20자여야 합니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
