package com.example.slopproof.posts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.slopproof.support.EmbeddedPostgresConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Runs the posts API end to end on the embedded database: the happy path, paging, and the 4xx
 * answers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfiguration.class)
class PostsSmokeTest {

  private static final String POSTS = "/api/posts";

  private final MockMvc mvc;

  @Autowired
  PostsSmokeTest(MockMvc mvc) {
    this.mvc = mvc;
  }

  @Test
  void createsGetsListsAndDeletesAPost() throws Exception {
    String location = createPost("hello", "first post");

    mvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("hello"));
    mvc.perform(get(POSTS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].title").value("hello"));
    mvc.perform(delete(location)).andExpect(status().isNoContent());
    mvc.perform(delete(location)).andExpect(status().isNotFound());
    mvc.perform(get(location))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:slopproof:problem:post-not-found"));
  }

  @Test
  void pagesThroughEveryPostNewestFirst() throws Exception {
    String first = createPost("first", "a");
    String second = createPost("second", "b");
    String third = createPost("third", "c");

    List<String> ids = listEveryId();

    assertEquals(ids.size(), ids.stream().distinct().count());
    assertTrue(ids.indexOf(idOf(third)) < ids.indexOf(idOf(second)));
    assertTrue(ids.indexOf(idOf(second)) < ids.indexOf(idOf(first)));
  }

  @Test
  void answersMalformedRequestsWithProblemDetails() throws Exception {
    mvc.perform(get(POSTS + "?cursor=not-a-cursor"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:slopproof:problem:invalid-cursor"));
    mvc.perform(get(POSTS + "?limit=0"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    mvc.perform(get(POSTS + "/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("postId must be a value of type UUID"));
    mvc.perform(
            post(POSTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"only a title\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    // The contract's length limits accept whitespace, so the business rule has to refuse it.
    mvc.perform(
            post(POSTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \",\"body\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("title must not be blank (got 3 characters)"));
  }

  private String createPost(String title, String body) throws Exception {
    MvcResult created =
        mvc.perform(
                post(POSTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"" + title + "\",\"body\":\"" + body + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();
    return Objects.requireNonNull(
        created.getResponse().getHeader("Location"), "201 without a Location header");
  }

  // Follows nextCursor until the last page, so the test covers every row, not one page of it.
  private List<String> listEveryId() throws Exception {
    List<String> ids = new ArrayList<>();
    String url = POSTS + "?limit=2";
    while (url != null) {
      String body =
          mvc.perform(get(url))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      List<String> pageIds = JsonPath.read(body, "$.items[*].id");
      ids.addAll(pageIds);
      // $..nextCursor is a deep scan, so an absent field gives an empty list rather than an error.
      List<String> cursors = JsonPath.read(body, "$..nextCursor");
      url = cursors.isEmpty() ? null : POSTS + "?limit=2&cursor=" + cursors.getFirst();
    }
    return ids;
  }

  private static String idOf(String location) {
    return location.substring(location.lastIndexOf('/') + 1);
  }
}
