package com.example.slopproof.posts;

import static com.example.slopproof.db.Tables.POSTS;
import static com.example.slopproof.support.ContractValidation.refusedOnPurpose;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.slopproof.support.ApiTest;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Every operation of the posts API, with each status its contract documents. Every response is
 * checked against the contract by {@link ApiTest}, problem bodies included.
 */
@ApiTest
class PostsApiTest {

  private static final String POSTS_PATH = "/api/posts";
  private static final String MISSING_ID = "00000000-0000-0000-0000-000000000000";

  private final MockMvc mvc;
  private final DSLContext dsl;

  @Autowired
  PostsApiTest(MockMvc mvc, DSLContext dsl) {
    this.mvc = mvc;
    this.dsl = dsl;
  }

  @BeforeEach
  void emptyPosts() {
    dsl.deleteFrom(POSTS).execute();
  }

  @Test
  void createPostAnswers201WithTheLocationOfThePost() throws Exception {
    String location = createPost("hello", "first post");

    mvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("hello"))
        .andExpect(jsonPath("$.body").value("first post"));
  }

  @Test
  void createPostAnswers400ForARequestTheContractOrTheRulesRefuse() throws Exception {
    mvc.perform(
            refusedOnPurpose(
                post(POSTS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"x\"}")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    // The contract's length limits accept whitespace, so the business rule refuses it.
    mvc.perform(json("{\"title\":\"   \",\"body\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("title must not be blank (got 3 characters)"));
    mvc.perform(json("{\"title\":\"a\",\"body\":\"x\\u0001\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:slopproof:problem:invalid-post"));
  }

  @Test
  void listPostsAnswers200WithAnEmptyPageWhenThereAreNoPosts() throws Exception {
    mvc.perform(get(POSTS_PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void listPostsPagesThroughEveryPostNewestFirst() throws Exception {
    String first = createPost("first", "a");
    String second = createPost("second", "b");
    String third = createPost("third", "c");

    List<String> ids = listEveryId();

    assertEquals(ids.size(), ids.stream().distinct().count());
    assertTrue(ids.indexOf(idOf(third)) < ids.indexOf(idOf(second)));
    assertTrue(ids.indexOf(idOf(second)) < ids.indexOf(idOf(first)));
  }

  @Test
  void listPostsSetsNoNextCursorWhenThePostsFitTheLimitExactly() throws Exception {
    createPost("first", "a");
    createPost("second", "b");

    mvc.perform(get(POSTS_PATH + "?limit=2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void listPostsAnswers400ForAMalformedQuery() throws Exception {
    mvc.perform(refusedOnPurpose(get(POSTS_PATH + "?limit=0")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    mvc.perform(get(POSTS_PATH + "?cursor=not-a-cursor"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:slopproof:problem:invalid-cursor"));
  }

  @Test
  void getPostAnswers200WithThePost() throws Exception {
    String location = createPost("hello", "first post");

    mvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(idOf(location)));
  }

  @Test
  void getPostAnswers400ForAnIdThatIsNotAUuid() throws Exception {
    mvc.perform(refusedOnPurpose(get(POSTS_PATH + "/not-a-uuid")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("postId must be a value of type UUID"));
  }

  @Test
  void getPostAnswers404ForAMissingPost() throws Exception {
    mvc.perform(get(POSTS_PATH + "/" + MISSING_ID))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:slopproof:problem:post-not-found"));
  }

  @Test
  void deletePostAnswers204ThenMissing404() throws Exception {
    String location = createPost("hello", "first post");

    mvc.perform(delete(location)).andExpect(status().isNoContent());
    mvc.perform(delete(location))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:slopproof:problem:post-not-found"));
    mvc.perform(get(location)).andExpect(status().isNotFound());
  }

  @Test
  void deletePostAnswers400ForAnIdThatIsNotAUuid() throws Exception {
    mvc.perform(refusedOnPurpose(delete(POSTS_PATH + "/not-a-uuid")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  private static MockHttpServletRequestBuilder json(String body) {
    return post(POSTS_PATH).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private String createPost(String title, String body) throws Exception {
    MvcResult created =
        mvc.perform(json("{\"title\":\"" + title + "\",\"body\":\"" + body + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();
    return Objects.requireNonNull(
        created.getResponse().getHeader("Location"), "201 without a Location header");
  }

  // Follows nextCursor until the last page, so the test covers every row, not one page of it.
  private List<String> listEveryId() throws Exception {
    List<String> ids = new ArrayList<>();
    String url = POSTS_PATH + "?limit=2";
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
      url = cursors.isEmpty() ? null : POSTS_PATH + "?limit=2&cursor=" + cursors.getFirst();
    }
    return ids;
  }

  private static String idOf(String location) {
    return location.substring(location.lastIndexOf('/') + 1);
  }
}
