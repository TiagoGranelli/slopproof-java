package com.example.slopproof.posts;

import static com.example.slopproof.api.PostsApi.PATH_CREATE_POST;

import com.example.slopproof.api.PostsApi;
import com.example.slopproof.api.model.CreatePostRequestDto;
import com.example.slopproof.api.model.PostDto;
import com.example.slopproof.api.model.PostPageDto;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter of the posts API: maps the contract's DTOs to the domain and back, and decides
 * nothing itself.
 */
@RestController
public class PostsController implements PostsApi {

  private final PostService posts;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected bean, never returned to a caller")
  public PostsController(PostService posts) {
    this.posts = posts;
  }

  @Override
  public ResponseEntity<PostPageDto> listPosts(Integer limit, @Nullable String cursor) {
    PostPage page = posts.list(limit, decodeCursor(cursor));
    return ResponseEntity.ok(toPageDto(page));
  }

  @Override
  public ResponseEntity<PostDto> createPost(CreatePostRequestDto request) {
    Post created = posts.create(request.getTitle(), request.getBody());
    URI location = URI.create(PATH_CREATE_POST + "/" + created.id().value());
    return ResponseEntity.created(location).body(toDto(created));
  }

  @Override
  public ResponseEntity<PostDto> getPost(UUID postId) {
    return ResponseEntity.ok(toDto(posts.get(new PostId(postId))));
  }

  @Override
  public ResponseEntity<Void> deletePost(UUID postId) {
    posts.delete(new PostId(postId));
    return ResponseEntity.noContent().build();
  }

  private static @Nullable PostCursor decodeCursor(@Nullable String cursor) {
    return cursor == null ? null : PostCursor.decode(cursor);
  }

  private static PostPageDto toPageDto(PostPage page) {
    List<PostDto> items = page.posts().stream().map(PostsController::toDto).toList();
    PostCursor next = page.nextCursor();
    return new PostPageDto(items).nextCursor(next == null ? null : next.encode());
  }

  private static PostDto toDto(Post post) {
    OffsetDateTime createdAt = OffsetDateTime.ofInstant(post.createdAt(), ZoneOffset.UTC);
    return new PostDto(post.id().value(), post.title(), post.body(), createdAt);
  }
}
