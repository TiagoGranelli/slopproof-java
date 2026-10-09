package com.example.slopproof.posts;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.slopproof.shared.JsonInputConfiguration;
import com.example.slopproof.support.EmbeddedPostgresConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Checks the create body against the rules the contract cannot express: text that only looks empty,
 * characters Postgres refuses, and JSON the contract does not describe. Each refusal is a 400 with
 * a problem document, never a 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfiguration.class)
class PostInputTest {

  private static final String POSTS = "/api/posts";

  private final MockMvc mvc;

  @Autowired
  PostInputTest(MockMvc mvc) {
    this.mvc = mvc;
  }

  @Test
  void refusesNulAndLoneSurrogatesInText() throws Exception {
    // The JSON escapes these, so the database never sees a raw NUL or a lone surrogate.
    refused("{\"title\":\"a\\u0000b\",\"body\":\"x\"}")
        .andExpect(
            jsonPath("$.detail")
                .value("title must not contain NUL or unpaired surrogate characters"));
    refused("{\"title\":\"x\",\"body\":\"\\ud800\"}")
        .andExpect(
            jsonPath("$.detail")
                .value("body must not contain NUL or unpaired surrogate characters"));
  }

  @Test
  void refusesTextThatOnlyLooksEmpty() throws Exception {
    refused("{\"title\":\"\\u00a0\\u200b\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value("title must not be blank (got 2 characters)"));
  }

  @Test
  void acceptsASurrogatePairAsOneCharacter() throws Exception {
    mvc.perform(
            post(POSTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\\ud83d\\ude00\",\"body\":\"x\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void refusesPropertiesTheContractDoesNotName() throws Exception {
    refused("{\"title\":\"a\",\"body\":\"b\",\"id\":\"00000000-0000-0000-0000-000000000000\"}");
  }

  @Test
  void refusesNumbersReadAsText() throws Exception {
    refused("{\"title\":123,\"body\":\"b\"}");
  }

  @Test
  void refusesTextPastTheStringLimit() throws Exception {
    String oversized = "x".repeat(JsonInputConfiguration.MAX_STRING_LENGTH + 1);
    refused("{\"title\":\"a\",\"body\":\"" + oversized + "\"}");
  }

  private ResultActions refused(String json) throws Exception {
    return mvc.perform(post(POSTS).contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }
}
