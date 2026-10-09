package com.example.slopproof.shared;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A failure that answers with its own status and an RFC 9457 problem body. The type is a stable
 * URN, so a client can tell the failures apart without parsing the detail.
 */
public abstract class ProblemException extends ErrorResponseException {

  private static final long serialVersionUID = 1L;

  /**
   * Answers with the status and body of {@code problem}; the cause is kept for the stack trace, not
   * sent.
   */
  protected ProblemException(ProblemDetail problem, @Nullable Throwable cause) {
    super(HttpStatusCode.valueOf(problem.getStatus()), problem, cause);
  }

  /**
   * A problem body whose type is {@code urn:slopproof:problem:<slug>}, e.g. {@code post-not-found}.
   */
  protected static ProblemDetail problem(
      HttpStatus status, String slug, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("urn:slopproof:problem:" + slug));
    problem.setTitle(title);
    return problem;
  }
}
