package com.dunowljj.board.application.port.out;

import com.dunowljj.board.application.port.out.result.AuditedUser;
import com.dunowljj.board.domain.user.Email;

import java.util.Optional;

public interface LoadUserPort {

    Optional<AuditedUser> findById(Long id);

    Optional<AuditedUser> findByEmail(Email email);
}
