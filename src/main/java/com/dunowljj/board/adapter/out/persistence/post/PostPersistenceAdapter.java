package com.dunowljj.board.adapter.out.persistence.post;

import com.dunowljj.board.application.common.PostPage;
import com.dunowljj.board.application.port.out.CreatePostPort;
import com.dunowljj.board.application.port.out.DeletePostPort;
import com.dunowljj.board.application.port.out.LoadPostPort;
import com.dunowljj.board.application.port.out.UpdatePostPort;
import com.dunowljj.board.application.port.out.result.AuditedPost;
import com.dunowljj.board.common.error.PostNotFoundException;
import com.dunowljj.board.domain.post.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostPersistenceAdapter
        implements LoadPostPort, CreatePostPort, UpdatePostPort, DeletePostPort {

    private final PostJpaRepository postJpaRepository;

    @Override
    public Optional<AuditedPost> findById(Long id) {
        return postJpaRepository.findById(id)
                .map(PostMapper::toAuditedPost);
    }

    @Override
    public PostPage findPage(int page, int size) {
        Page<PostJpaEntity> postsPage = postJpaRepository
                .findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size));
        List<AuditedPost> items = postsPage.getContent().stream()
                .map(PostMapper::toAuditedPost)
                .toList();
        return new PostPage(items, postsPage.getTotalElements());
    }

    @Override
    public AuditedPost create(Post post) {
        PostJpaEntity entity = PostMapper.toEntity(post);
        PostJpaEntity saved = postJpaRepository.save(entity);
        return PostMapper.toAuditedPost(saved);
    }

    /**
     * load-mutate-save 패턴 + flush 보장 (ADR-0008 §4.1). repository.save 만으로는
     * flush 보장 안 됨 — saveAndFlush 로 listener 즉시 발화시켜 반환 audit timestamp
     * 가 stale 아니게.
     */
    @Override
    public AuditedPost update(Post post) {
        PostJpaEntity existing = postJpaRepository.findById(post.getId())
                .orElseThrow(() -> new PostNotFoundException(post.getId()));
        existing.update(post.getTitle(), post.getBody());
        PostJpaEntity saved = postJpaRepository.saveAndFlush(existing);
        return PostMapper.toAuditedPost(saved);
    }

    @Override
    public int deleteById(Long id) {
        return postJpaRepository.deletePostById(id);
    }
}
