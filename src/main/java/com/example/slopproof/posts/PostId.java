package com.example.slopproof.posts;

import java.util.Objects;
import java.util.UUID;

/**
 * The identity of a post. A wrapper, so a post id cannot be passed where another id is expected.
 */
public record PostId(UUID value) {

  public PostId {
    Objects.requireNonNull(value, "post id must not be null");
  }
}
