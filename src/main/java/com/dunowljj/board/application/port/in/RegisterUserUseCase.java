package com.dunowljj.board.application.port.in;

import com.dunowljj.board.application.port.in.result.UserResult;

public interface RegisterUserUseCase {

    UserResult register(RegisterUserCommand command);

    record RegisterUserCommand(String email, String nickname, String password) {}
}
