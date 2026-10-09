package com.example.slopproof.posts;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
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

  // Tab, line feed and carriage return are the controls a body can hold on purpose.
  private static final Set<Integer> LINE_LAYOUT = Set.of(0x09, 0x0A, 0x0D);

  // The embedding, override and isolate controls, which reorder the text after them. The marks
  // LRM, RLM and ALM are left out: they have no glyph and cannot reverse a run of text.
  private static final Set<Integer> BIDI_CONTROLS =
      Set.of(0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069);

  // The two selectors that choose emoji or text presentation. Any other selector hides data.
  private static final Set<Integer> PRESENTATION_SELECTORS = Set.of(0xFE0E, 0xFE0F);

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
    int[] codePoints = value.codePoints().toArray();
    if (IntStream.range(0, codePoints.length).anyMatch(index -> isRefused(codePoints, index))) {
      throw new InvalidPostException(
          "%s must not contain control, bidi, hidden or unpaired surrogate characters"
              .formatted(field));
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

  // A position is refused when its character is never allowed, or when it is a presentation
  // selector that has no character before it, or that follows another selector.
  private static boolean isRefused(int[] codePoints, int index) {
    int codePoint = codePoints[index];
    int previous = index == 0 ? -1 : codePoints[index - 1];
    return isNeverAllowed(codePoint)
        || (PRESENTATION_SELECTORS.contains(codePoint) && (previous == -1 || isSelector(previous)));
  }

  // Postgres refuses NUL, and a lone surrogate would be stored as '?' without an error. Other
  // controls, such as an escape sequence, rewrite the terminal of whoever reads the post. Tags and
  // selectors spell text that no renderer shows, and a bidi control reorders the text after it.
  private static boolean isNeverAllowed(int codePoint) {
    int type = Character.getType(codePoint);
    boolean control = type == Character.CONTROL && !LINE_LAYOUT.contains(codePoint);
    return control
        || type == Character.SURROGATE
        || BIDI_CONTROLS.contains(codePoint)
        || isTag(codePoint)
        || (isSelector(codePoint) && !PRESENTATION_SELECTORS.contains(codePoint))
        || isHiddenUnassigned(codePoint);
  }

  // Tag characters spell ASCII that no renderer shows. The England, Scotland and Wales flags use
  // them too, so those flags are refused.
  private static boolean isTag(int codePoint) {
    return codePoint == 0xE0001 || inRange(codePoint, 0xE0020, 0xE007F);
  }

  private static boolean isSelector(int codePoint) {
    return inRange(codePoint, 0xFE00, 0xFE0F) || inRange(codePoint, 0xE0100, 0xE01EF);
  }

  // Unassigned code points that Unicode marks default-ignorable draw as nothing, and noncharacters
  // are reserved for software. Other unassigned code points are newer characters than the JDK's
  // Unicode table, so they stay allowed.
  private static boolean isHiddenUnassigned(int codePoint) {
    boolean ignorable =
        codePoint == 0x2065
            || codePoint == 0xE0000
            || inRange(codePoint, 0xFFF0, 0xFFF8)
            || inRange(codePoint, 0xE0002, 0xE001F)
            || inRange(codePoint, 0xE0080, 0xE00FF)
            || inRange(codePoint, 0xE01F0, 0xE0FFF);
    boolean noncharacter = inRange(codePoint, 0xFDD0, 0xFDEF) || (codePoint & 0xFFFE) == 0xFFFE;
    return Character.getType(codePoint) == Character.UNASSIGNED && (ignorable || noncharacter);
  }

  private static boolean inRange(int codePoint, int first, int last) {
    return codePoint >= first && codePoint <= last;
  }
}
