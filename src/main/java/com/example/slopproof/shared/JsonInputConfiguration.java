package com.example.slopproof.shared;

import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.MutableCoercionConfig;
import tools.jackson.databind.type.LogicalType;

/**
 * Holds request JSON to the contract: no text longer than the contract allows, no property the
 * contract does not name, and no number or boolean read as text. The string cap also bounds the
 * memory one request can hold, because a bound value is kept until validation runs.
 */
@Configuration(proxyBeanMethods = false)
public class JsonInputConfiguration {

  // The longest text the contract accepts is a post body, at 10000 characters.
  public static final int MAX_STRING_LENGTH = 10_000;

  @Bean
  JsonFactoryBuilderCustomizer boundedStrings() {
    return builder ->
        builder.streamReadConstraints(
            StreamReadConstraints.builder().maxStringLength(MAX_STRING_LENGTH).build());
  }

  // Ordered last so that Spring Boot's own defaults cannot switch these back on.
  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  JsonMapperBuilderCustomizer strictReads() {
    return builder ->
        builder
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .withCoercionConfig(LogicalType.Textual, JsonInputConfiguration::refuseScalarsAsText);
  }

  // Jackson turns 123 into "123" by default; a client that sends a number for a text field is
  // wrong.
  private static void refuseScalarsAsText(MutableCoercionConfig config) {
    config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
    config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
    config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
  }
}
