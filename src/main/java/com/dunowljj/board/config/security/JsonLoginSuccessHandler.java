package com.dunowljj.board.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * 로그인 성공 → 204 No Content (F-b, ADR-0011 §4). session 재발급 + SecurityContext 저장은
 * 필터 베이스가 이미 수행했으므로 여기선 상태 코드만 설정한다.
 */
public class JsonLoginSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) {
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
