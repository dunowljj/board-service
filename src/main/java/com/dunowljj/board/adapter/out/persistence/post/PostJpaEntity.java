package com.dunowljj.board.adapter.out.persistence.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Audit metadata 의 책임 자리 (ADR-0008 §2). createdAt/updatedAt 은
 * {@code AuditingEntityListener} 가 채운다 — 도메인 (Post) 은 모름.
 *
 * <p>update 경로는 {@link #update(String, String)} 메서드를 통해 *부분 변경* — setter
 * 우후죽순 노출 회피, entity 의 자기 변경 책임 (ADR-0008 §4.1 의 load-mutate-save 흐름).
 */
@Entity
@Table(name = "posts")
@EntityListeners(AuditingEntityListener.class)
public class PostJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private String author;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected PostJpaEntity() {}

    public PostJpaEntity(Long id, String title, String body, String author) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.author = author;
        // createdAt/updatedAt — AuditingEntityListener 가 채운다.
    }

    /**
     * 부분 변경 — load-mutate-save 흐름의 mutate 자리. 동일 값 set 은 Hibernate
     * dirty check 가 *변경 없음* 판정 → @PreUpdate 미발화 → updatedAt 불변
     * (ADR-0008 §3.1 의 no-op 시맨틱).
     */
    public void update(String title, String body) {
        this.title = title;
        this.body = body;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getAuthor() { return author; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
