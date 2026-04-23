package com.dunowljj.board.adapter.out.persistence.post;

import com.dunowljj.board.application.common.PostPage;
import com.dunowljj.board.application.port.out.DeletePostPort;
import com.dunowljj.board.application.port.out.LoadPostPort;
import com.dunowljj.board.application.port.out.SavePostPort;
import com.dunowljj.board.domain.post.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostPersistenceAdapter implements LoadPostPort, SavePostPort, DeletePostPort {

    private final PostJpaRepository postJpaRepository;

    @Override
    public Optional<Post> findById(Long id) {
        return postJpaRepository.findById(id)
                .map(PostMapper::toDomain);
    }

    @Override
    public PostPage findPage(int page, int size) {
        Page<PostJpaEntity> postsPage = postJpaRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        List<Post> items = postsPage.getContent().stream()
                .map(PostMapper::toDomain)
                .toList();
        return new PostPage(items, postsPage.getTotalElements());
    }

    @Override
    public Post save(Post post) {
        PostJpaEntity entity = PostMapper.toEntity(post);
        PostJpaEntity saved = postJpaRepository.save(entity);
        return PostMapper.toDomain(saved);
    }

    @Override
    public int deleteById(Long id) {
        return postJpaRepository.deletePostById(id);
    }
}
