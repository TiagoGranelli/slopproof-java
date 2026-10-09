package com.example.slopproof.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Applies the contract check and the hit recorder to every MockMvc exchange of the test, so a test
 * cannot skip them by forgetting a call.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContractValidationConfiguration {

  @Bean
  MockMvcBuilderCustomizer contractValidation() {
    return builder -> {
      builder.alwaysExpect(ContractValidation::check);
      builder.alwaysDo(ContractHits::record);
    };
  }
}
