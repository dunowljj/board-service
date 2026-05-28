package com.dunowljj.board.adapter.out.persistence.post;

/**
 * JPQL join projection — {@link PostJpaEntity} + author nickname 한 query (PLAN-0011 §7, N+1 회피).
 */
public record PostWithAuthor(PostJpaEntity post, String authorNickname) {}
