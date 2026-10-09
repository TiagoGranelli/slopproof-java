package com.example.slopproof.posts;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.slopproof.shared.JsonInputConfiguration;
import com.example.slopproof.shared.StrictUtf8JsonFilter;
import com.example.slopproof.support.EmbeddedPostgresConfiguration;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
 * characters Postgres refuses or that hide text, and JSON the contract does not describe. Each
 * refusal is a 400 with a problem document, never a 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfiguration.class)
class PostInputTest {

  private static final String POSTS = "/api/posts";

  private static final String CHARACTERS_REFUSED =
      "%s must not contain control, bidi, hidden or unpaired surrogate characters";

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
  void refusesCharactersThatHideOrReorderText() throws Exception {
    // U+E0080 is unassigned and default-ignorable, so it draws nothing.
    refused("{\"title\":\"\\udb40\\udc80\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
    // A right-to-left override reorders the text after it, so the title on screen differs from the
    // stored one.
    refused("{\"title\":\"a\\u202eb\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
    // U+E0049 is a tag, which spells ASCII that no renderer shows.
    refused("{\"title\":\"a\\udb40\\udc49\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
    // A noncharacter is reserved for software and never text.
    refused("{\"title\":\"a\\uffff\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
  }

  @Test
  void refusesInvisibleCharactersBetweenVisibleOnes() throws Exception {
    // Each of these draws nothing, so it could carry a bit of hidden text between two letters.
    refused("{\"title\":\"a\\u200bb\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
    refused("{\"title\":\"a\\u200cb\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
    refused("{\"title\":\"a\\u00adb\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
    refused("{\"title\":\"a\\u180bb\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
    refused("{\"title\":\"a\\u2061b\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
  }

  @Test
  void refusesAJoinerOutsideAnEmojiSequence() throws Exception {
    // A zero-width joiner joins two emoji and nothing else.
    refused("{\"title\":\"a\\u200db\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
  }

  @Test
  void refusesMisplacedOrRepeatedSelectors() throws Exception {
    // A non-emoji selector hides data, whatever it follows.
    refused("{\"title\":\"a\\ufe00\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
    // A presentation selector needs a character before it and takes no second selector.
    refused("{\"title\":\"\\ufe0fa\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
    refused("{\"title\":\"a\\ufe0f\\ufe0f\",\"body\":\"x\"}")
        .andExpect(jsonPath("$.detail").value(CHARACTERS_REFUSED.formatted("title")));
  }

  @Test
  void acceptsTextThatUsesNewerCharactersAndSelectorsCorrectly() throws Exception {
    // U+20C1 is newer than the JDK's Unicode table; U+F8FF is the Apple logo, in private use.
    mvc.perform(
            post(POSTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"100 \\u20c1 \\uf8ff\",\"body\":\"x\"}"))
        .andExpect(status().isCreated());
    // An emoji presentation selector after its character is ordinary.
    mvc.perform(
            post(POSTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\\u2764\\ufe0f\",\"body\":\"x\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void acceptsEmojiJoinedByJoiners() throws Exception {
    // A family (man, woman, girl) and a rainbow flag, which joins a selector-marked flag to a
    // rainbow.
    mvc.perform(
            post(POSTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"\\ud83d\\udc68\\u200d\\ud83d\\udc69\\u200d\\ud83d\\udc67\","
                        + "\"body\":\"x\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post(POSTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"\\ud83c\\udff3\\ufe0f\\u200d\\ud83c\\udf08\",\"body\":\"x\"}"))
        .andExpect(status().isCreated());
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
  void refusesAnOverlongEncodingBeforeAnythingParsesIt() throws Exception {
    // C0 BC is an overlong '<': Jackson would read it, and a proxy would not.
    byte[] head = "{\"title\":\"".getBytes(StandardCharsets.UTF_8);
    byte[] overlong = {(byte) 0xC0, (byte) 0xBC};
    byte[] tail = "\",\"body\":\"x\"}".getBytes(StandardCharsets.UTF_8);
    byte[] body =
        ByteBuffer.allocate(head.length + overlong.length + tail.length)
            .put(head)
            .put(overlong)
            .put(tail)
            .array();
    refused(body).andExpect(jsonPath("$.detail").value(StrictUtf8JsonFilter.REFUSED_DETAIL));
  }

  @Test
  void refusesARawNulByte() throws Exception {
    refused("{\"title\":\"a\",\"body\":\"x\"}\0")
        .andExpect(jsonPath("$.detail").value(StrictUtf8JsonFilter.REFUSED_DETAIL));
  }

  @Test
  void refusesDeclaredCharsetsOtherThanUtf8() throws Exception {
    // Under Shift_JIS a backslash can be the second byte of a character, so an escape can change.
    byte[] json = "{\"title\":\"a\",\"body\":\"x\"}".getBytes(StandardCharsets.UTF_8);
    refusedAs("application/json; charset=Shift_JIS", json)
        .andExpect(jsonPath("$.detail").value(StrictUtf8JsonFilter.REFUSED_DETAIL));
    refusedAs("application/json; charset=ISO-8859-1", json)
        .andExpect(jsonPath("$.detail").value(StrictUtf8JsonFilter.REFUSED_DETAIL));
    // A repeated charset is read as the last one, so it is refused even when the first is UTF-8.
    refusedAs("application/json; charset=utf-8; charset=Shift_JIS", json)
        .andExpect(jsonPath("$.detail").value(StrictUtf8JsonFilter.REFUSED_DETAIL));
  }

  @Test
  void acceptsUtf8DeclaredAsCharset() throws Exception {
    mvc.perform(
            post(POSTS)
                .contentType("application/json; charset=UTF-8")
                .content("{\"title\":\"a\",\"body\":\"x\"}"))
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
        .andExpect(jsonPath("$.detail").value(StrictUtf8JsonFilter.REFUSED_DETAIL));
  }

  private ResultActions refused(String json) throws Exception {
    return refused(json.getBytes(StandardCharsets.UTF_8));
  }

  private ResultActions refused(byte[] json) throws Exception {
    return refusedAs(MediaType.APPLICATION_JSON_VALUE, json);
  }

  private ResultActions refusedAs(String contentType, byte[] json) throws Exception {
    return mvc.perform(post(POSTS).contentType(contentType).content(json))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }
}
