package com.dunowljj.board.domain.user;

/**
 * 사용자 aggregate root. email/nickname/passwordHash 의 invariant 는 각 value object 가 책임 (ADR-0011 §1).
 */
public class User {

    private Long id;
    private Email email;
    private Nickname nickname;
    private PasswordHash passwordHash;

    private User(Long id, Email email, Nickname nickname, PasswordHash passwordHash) {
        this.id = id;
        this.email = email;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
    }

    public static User register(Email email, Nickname nickname, PasswordHash passwordHash) {
        return new User(null, email, nickname, passwordHash);
    }

    public static User reconstitute(Long id, Email email, Nickname nickname, PasswordHash passwordHash) {
        if (id == null) {
            throw new IllegalArgumentException("Id must not be null");
        }
        return new User(id, email, nickname, passwordHash);
    }

    public Long getId() {
        return id;
    }

    public Email getEmail() {
        return email;
    }

    public Nickname getNickname() {
        return nickname;
    }

    public PasswordHash getPasswordHash() {
        return passwordHash;
    }
}
