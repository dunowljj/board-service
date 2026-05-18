package com.dunowljj.board.adapter.out.persistence.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostJpaRepository extends JpaRepository<PostJpaEntity, Long> {

    /**
     * createdAt DESC 정렬 + id DESC tie-breaker. 동일 ms INSERT 가 production 에서도
     * 가능 (대량 입력 / 동시 요청 / ms 정밀도 손실) — 단일 정렬은 페이지 경계에서
     * 동일 row 가 두 페이지에 나타나거나 사라지는 사고 위험 (ADR-0008 Consequences).
     */
    Page<PostJpaEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @Modifying
    @Query("delete from PostJpaEntity p where p.id = :id")
    int deletePostById(@Param("id") Long id);
}
