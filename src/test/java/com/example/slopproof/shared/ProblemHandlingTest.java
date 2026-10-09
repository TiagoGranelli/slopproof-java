package com.example.slopproof.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.slopproof.support.EmbeddedPostgresConfiguration;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Drives a failure through the dispatcher, because the handler only runs there. MockMvc cannot
 * produce Tomcat's exception itself, so a test controller throws it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({EmbeddedPostgresConfiguration.class, ProblemHandlingTest.UndecodableController.class})
class ProblemHandlingTest {

  private final MockMvc mvc;

  @Autowired
  ProblemHandlingTest(MockMvc mvc) {
    this.mvc = mvc;
  }

  @Test
  void answersATomcatDecodingFailureWithAGeneric400() throws Exception {
    mvc.perform(get("/test/undecodable-query"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ProblemHandling.UNDECODABLE_QUERY_DETAIL));
  }

  @RestController
  static class UndecodableController {

    @GetMapping("/test/undecodable-query")
    void throwUndecodable() {
      throw new InvalidParameterException("Character decoding failed. Parameter [cursor] ignored");
    }
  }
}
