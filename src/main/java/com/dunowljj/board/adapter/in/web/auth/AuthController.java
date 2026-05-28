package com.dunowljj.board.adapter.in.web.auth;

import com.dunowljj.board.adapter.in.web.dto.request.RegisterRequest;
import com.dunowljj.board.adapter.in.web.dto.response.UserResponse;
import com.dunowljj.board.application.port.in.RegisterUserUseCase;
import com.dunowljj.board.application.port.in.result.UserResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 endpoint (ADR-0011 §3, §4). JSON 커스텀 REST.
 *
 * <p>login 은 본 컨트롤러가 아니라 {@code JsonUsernamePasswordAuthenticationFilter} 가 처리한다
 * (F-b, ADR-0011 §4 amended 2026-06-07). 본 컨트롤러는 register / logout 만 담당한다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final boolean sessionCookieSecure;

    public AuthController(RegisterUserUseCase registerUserUseCase,
                          @Value("${server.servlet.session.cookie.secure:false}") boolean sessionCookieSecure) {
        this.registerUserUseCase = registerUserUseCase;
        this.sessionCookieSecure = sessionCookieSecure;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResult result = registerUserUseCase.register(
                new RegisterUserUseCase.RegisterUserCommand(
                        request.email(), request.nickname(), request.password()));
        return ResponseEntity.status(201).body(UserResponse.from(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        ResponseCookie expiredSessionCookie = ResponseCookie.from("JSESSIONID", "")
                .path("/")
                .httpOnly(true)
                .secure(sessionCookieSecure)
                .sameSite("Lax")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredSessionCookie.toString());
        return ResponseEntity.noContent().build();
    }
}
