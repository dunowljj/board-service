package com.dunowljj.board.config.security;

import com.dunowljj.board.application.port.in.LoginUserUseCase;
import com.dunowljj.board.common.error.AuthenticationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * JSON 기반 로그인 필터 (F-b, ADR-0011 §4 amended 2026-06-07). {@code POST /api/auth/login} 의 JSON
 * body 를 파싱해 {@link LoginUserUseCase} 로 직접 인증한다. Spring Security {@code formLogin}
 * (form-urlencoded 전제) 미사용.
 *
 * <p>인증 성공 후 session fixation 전략 + {@code SecurityContextRepository} 저장은 베이스
 * {@link AbstractAuthenticationProcessingFilter#successfulAuthentication}(프레임워크 보장)이 처리한다.
 * 실제 자격 검증은 use case 가 수행하므로 생성자에 넘기는 {@code AuthenticationManager} 는 trivial
 * pass-through 다. application 의 Spring-free {@link AuthenticationFailedException} 은 여기서 Spring
 * Security 의 {@link BadCredentialsException} 으로 경계 번역되어 {@code AuthenticationFailureHandler}
 * 로 흐른다 (application 의 Spring Security 의존 0 유지).
 */
public class JsonUsernamePasswordAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    private static final RequestMatcher LOGIN_MATCHER = request ->
            "POST".equalsIgnoreCase(request.getMethod())
                    && "/api/auth/login".equals(request.getRequestURI());

    private final LoginUserUseCase loginUserUseCase;
    private final ObjectMapper objectMapper;

    public JsonUsernamePasswordAuthenticationFilter(LoginUserUseCase loginUserUseCase, ObjectMapper objectMapper) {
        super(LOGIN_MATCHER, authentication -> authentication);
        this.loginUserUseCase = loginUserUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        LoginRequestBody body = readBody(request);
        try {
            Long actorUserId = loginUserUseCase.login(
                    new LoginUserUseCase.LoginCommand(body.email(), body.password()));
            return UsernamePasswordAuthenticationToken.authenticated(actorUserId, null, List.of());
        } catch (AuthenticationFailedException e) {
            throw new BadCredentialsException("authentication failed", e);
        }
    }

    private LoginRequestBody readBody(HttpServletRequest request) {
        try {
            return objectMapper.readValue(request.getInputStream(), LoginRequestBody.class);
        } catch (Exception e) {
            // 깨진 JSON / 미지원 content-type 은 인증 실패(401)가 아니라 요청 형식 오류(400).
            throw new MalformedAuthenticationRequestException("malformed login request", e);
        }
    }

    private record LoginRequestBody(String email, String password) {}
}
