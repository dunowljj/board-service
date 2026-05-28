package com.dunowljj.board.application.port.in;

/**
 * 로그인 use case. 실패 시 {@link com.dunowljj.board.common.error.AuthenticationFailedException} throw
 * → {@code GlobalExceptionHandler} 가 401 ProblemDetail (ADR-0011 §4, §4b).
 *
 * <p>반환 = 인증된 사용자 id. controller 가 이 값으로 {@code SecurityContext} 를 만들어
 * {@code SecurityContextRepository.saveContext} 호출.
 */
public interface LoginUserUseCase {

    Long login(LoginCommand command);

    record LoginCommand(String email, String password) {}
}
