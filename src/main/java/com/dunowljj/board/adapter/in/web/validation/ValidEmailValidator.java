package com.dunowljj.board.adapter.in.web.validation;

import com.dunowljj.board.domain.user.Email;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link ValidEmail} 구현 — 도메인 {@link Email#isValid} 규칙 재사용(단일 출처).
 * null/blank 는 {@code @NotBlank} 책임이라 통과(중복 메시지 회피).
 */
public class ValidEmailValidator implements ConstraintValidator<ValidEmail, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return Email.isValid(value);
    }
}
