package com.dunowljj.board.adapter.in.web;

import com.dunowljj.board.adapter.in.web.dto.request.CreatePostRequest;
import com.dunowljj.board.adapter.in.web.dto.request.UpdatePostRequest;
import com.dunowljj.board.adapter.in.web.dto.response.PostListResponse;
import com.dunowljj.board.adapter.in.web.dto.response.PostResponse;
import com.dunowljj.board.application.port.in.CreatePostUseCase;
import com.dunowljj.board.application.port.in.DeletePostUseCase;
import com.dunowljj.board.application.port.in.GetPostUseCase;
import com.dunowljj.board.application.port.in.ListPostsUseCase;
import com.dunowljj.board.application.port.in.UpdatePostUseCase;
import com.dunowljj.board.application.port.in.result.AuditedPostResult;
import com.dunowljj.board.application.port.in.result.PostListResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final CreatePostUseCase createPostUseCase;
    private final GetPostUseCase getPostUseCase;
    private final UpdatePostUseCase updatePostUseCase;
    private final DeletePostUseCase deletePostUseCase;
    private final ListPostsUseCase listPostsUseCase;

    @PostMapping
    public ResponseEntity<PostResponse> create(@Valid @RequestBody CreatePostRequest request) {
        var command = new CreatePostUseCase.CreatePostCommand(
                request.title(), request.body(), request.author());
        AuditedPostResult result = createPostUseCase.create(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(PostResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getById(@PathVariable Long id) {
        AuditedPostResult result = getPostUseCase.getById(id);
        return ResponseEntity.ok(PostResponse.from(result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdatePostRequest request) {
        var command = new UpdatePostUseCase.UpdatePostCommand(
                id, request.title(), request.body());
        AuditedPostResult result = updatePostUseCase.update(command);
        return ResponseEntity.ok(PostResponse.from(result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deletePostUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<PostListResponse> list(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        PostListResult result = listPostsUseCase.list(page, size);
        return ResponseEntity.ok(PostListResponse.from(result));
    }
}
