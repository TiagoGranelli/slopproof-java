package com.example.slopproof.posts;

import static com.example.slopproof.support.ContractValidation.refusedOnPurpose;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.slopproof.shared.JsonInputConfiguration;
import com.example.slopproof.support.ApiTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The create body against the rules the contract cannot express: text that is only whitespace,
 * control characters, and JSON that the parser or the contract refuses. Each refusal is a 400 with
 * a problem document, never a 500.
 */
@ApiTest
class PostInputTest {

  private static final String POSTS = "/api/posts";

  private final MockMvc mvc;

  @Autowired
  PostInputTest(MockMvc mvc) {
    this.mvc = mvc;
  }

  @Test
  void refusesBlankTitleAndBody() throws Exception {
    refusedRule("{\"title\":\"   \\t\\n \",\"body\":\"x\"}", "title must not be blank");
    refusedRule("{\"title\":\"x\",\"body\":\" \\u2003 \"}", "body must not be blank");
  }

  @Test
  void refusesControlCharactersInTheTitle() throws Exception {
    refusedRule(
        "{\"title\":\"a\\u0000b\",\"body\":\"x\"}", "title must not contain control characters");
    refusedRule(
        "{\"title\":\"a\\tb\",\"body\":\"x\"}", "title must not contain control characters");
  }

  @Test
  void refusesControlCharactersInTheBodyExceptLineLayout() throws Exception {
    refusedRule(
        "{\"title\":\"x\",\"body\":\"a\\u001b[2J\"}",
        "body must not contain control characters except tab, line feed and carriage return");
  }

  @Test
  void acceptsTabsAndLineBreaksInTheBody() throws Exception {
    created("{\"title\":\"x\",\"body\":\"one\\ttwo\\nthree\\r\\nfour\"}");
  }

  @Test
  void acceptsTextThatUsesJoinersAndScriptsCorrectly() throws Exception {
    // Persian with a zero-width non-joiner, a Hindi conjunct, and an emoji joined by a zero-width
    // joiner.
    created("{\"title\":\"می\\u200cخواهم\",\"body\":\"क्षत्रिय\"}");
    created(
        "{\"title\":\"\\ud83d\\udc68\\u200d\\ud83d\\udc69\\u200d\\ud83d\\udc67\",\"body\":\"x\"}");
  }

  @Test
  void acceptsASurrogatePairAsOneCharacter() throws Exception {
    created("{\"title\":\"\\ud83d\\ude00\",\"body\":\"x\"}");
  }

  @Test
  void refusesPropertiesTheContractDoesNotName() throws Exception {
    refusedByParser(
        "{\"title\":\"a\",\"body\":\"b\",\"id\":\"00000000-0000-0000-0000-000000000000\"}");
  }

  @Test
  void refusesNumbersReadAsText() throws Exception {
    refusedByParser("{\"title\":123,\"body\":\"b\"}");
  }

  @Test
  void refusesRepeatedKeys() throws Exception {
    refusedByParser("{\"title\":\"a\",\"title\":\"b\",\"body\":\"x\"}");
  }

  @Test
  void refusesTextPastTheStringLimit() throws Exception {
    String oversized = "x".repeat(JsonInputConfiguration.MAX_STRING_LENGTH + 1);
    refusedByParser("{\"title\":\"a\",\"body\":\"" + oversized + "\"}");
  }

  @Test
  void refusesDocumentsPastTheLengthLimit() throws Exception {
    String padding = " ".repeat((int) JsonInputConfiguration.MAX_DOCUMENT_LENGTH);
    refusedByParser(padding + "{\"title\":\"a\",\"body\":\"x\"}");
  }

  @Test
  void refusesMalformedUtf8BeforeAnythingIsStored() throws Exception {
    // A lone continuation byte is not UTF-8, so the parser stops at it.
    refusedByParser(bodyWithBytesInside((byte) 0x80));
  }

  @Test
  void refusesAnEncodedSurrogate() throws Exception {
    // ED A0 80 encodes U+D800, which UTF-8 reserves for UTF-16 surrogates.
    refusedByParser(bodyWithBytesInside((byte) 0xED, (byte) 0xA0, (byte) 0x80));
  }

  private static byte[] bodyWithBytesInside(byte... inserted) {
    byte[] head = "{\"title\":\"a\",\"body\":\"".getBytes(StandardCharsets.UTF_8);
    byte[] tail = "\"}".getBytes(StandardCharsets.UTF_8);
    byte[] body = new byte[head.length + inserted.length + tail.length];
    System.arraycopy(head, 0, body, 0, head.length);
    System.arraycopy(inserted, 0, body, head.length, inserted.length);
    System.arraycopy(tail, 0, body, head.length + inserted.length, tail.length);
    return body;
  }

  @Test
  void refusesARawNulByte() throws Exception {
    refusedByParser("{\"title\":\"a\",\"body\":\"x\"}\0");
  }

  private void refusedRule(String json, String detail) throws Exception {
    mvc.perform(post(POSTS).contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(startsWith(detail)));
  }

  private void created(String json) throws Exception {
    mvc.perform(post(POSTS).contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isCreated());
  }

  private ResultActions refusedByParser(String json) throws Exception {
    return refusedByParser(json.getBytes(StandardCharsets.UTF_8));
  }

  private ResultActions refusedByParser(byte[] json) throws Exception {
    return mvc.perform(
            refusedOnPurpose(post(POSTS).contentType(MediaType.APPLICATION_JSON).content(json)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }
}
