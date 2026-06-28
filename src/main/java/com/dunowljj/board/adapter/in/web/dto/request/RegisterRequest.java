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
 *
 * <p>`errors[].code` 미도입(ADR-0005 §5.1)이라 `errors[].reason` 이 프론트의 사용자 표시 메시지다.
 * 따라서 *모든* 제약(`@NotBlank`/`@Size` 포함)에 한국어 메시지를 명시해 프레임워크 기본(영문)
 * 메시지가 섞이지 않게 한다.
 */
public record RegisterRequest(
        @NotBlank(message = "이메일을 입력해주세요") @ValidEmail String email,
        @NotBlank(message = "닉네임을 입력해주세요") @ValidNickname String nickname,
        @NotBlank(message = "비밀번호를 입력해주세요")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
        @MaxUtf8Bytes(value = 72, message = "비밀번호가 너무 깁니다") String password) {}
