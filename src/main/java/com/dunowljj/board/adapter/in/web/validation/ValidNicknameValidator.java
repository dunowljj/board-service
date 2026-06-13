package com.dunowljj.board.adapter.in.web.validation;

import com.dunowljj.board.domain.user.Nickname;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link ValidNickname} 구현 — 도메인 {@link Nickname#isValidDisplay} 규칙 재사용(단일 출처).
 * null/blank 는 {@code @NotBlank} 책임이라 통과(중복 메시지 회피).
 */
public class ValidNicknameValidator implements ConstraintValidator<ValidNickname, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return Nickname.isValidDisplay(value);
    }
}
