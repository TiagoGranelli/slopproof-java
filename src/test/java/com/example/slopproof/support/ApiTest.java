package com.example.slopproof.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * An HTTP test of the API: the whole application on the shared database, driven through MockMvc.
 * Every exchange is checked against {@code api/openapi.yaml} and recorded for the contract coverage
 * gate. See {@code src/test/AGENTS.md}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest
@AutoConfigureMockMvc
@Import({EmbeddedPostgresConfiguration.class, ContractValidationConfiguration.class})
public @interface ApiTest {}
