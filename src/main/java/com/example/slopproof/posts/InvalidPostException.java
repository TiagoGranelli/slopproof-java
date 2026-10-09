package com.example.slopproof.posts;

import com.example.slopproof.shared.ProblemException;
import org.springframework.http.HttpStatus;

/**
 * A post the business rules refuse. The contract's length limits do not catch whitespace-only text,
 * so the service checks for it.
 */
public final class InvalidPostException extends ProblemException {

  private static final long serialVersionUID = 1L;

  InvalidPostException(String field, int length) {
    super(
        problem(
            HttpStatus.BAD_REQUEST,
            "invalid-post",
            "Invalid post",
            "%s must not be blank (got %d characters)".formatted(field, length)),
        null);
  }
}
