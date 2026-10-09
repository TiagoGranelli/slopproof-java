package com.example.slopproof.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ProblemHandlingTest {

  @Test
  void answersAnUndecodableQueryWithAGeneric400() {
    ResponseEntity<Object> response = new ProblemHandling().handleUndecodableParameter();

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail problem = (ProblemDetail) response.getBody();
    assertEquals(
        "a query parameter holds a value that cannot be read as text",
        problem == null ? null : problem.getDetail());
  }
}
