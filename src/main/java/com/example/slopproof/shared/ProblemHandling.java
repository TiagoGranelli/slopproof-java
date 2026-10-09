package com.example.slopproof.shared;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Answers every error of the API as {@code application/problem+json}, the shape of the contract's
 * Problem schema. The details this class writes name the field or parameter that was wrong and
 * never echo the rejected value, because a request body can hold a secret. Spring's own details for
 * an unknown route, method or media type can repeat the request's path or type, and the {@code
 * instance} field repeats the path. All of that came from the client, and only that client sees it.
 */
@RestControllerAdvice
public class ProblemHandling extends ResponseEntityExceptionHandler {

  // Tomcat raises one exception for a value that is not text and for too many parameters, so the
  // detail names both. Public so the test checks the text without copying it.
  public static final String UNDECODABLE_QUERY_DETAIL =
      "the query string cannot be read: a value is not text, or there are too many parameters";

  private static final Logger log = LoggerFactory.getLogger(ProblemHandling.class);

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String detail =
        exception.getBindingResult().getFieldErrors().stream()
            .map(ProblemHandling::describe)
            .collect(Collectors.joining("; "));
    return problemResponse(status, headers, detail);
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String name = Objects.requireNonNullElse(exception.getPropertyName(), "parameter");
    Class<?> requiredType = exception.getRequiredType();
    String type = requiredType == null ? "the expected type" : requiredType.getSimpleName();
    String detail = "%s must be a value of type %s".formatted(name, type);
    return problemResponse(status, headers, detail);
  }

  /**
   * The controllers implement a {@code @Validated} interface, so Spring validates their parameters
   * with AOP and raises this instead of Spring MVC's own method validation exception.
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception) {
    String detail =
        exception.getConstraintViolations().stream()
            .map(ProblemHandling::describe)
            .collect(Collectors.joining("; "));
    return problemResponse(HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY, detail);
  }

  /**
   * Tomcat refuses a query string it cannot decode, and its message quotes the raw value, which can
   * hold a newline that forges a log line. So this answers 400 without the message, and logs
   * nothing.
   */
  @ExceptionHandler(InvalidParameterException.class)
  public ResponseEntity<Object> handleUndecodableParameter() {
    return problemResponse(HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY, UNDECODABLE_QUERY_DETAIL);
  }

  /**
   * The last resort: the cause goes to the log, and the client gets a generic 500 without the
   * message.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
    log.error("Request failed with an unexpected error", exception);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "The server could not process the request.");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
  }

  private static ResponseEntity<Object> problemResponse(
      HttpStatusCode status, HttpHeaders headers, String detail) {
    return ResponseEntity.status(status)
        .headers(headers)
        .body(ProblemDetail.forStatusAndDetail(status, detail));
  }

  private static String describe(FieldError error) {
    return error.getField() + ": " + error.getDefaultMessage();
  }

  // The property path reads "method.parameter", so the last segment is the parameter's name.
  private static String describe(ConstraintViolation<?> violation) {
    String path = violation.getPropertyPath().toString();
    return path.substring(path.lastIndexOf('.') + 1) + ": " + violation.getMessage();
  }
}
