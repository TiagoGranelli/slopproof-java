package com.example.slopproof.posts;

import com.example.slopproof.shared.ProblemException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

/**
 * A cursor that is not one {@link PostCursor#encode()} wrote: a foreign string, or one a client
 * edited.
 */
public final class InvalidCursorException extends ProblemException {

  private static final long serialVersionUID = 1L;

  InvalidCursorException(String cursor, @Nullable Throwable cause) {
    super(
        problem(
            HttpStatus.BAD_REQUEST,
            "invalid-cursor",
            "Invalid cursor",
            "cursor must be a nextCursor from a previous page (got %d characters)"
                .formatted(cursor.length())),
        cause);
  }
}
