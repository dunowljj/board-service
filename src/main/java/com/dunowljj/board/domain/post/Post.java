package com.dunowljj.board.domain.post;

import com.dunowljj.board.common.error.InvalidPostContentException;
import java.time.LocalDateTime;

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

    public static Post create(String title, String body, String author) {
        validateAuthor(author);
        PostContent content = new PostContent(title, body);
        LocalDateTime now = LocalDateTime.now();
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

    public void updateContent(String title, String body) {
        this.content = new PostContent(title, body);
        this.updatedAt = LocalDateTime.now();
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
