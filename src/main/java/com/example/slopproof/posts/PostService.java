package com.example.slopproof.posts;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rules of posts and the transactions they run in. The controller calls this, never the
 * repository.
 */
@Service
public class PostService {

  // Hangul fillers and the Braille blank draw nothing, though Unicode files them as letters or
  // symbols.
  private static final Set<Integer> BLANK_FILLERS = Set.of(0x115F, 0x1160, 0x2800, 0x3164, 0xFFA0);

  private final PostRepository repository;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected bean, never returned to a caller")
  public PostService(PostRepository repository) {
    this.repository = repository;
  }

  /**
   * Creates a post. The contract's length limits let whitespace-only and invisible text through,
   * and Postgres refuses a NUL, so this refuses those before they reach the database.
   */
  @Transactional
  public Post create(String title, String body) {
    requireText("title", title);
    requireText("body", body);
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

  private static void requireText(String field, String value) {
    if (value.codePoints().allMatch(PostService::isInvisible)) {
      throw new InvalidPostException(
          "%s must not be blank (got %d characters)".formatted(field, value.length()));
    }
    if (value.codePoints().anyMatch(PostService::isRefused)) {
      throw new InvalidPostException(
          "%s must not contain control or unpaired surrogate characters".formatted(field));
    }
  }

  // String.isBlank misses no-break and zero-width spaces, which are whitespace to a reader. Marks
  // count too, because text made only of them shows nothing.
  private static boolean isInvisible(int codePoint) {
    return Character.isWhitespace(codePoint)
        || Character.isSpaceChar(codePoint)
        || Character.getType(codePoint) == Character.FORMAT
        || Character.getType(codePoint) == Character.NON_SPACING_MARK
        || BLANK_FILLERS.contains(codePoint);
  }

  // Postgres refuses NUL, a lone surrogate would be stored as '?' without an error, and other
  // controls, such as an escape sequence, rewrite the terminal of whoever reads the post.
  private static boolean isRefused(int codePoint) {
    boolean control = Character.getType(codePoint) == Character.CONTROL;
    boolean lineLayout = codePoint == '\t' || codePoint == '\n' || codePoint == '\r';
    return (control && !lineLayout) || Character.getType(codePoint) == Character.SURROGATE;
  }
}
