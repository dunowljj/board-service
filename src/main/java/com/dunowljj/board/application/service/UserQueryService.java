package com.dunowljj.board.application.service;

import com.dunowljj.board.application.port.in.GetCurrentUserUseCase;
import com.dunowljj.board.application.port.in.result.UserResult;
import com.dunowljj.board.application.port.out.LoadUserPort;
import com.dunowljj.board.application.port.out.result.AuditedUser;
import com.dunowljj.board.common.error.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserQueryService implements GetCurrentUserUseCase {

    private final LoadUserPort loadUserPort;

    public UserQueryService(LoadUserPort loadUserPort) {
        this.loadUserPort = loadUserPort;
    }

    @Override
    public UserResult getById(Long userId) {
        AuditedUser audited = loadUserPort.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return UserResult.of(audited.user(), audited.createdAt(), audited.updatedAt());
    }
}
