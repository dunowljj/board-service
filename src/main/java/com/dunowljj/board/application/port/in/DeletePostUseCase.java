package com.dunowljj.board.application.port.in;

public interface DeletePostUseCase {

    void delete(DeletePostCommand command);

    record DeletePostCommand(Long id, Long actorUserId) {}
}
