package com.dunowljj.board.application.port.out;

import com.dunowljj.board.domain.user.Email;

public interface ExistsUserPort {

    boolean existsByEmail(Email email);

    boolean existsByNicknameCanonical(String canonical);
}
