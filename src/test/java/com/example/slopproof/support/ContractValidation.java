package com.example.slopproof.support;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.MockMvcRequest;
import com.atlassian.oai.validator.mockmvc.MockMvcResponse;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.report.ValidationReport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Checks every exchange of an API test against {@value #SPECIFICATION}: the request, the response
 * of every status, and the problem bodies. A test that sends a request the contract refuses on
 * purpose marks it with {@link #refusedOnPurpose}, and then only the response is checked.
 */
public final class ContractValidation {

  /** The contract, relative to the project directory where Gradle runs the tests. */
  public static final String SPECIFICATION = "api/openapi.yaml";

  // A request attribute, so the mark travels with the exchange into the ResultMatcher.
  private static final String REFUSED_ON_PURPOSE =
      ContractValidation.class.getName() + ".refusedOnPurpose";

  private static final OpenApiInteractionValidator VALIDATOR =
      OpenApiInteractionValidator.createFor(SPECIFICATION).build();

  private ContractValidation() {}

  /**
   * Marks a request the contract refuses on purpose, such as a body that breaks the schema. Only
   * the response is then checked, because the request is wrong by design.
   */
  public static MockHttpServletRequestBuilder refusedOnPurpose(
      MockHttpServletRequestBuilder request) {
    return request.requestAttr(REFUSED_ON_PURPOSE, true);
  }

  /**
   * Fails the exchange when its response, or its request unless it is marked, breaks the contract.
   */
  static void check(MvcResult exchange) {
    MockHttpServletRequest request = exchange.getRequest();
    String path =
        Objects.requireNonNull(request.getRequestURI(), "the exchange has no request URI");
    Request.Method method = Request.Method.valueOf(request.getMethod());
    ValidationReport response =
        VALIDATOR.validateResponse(path, method, MockMvcResponse.of(exchange.getResponse()));
    ValidationReport report =
        isRefusedOnPurpose(request)
            ? response
            : VALIDATOR.validateRequest(MockMvcRequest.of(request)).merge(response);
    if (report.hasErrors()) {
      throw new AssertionError(describe(request, report));
    }
  }

  private static boolean isRefusedOnPurpose(HttpServletRequest request) {
    return request.getAttribute(REFUSED_ON_PURPOSE) != null;
  }

  private static String describe(HttpServletRequest request, ValidationReport report) {
    String problems =
        report.getMessages().stream()
            .map(ValidationReport.Message::getMessage)
            .collect(Collectors.joining("\n  "));
    return "%s %s does not conform to %s:%n  %s"
        .formatted(request.getMethod(), request.getRequestURI(), SPECIFICATION, problems);
  }
}
