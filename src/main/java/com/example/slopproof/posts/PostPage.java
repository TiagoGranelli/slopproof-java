package com.example.slopproof.posts;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** One page of posts, newest first, and the cursor of the page after it when there is one. */
public record PostPage(List<Post> posts, @Nullable PostCursor nextCursor) {

  public PostPage {
    // A copy, so a caller that keeps its list cannot change the page after it was built.
    posts = List.copyOf(posts);
  }
}
