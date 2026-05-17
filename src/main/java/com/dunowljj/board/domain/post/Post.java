package com.dunowljj.board.domain.post;

import com.dunowljj.board.common.error.InvalidPostContentException;

import java.time.LocalDateTime;
import java.util.Objects;

public class Post {

    private Long id;
    private PostContent content;
    private String author;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Post(Long id, PostContent content, String author,
                 LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.content = content;
        this.author = author;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Post create(LocalDateTime now, String title, String body, String author) {
        Objects.requireNonNull(now, "now must not be null");
        validateAuthor(author);
        PostContent content = new PostContent(title, body);
        return new Post(null, content, author, now, now);
    }

    public static Post reconstitute(Long id, String title, String body, String author,
                                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("Id must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("CreatedAt must not be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("UpdatedAt must not be null");
        }
        validateAuthor(author);
        PostContent content = new PostContent(title, body);
        return new Post(id, content, author, createdAt, updatedAt);
    }

    private static void validateAuthor(String author) {
        if (author == null || author.isBlank()) {
            throw new InvalidPostContentException("author");
        }
    }

    /**
     * Replace content and advance {@code updatedAt} to {@code now}.
     * Validates in order — {@code now} non-null, {@code now >= this.updatedAt}
     * (no backwards travel), then content invariants. All checks complete
     * before any mutation: a failing check leaves the aggregate unchanged
     * (ADR-0007 §2.1).
     */
    public void updateContent(LocalDateTime now, String title, String body) {
        Objects.requireNonNull(now, "now must not be null");
        if (now.isBefore(this.updatedAt)) {
            throw new IllegalArgumentException(
                    "now must not be before current updatedAt (backwards travel forbidden)");
        }
        PostContent next = new PostContent(title, body);
        this.content = next;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public PostContent getContent() {
        return content;
    }

    public String getTitle() {
        return content.getTitle();
    }

    public String getBody() {
        return content.getBody();
    }

    public String getAuthor() {
        return author;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
