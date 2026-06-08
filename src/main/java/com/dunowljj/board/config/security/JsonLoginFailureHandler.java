package com.dunowljj.board.config.security;

import com.dunowljj.board.adapter.in.web.error.ErrorCategoryHttpStatusMapper;
import com.dunowljj.board.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;

/**
 * 로그인 실패(`AuthenticationException`) → 401 + ProblemDetail (`AUTHENTICATION_FAILED`).
 * 인증 필터는 {@code DispatcherServlet} 밖이라 {@code GlobalExceptionHandler} 가 못 잡으므로
 * 여기서 ADR-0005 ProblemDetail 형식으로 직접 직렬화한다 (ADR-0011 §4/§4b).
 */
public class JsonLoginFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    public JsonLoginFailureHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        // 깨진 요청은 400 MALFORMED_REQUEST, 자격 증명 실패는 401 AUTHENTICATION_FAILED (ADR-0005).
        ErrorCode code = (exception instanceof MalformedAuthenticationRequestException)
                ? ErrorCode.MALFORMED_REQUEST
                : ErrorCode.AUTHENTICATION_FAILED;
        HttpStatus status = ErrorCategoryHttpStatusMapper.toHttpStatus(code.category());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, code.defaultMessage());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code.code());
        problem.setProperty("timestamp", LocalDateTime.now().toString());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
