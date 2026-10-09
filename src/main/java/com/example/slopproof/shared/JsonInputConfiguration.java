package com.example.slopproof.shared;

import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.MutableCoercionConfig;
import tools.jackson.databind.type.LogicalType;

/**
 * Holds request JSON to the contract: no text longer than the contract allows, no document longer
 * than a post can be, no property the contract does not name, no duplicate key, and no number or
 * boolean read as text. The limits bound what one request can hold in memory, because a bound value
 * is kept until validation runs. They apply to every JSON read through the shared mapper.
 */
@Configuration(proxyBeanMethods = false)
public class JsonInputConfiguration {

  // The longest text the contract accepts is a post body, at 10000 characters.
  public static final int MAX_STRING_LENGTH = 10_000;

  // A post body escaped character by character is at most 6 bytes a character, plus its title.
  public static final long MAX_DOCUMENT_LENGTH = 256L * 1024;

  @Bean
  JsonFactoryBuilderCustomizer boundedStrings() {
    return builder ->
        builder.streamReadConstraints(
            StreamReadConstraints.builder()
                .maxStringLength(MAX_STRING_LENGTH)
                .maxDocumentLength(MAX_DOCUMENT_LENGTH)
                .build());
  }

  // Ordered last so that Spring Boot's own defaults cannot switch these back on. A repeated key
  // would otherwise let the last value win, hiding the first from a proxy that reads it.
  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  JsonMapperBuilderCustomizer strictReads() {
    return builder ->
        builder
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
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
