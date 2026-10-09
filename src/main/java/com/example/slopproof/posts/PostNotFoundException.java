package com.example.slopproof.posts;

import com.example.slopproof.shared.ProblemException;
import org.springframework.http.HttpStatus;

/** No post has the requested id, either because it never existed or because it was deleted. */
public final class PostNotFoundException extends ProblemException {

  private static final long serialVersionUID = 1L;

  PostNotFoundException(PostId id) {
    super(
        problem(
            HttpStatus.NOT_FOUND,
            "post-not-found",
            "Post not found",
            "No post has id " + id.value()),
        null);
  }
}
