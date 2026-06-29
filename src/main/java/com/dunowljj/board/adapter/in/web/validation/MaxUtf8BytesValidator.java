package com.dunowljj.board.adapter.in.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * {@link MaxUtf8Bytes} 구현. null/blank 는 {@code @NotBlank} 책임이라 통과 — `@ValidEmail`/
 * `@ValidNickname` 과 동일 가드(blank 입력에 byte-length 위반까지 겹쳐 errors 중복되는 것 회피).
 */
public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {

    private int max;

    @Override
    public void initialize(MaxUtf8Bytes constraint) {
        this.max = constraint.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
