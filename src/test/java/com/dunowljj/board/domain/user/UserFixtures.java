package com.dunowljj.board.domain.user;

public final class UserFixtures {

    public static final String VALID_EMAIL = "alice@example.com";
    public static final String VALID_NICKNAME = "alice";
    public static final String VALID_PASSWORD_HASH = "$2a$10$placeholderHashForTestUseOnlyXXXXXXXXXXXXXXXXXX";

    private UserFixtures() {}

    public static User aValidUser() {
        return User.register(
                new Email(VALID_EMAIL),
                new Nickname(VALID_NICKNAME),
                new PasswordHash(VALID_PASSWORD_HASH));
    }

    public static User aReconstitutedUser(Long id) {
        return User.reconstitute(id,
                new Email(VALID_EMAIL),
                new Nickname(VALID_NICKNAME),
                new PasswordHash(VALID_PASSWORD_HASH));
    }
}
