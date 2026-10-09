package com.example.slopproof.posts;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rules of posts and the transactions they run in. The controller calls this, never the
 * repository.
 */
@Service
public class PostService {

  private final PostRepository repository;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected bean, never returned to a caller")
  public PostService(PostRepository repository) {
    this.repository = repository;
  }

  /**
   * Creates a post. The contract's length limits accept blank text and control characters, so the
   * rules here refuse them before the database sees them.
   */
  @Transactional
  public Post create(String title, String body) {
    requireNotBlank("title", title);
    requireNotBlank("body", body);
    if (title.codePoints().anyMatch(Character::isISOControl)) {
      throw new InvalidPostException("title must not contain control characters");
    }
    if (body.codePoints().anyMatch(PostService::isBodyControl)) {
      throw new InvalidPostException(
          "body must not contain control characters except tab, line feed and carriage return");
    }
    return repository.insert(title, body);
  }

  @Transactional(readOnly = true)
  public Post get(PostId id) {
    return repository.findById(id).orElseThrow(() -> new PostNotFoundException(id));
  }

  /**
   * One page of at most {@code limit} posts after {@code after}; the cursor of the next page is set
   * when there is one.
   */
  @Transactional(readOnly = true)
  public PostPage list(int limit, @Nullable PostCursor after) {
    // One row beyond the page tells whether a next page exists, without a second query.
    List<Post> fetched = repository.findNewestFirst(after, limit + 1);
    if (fetched.size() <= limit) {
      return new PostPage(fetched, null);
    }
    List<Post> page = fetched.subList(0, limit);
    return new PostPage(page, PostCursor.after(page.getLast()));
  }

  @Transactional
  public void delete(PostId id) {
    if (!repository.deleteById(id)) {
      throw new PostNotFoundException(id);
    }
  }

  // String.strip() is the trim the rules name; a text of only spaces is blank.
  private static void requireNotBlank(String field, String value) {
    if (value.strip().isEmpty()) {
      throw new InvalidPostException(
          "%s must not be blank (got %d characters)".formatted(field, value.length()));
    }
  }

  // A body may break lines and indent, and nothing else that is a control character.
  private static boolean isBodyControl(int codePoint) {
    boolean lineLayout = codePoint == '\t' || codePoint == '\n' || codePoint == '\r';
    return Character.isISOControl(codePoint) && !lineLayout;
  }
}
