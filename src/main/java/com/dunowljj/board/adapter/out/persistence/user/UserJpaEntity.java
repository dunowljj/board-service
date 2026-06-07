package com.dunowljj.board.adapter.out.persistence.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 사용자 영속 entity (ADR-0011 §1, ADR-0008 audit listener).
 *
 * <p>{@code email} 과 {@code nickname_canonical} 만 unique. {@code nickname} 표시 컬럼은 unique 없음.
 * constraint name 명시 — race fallback (DataIntegrityViolationException) 분기 기준 (PLAN-0011 Risk #5).
 */
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = UserJpaEntity.EMAIL_CONSTRAINT, columnNames = "email"),
        @UniqueConstraint(name = UserJpaEntity.NICKNAME_CONSTRAINT, columnNames = "nickname_canonical")
})
@EntityListeners(AuditingEntityListener.class)
public class UserJpaEntity {

    /** unique constraint name — race fallback 분기(UserPersistenceAdapter)와 단일 출처 공유. */
    public static final String EMAIL_CONSTRAINT = "uk_users_email";
    public static final String NICKNAME_CONSTRAINT = "uk_users_nickname_canonical";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "nickname_canonical", nullable = false)
    private String nicknameCanonical;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected UserJpaEntity() {}

    public UserJpaEntity(Long id, String email, String nickname, String nicknameCanonical, String passwordHash) {
        this.id = id;
        this.email = email;
        this.nickname = nickname;
        this.nicknameCanonical = nicknameCanonical;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
    public String getNicknameCanonical() { return nicknameCanonical; }
    public String getPasswordHash() { return passwordHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
