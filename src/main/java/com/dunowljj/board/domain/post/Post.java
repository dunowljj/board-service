package com.dunowljj.board.domain.post;

import com.dunowljj.board.common.error.InvalidPostContentException;

public class Post {

    private Long id;
    private PostContent content;
    private String author;

    private Post(Long id, PostContent content, String author) {
        this.id = id;
        this.content = content;
        this.author = author;
    }

    public static Post create(String title, String body, String author) {
        validateAuthor(author);
        return new Post(null, new PostContent(title, body), author);
    }

    public static Post reconstitute(Long id, String title, String body, String author) {
        if (id == null) {
            throw new IllegalArgumentException("Id must not be null");
        }
        validateAuthor(author);
        return new Post(id, new PostContent(title, body), author);
    }

    private static void validateAuthor(String author) {
        if (author == null || author.isBlank()) {
            throw new InvalidPostContentException("author");
        }
    }

    public void updateContent(String title, String body) {
        this.content = new PostContent(title, body);
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
}
