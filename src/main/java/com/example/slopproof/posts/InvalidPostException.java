package com.example.slopproof.posts;

import com.example.slopproof.shared.ProblemException;
import org.springframework.http.HttpStatus;

/**
 * A post the business rules refuse. The contract's length limits do not catch whitespace-only text
 * or characters Postgres cannot store, so the service checks for them.
 */
public final class InvalidPostException extends ProblemException {

  private static final long serialVersionUID = 1L;

  InvalidPostException(String detail) {
    super(problem(HttpStatus.BAD_REQUEST, "invalid-post", "Invalid post", detail), null);
  }
}
