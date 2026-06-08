package com.dunowljj.board.config.security;

import org.springframework.security.core.AuthenticationException;

/**
 * 로그인 요청 body 가 깨졌거나 JSON 으로 파싱 불가한 경우(미지원 content-type 포함).
 *
 * <p>이는 자격 증명 실패(401)가 아니라 *요청 형식 오류*(400)다. {@code AuthenticationException}
 * 을 상속해 인증 필터의 실패 라우팅을 타되, {@link JsonLoginFailureHandler} 가 타입을 판별해
 * {@code AUTHENTICATION_FAILED}(401) 가 아닌 {@code MALFORMED_REQUEST}(400, ADR-0005) 로 응답한다.
 */
public class MalformedAuthenticationRequestException extends AuthenticationException {

    public MalformedAuthenticationRequestException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
