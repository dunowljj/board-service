package com.dunowljj.board.adapter.in.web.auth;

import com.dunowljj.board.config.security.SecurityConfig;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CSRF token 발급 endpoint (ADR-0011 §6). anonymous OK. JS 가 첫 진입 시 호출해 XSRF-TOKEN cookie 확보.
 *
 * <p>응답 본문은 Spring {@link CsrfToken} 직렬화 표준 — `{ headerName, parameterName, token }`.
 */
@RestController
@RequestMapping("/api/csrf")
public class CsrfController {

    private final boolean csrfCookieSecure;

    public CsrfController(@Value("${server.servlet.session.cookie.secure:false}") boolean csrfCookieSecure) {
        this.csrfCookieSecure = csrfCookieSecure;
    }

    @GetMapping
    public CsrfToken csrf(CsrfToken token, HttpServletResponse response) {
        ResponseCookie csrfCookie = ResponseCookie.from(SecurityConfig.CSRF_COOKIE_NAME, token.getToken())
                .path("/")
                .httpOnly(false)
                .secure(csrfCookieSecure)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());
        return token;
    }
}
