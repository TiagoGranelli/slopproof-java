package com.example.slopproof.posts;

import static com.example.slopproof.db.Tables.POSTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.slopproof.support.EmbeddedPostgresConfiguration;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The posts queries against the real database: keyset order with ties, the fetch size, and deletes.
 * Each test starts from an empty table, so the shared database holds nothing between tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(EmbeddedPostgresConfiguration.class)
class PostRepositoryTest {

  private static final Instant SAME_TIME = Instant.parse("2026-10-09T12:00:00.123456Z");
  private static final UUID FIRST = UUID.fromString("00000000-0000-7000-8000-000000000001");
  private static final UUID SECOND = UUID.fromString("00000000-0000-7000-8000-000000000002");
  private static final UUID THIRD = UUID.fromString("00000000-0000-7000-8000-000000000003");

  private final PostRepository repository;
  private final DSLContext dsl;
  private final Clock clock = Clock.systemUTC();

  @Autowired
  PostRepositoryTest(PostRepository repository, DSLContext dsl) {
    this.repository = repository;
    this.dsl = dsl;
  }

  @BeforeEach
  void emptyPosts() {
    dsl.deleteFrom(POSTS).execute();
  }

  @Test
  void orderTiesOfOneCreationTimeByIdNewestFirst() {
    insertAt(FIRST, SAME_TIME);
    insertAt(SECOND, SAME_TIME);
    insertAt(THIRD, SAME_TIME);

    List<PostId> ids = idsOf(repository.findNewestFirst(null, 10));

    assertEquals(List.of(new PostId(THIRD), new PostId(SECOND), new PostId(FIRST)), ids);
  }

  @Test
  void resumesAfterATieWithoutSkippingOrRepeatingARow() {
    insertAt(FIRST, SAME_TIME);
    insertAt(SECOND, SAME_TIME);
    insertAt(THIRD, SAME_TIME);
    Post afterTheNewest = repository.findNewestFirst(null, 1).getFirst();

    List<Post> rest = repository.findNewestFirst(PostCursor.after(afterTheNewest), 10);

    assertEquals(List.of(new PostId(SECOND), new PostId(FIRST)), idsOf(rest));
  }

  @Test
  void fetchesUpToTheSizeAskedFor() {
    Instant base = Instant.parse("2026-10-09T12:00:00Z");
    insertAt(FIRST, base);
    insertAt(SECOND, base.plusSeconds(1));
    insertAt(THIRD, base.plusSeconds(2));

    // The service asks for one row beyond its page, so a fetch of limit + 1 must return that row.
    assertEquals(3, repository.findNewestFirst(null, 3).size());
    assertEquals(3, repository.findNewestFirst(null, 4).size());
    assertEquals(
        List.of(new PostId(THIRD), new PostId(SECOND)), idsOf(repository.findNewestFirst(null, 2)));
  }

  @Test
  void findsNothingForAMissingId() {
    assertTrue(repository.findById(new PostId(FIRST)).isEmpty());
  }

  @Test
  void insertReturnsThePostWithItsDatabaseIdAndTime() {
    Post stored = repository.insert("hello", "first post");

    assertEquals("hello", stored.title());
    assertTrue(Duration.between(stored.createdAt(), Instant.now(clock)).abs().toSeconds() < 60);
    assertEquals(stored, repository.findById(stored.id()).orElseThrow());
  }

  @Test
  void deleteReportsWhetherThePostExisted() {
    Post stored = repository.insert("hello", "first post");

    assertTrue(repository.deleteById(stored.id()));
    assertFalse(repository.deleteById(stored.id()));
    assertFalse(repository.deleteById(new PostId(FIRST)));
  }

  private void insertAt(UUID id, Instant createdAt) {
    dsl.insertInto(POSTS)
        .columns(POSTS.ID, POSTS.TITLE, POSTS.BODY, POSTS.CREATED_AT)
        .values(id, "title", "body", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
        .execute();
  }

  private static List<PostId> idsOf(List<Post> posts) {
    return posts.stream().map(Post::id).toList();
  }
}
