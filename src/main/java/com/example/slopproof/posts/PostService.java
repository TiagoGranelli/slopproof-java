package com.example.slopproof.posts;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Arrays;
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

  // The two selectors that choose emoji or text presentation. They are the only default-ignorable
  // characters a body may hold, and only after a visible character.
  private static final Set<Integer> PRESENTATION_SELECTORS = Set.of(0xFE0E, 0xFE0F);

  private static final int ZERO_WIDTH_JOINER = 0x200D;

  private static final int VARIATION_SELECTOR_16 = 0xFE0F;

  // Unicode's Default_Ignorable_Code_Point property (DerivedCoreProperties, Unicode 16), as ranges.
  // These characters draw nothing a reader can see, so a body that holds one hides text from them.
  private static final int[][] DEFAULT_IGNORABLE = {
    {0x00AD, 0x00AD},
    {0x034F, 0x034F},
    {0x061C, 0x061C},
    {0x115F, 0x1160},
    {0x17B4, 0x17B5},
    {0x180B, 0x180F},
    {0x200B, 0x200F},
    {0x202A, 0x202E},
    {0x2060, 0x206F},
    {0x3164, 0x3164},
    {0xFE00, 0xFE0F},
    {0xFEFF, 0xFEFF},
    {0xFFA0, 0xFFA0},
    {0xFFF0, 0xFFF8},
    {0x1BCA0, 0x1BCA3},
    {0x1D173, 0x1D17A},
    {0xE0000, 0xE0FFF}
  };

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

  // A position is refused by its own rule: a presentation selector needs a visible character
  // before it, a joiner needs an emoji on each side, and any other character is refused when it
  // is never allowed.
  private static boolean isRefused(int[] codePoints, int index) {
    int codePoint = codePoints[index];
    if (PRESENTATION_SELECTORS.contains(codePoint)) {
      return !followsVisibleCharacter(codePoints, index);
    }
    if (codePoint == ZERO_WIDTH_JOINER) {
      return !joinsEmoji(codePoints, index);
    }
    return isNeverAllowed(codePoint);
  }

  private static boolean followsVisibleCharacter(int[] codePoints, int index) {
    return index > 0 && !isDefaultIgnorable(codePoints[index - 1]);
  }

  // A joiner glues two pictographs into one emoji, as in a family or a profession. Anywhere else
  // it would hide a bit between two visible characters, so it is refused there.
  private static boolean joinsEmoji(int[] codePoints, int index) {
    if (index == 0 || index == codePoints.length - 1) {
      return false;
    }
    int previous = codePoints[index - 1];
    boolean afterEmoji =
        Character.isExtendedPictographic(previous)
            || Character.isEmojiModifier(previous)
            || previous == VARIATION_SELECTOR_16;
    return afterEmoji && Character.isExtendedPictographic(codePoints[index + 1]);
  }

  // Postgres refuses NUL, and a lone surrogate would be stored as '?' without an error. Other
  // controls, such as an escape sequence, rewrite the terminal of whoever reads the post. A
  // default-ignorable character hides text or reorders the text after it, so none is kept.
  private static boolean isNeverAllowed(int codePoint) {
    int type = Character.getType(codePoint);
    boolean control = type == Character.CONTROL && !LINE_LAYOUT.contains(codePoint);
    return control
        || type == Character.SURROGATE
        || isDefaultIgnorable(codePoint)
        || isNoncharacter(codePoint);
  }

  private static boolean isDefaultIgnorable(int codePoint) {
    return Arrays.stream(DEFAULT_IGNORABLE)
        .anyMatch(range -> inRange(codePoint, range[0], range[1]));
  }

  // Noncharacters are reserved for software and are never text.
  private static boolean isNoncharacter(int codePoint) {
    return inRange(codePoint, 0xFDD0, 0xFDEF) || (codePoint & 0xFFFE) == 0xFFFE;
  }

  private static boolean inRange(int codePoint, int first, int last) {
    return codePoint >= first && codePoint <= last;
  }
}
