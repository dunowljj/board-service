package com.dunowljj.board.application.port.out;

import com.dunowljj.board.application.port.out.result.AuditedUser;
import com.dunowljj.board.domain.user.User;

public interface SaveUserPort {

    AuditedUser save(User user);
}
