package com.example.slopproof.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A credential that prints as {@code <redacted>}, so a log line, a debugger or a stack trace cannot
 * leak it. Read the value with {@link #reveal()} only where it is sent.
 */
public final class Secret {

  private final String value;

  /**
   * Holds the credential; rejects an empty one, since an empty secret is a configuration mistake.
   */
  public Secret(String value) {
    Objects.requireNonNull(value, "secret must not be null");
    if (value.isEmpty()) {
      throw new IllegalArgumentException("secret must not be empty");
    }
    this.value = value;
  }

  /** The credential itself. Call it only where the value leaves the process. */
  public String reveal() {
    return value;
  }

  /**
   * Compares in constant time, so the comparison does not reveal how many leading characters match.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof Secret that
        && MessageDigest.isEqual(
            value.getBytes(StandardCharsets.UTF_8), that.value.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return "<redacted>";
  }
}
