package com.dunowljj.board.adapter.out.persistence.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostJpaRepository extends JpaRepository<PostJpaEntity, Long> {

    Page<PostJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Modifying
    @Query("delete from PostJpaEntity p where p.id = :id")
    int deletePostById(@Param("id") Long id);
}
