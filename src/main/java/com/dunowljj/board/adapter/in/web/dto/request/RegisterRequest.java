package com.dunowljj.board.adapter.in.web.dto.request;

import com.dunowljj.board.adapter.in.web.validation.MaxUtf8Bytes;
import com.dunowljj.board.adapter.in.web.validation.ValidEmail;
import com.dunowljj.board.adapter.in.web.validation.ValidNickname;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 입력. 형식·문자·길이 검증은 경계 커스텀 제약이 도메인 VO 규칙을 공유해 수행
 * (ADR-0005 §5.1). email/nickname 의 길이는 `@ValidEmail`/`@ValidNickname` 이 trim 후 단일 처리하므로
 * `@Size` 를 두지 않는다(중복 errors·raw vs trim 불일치 회피). `@NotBlank` 는 존재(required) 검사로
 * 커스텀 제약(null/blank 통과)과 짝을 이룬다. password 는 trim 안 함 — char 최소만 `@Size`, byte
 * 상한은 `@MaxUtf8Bytes`.
 */
public record RegisterRequest(
        @NotBlank @ValidEmail String email,
        @NotBlank @ValidNickname String nickname,
        @NotBlank @Size(min = 8) @MaxUtf8Bytes(value = 72, message = "비밀번호가 너무 깁니다") String password) {}
