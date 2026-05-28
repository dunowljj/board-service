package com.dunowljj.board.application.port.in;

import com.dunowljj.board.application.port.in.result.UserResult;

public interface GetCurrentUserUseCase {

    UserResult getById(Long userId);
}
