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

  private static final String CHARACTERS_REFUSED =
      "%s must not contain control, unassigned, private-use, bidi or unpaired surrogate characters";

  private final MockMvc mvc;

  @Autowired
  PostInputTest(MockMvc mvc) {
    this.mvc = mvc;
  }

  @Test
  void refusesControlsAndLoneSurrogatesInText() throws Exception {
    // The JSON escapes these, so the database never sees a raw NUL, escape or lone surrogate.
    refused("{\"title\":\"a\\u0000b\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
    refused("{\"title\":\"x\",\"body\":\"\\ud800\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("body")));
    refused("{\"title\":\"x\",\"body\":\"\\u001b[2J\"}");
  }

  @Test
  void refusesUnassignedPrivateUseAndBidiCharacters() throws Exception {
    // U+E0080 is unassigned and default-ignorable, so it draws nothing and dodges the blank check.
    refused("{\"title\":\"\\udb40\\udc80\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
    refused("{\"title\":\"a\\ue000\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
    // A right-to-left override reorders the text after it, so the title on screen differs from the
    // stored one.
    refused("{\"title\":\"a\\u202eb\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
  }

  @Test
  void refusesTextThatOnlyLooksEmpty() throws Exception {
    refused("{\"title\":\"\\u00a0\\u200b\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value("title must not be blank (got 2 characters)"));
    // A Hangul filler and a combining mark show nothing either.
    refused("{\"title\":\"\\u3164\\u034f\",\"body\":\"x\"}")
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
  void refusesRepeatedKeys() throws Exception {
    refused("{\"title\":\"a\",\"title\":\"b\",\"body\":\"x\"}");
  }

  @Test
  void refusesTextPastTheStringLimit() throws Exception {
    // @Size would refuse this body too, so the detail is Jackson's: that shows the cap ran first.
    String oversized = "x".repeat(JsonInputConfiguration.MAX_STRING_LENGTH + 1);
    refused("{\"title\":\"a\",\"body\":\"" + oversized + "\"}")
        .andExpect(jsonPath("$.detail").value("Failed to read request"));
  }

  @Test
  void refusesDocumentsPastTheLengthLimit() throws Exception {
    String padding = " ".repeat((int) JsonInputConfiguration.MAX_DOCUMENT_LENGTH);
    refused(padding + "{\"title\":\"a\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value("Failed to read request"));
  }

  private ResultActions refused(String json) throws Exception {
    return mvc.perform(post(POSTS).contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }
}
