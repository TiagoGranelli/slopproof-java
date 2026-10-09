package com.example.slopproof.posts;

import static com.example.slopproof.db.Tables.POSTS;

import com.example.slopproof.db.tables.records.PostsRecord;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * The only class that reads or writes the posts table. Every value reaches SQL as a bind parameter.
 */
@Repository
public class PostRepository {

  private final DSLContext dsl;

  // The DSLContext is the bean Spring injects; this class never hands it out, so no caller can
  // change it.
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected bean, never returned to a caller")
  public PostRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Inserts a post; the database fills in its id and creation time. */
  public Post insert(String title, String body) {
    PostsRecord stored =
        dsl.insertInto(POSTS)
            .columns(POSTS.TITLE, POSTS.BODY)
            .values(title, body)
            .returning()
            .fetchSingle();
    return toPost(stored);
  }

  public Optional<Post> findById(PostId id) {
    return dsl.selectFrom(POSTS)
        .where(POSTS.ID.eq(id.value()))
        .fetchOptional()
        .map(PostRepository::toPost);
  }

  /** Up to {@code fetchSize} posts older than {@code after}, or from the newest when it is null. */
  public List<Post> findNewestFirst(@Nullable PostCursor after, int fetchSize) {
    return dsl.selectFrom(POSTS)
        .where(olderThan(after))
        .orderBy(POSTS.CREATED_AT.desc(), POSTS.ID.desc())
        .limit(fetchSize)
        .fetch(PostRepository::toPost);
  }

  /** Deletes the post and reports whether one existed. */
  public boolean deleteById(PostId id) {
    return dsl.deleteFrom(POSTS).where(POSTS.ID.eq(id.value())).execute() > 0;
  }

  // Compares the pair, not the timestamp alone: two posts can share a creation time, and the id
  // breaks the tie.
  private static Condition olderThan(@Nullable PostCursor after) {
    if (after == null) {
      return DSL.noCondition();
    }
    OffsetDateTime createdAt = OffsetDateTime.ofInstant(after.createdAt(), ZoneOffset.UTC);
    return DSL.row(POSTS.CREATED_AT, POSTS.ID).lt(createdAt, after.id());
  }

  private static Post toPost(PostsRecord stored) {
    return new Post(
        new PostId(stored.getId()),
        stored.getTitle(),
        stored.getBody(),
        stored.getCreatedAt().toInstant());
  }
}
