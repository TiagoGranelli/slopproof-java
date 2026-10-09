package com.example.slopproof.posts;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The keyset position a page ends at: the list resumes just after {@code (createdAt, id)}. Clients
 * get it as an opaque base64url string, so the encoding can change without breaking them.
 */
public record PostCursor(Instant createdAt, UUID id) {

  // PostgreSQL rejects timestamps outside years 1 to 9999 with a 500, so a cursor outside them is
  // refused here.
  private static final Instant EARLIEST = Instant.parse("0001-01-01T00:00:00Z");
  private static final Instant LATEST = Instant.parse("9999-12-31T23:59:59.999999Z");
  private static final String SEPARATOR = "|";

  /** The cursor that resumes just after {@code post}, the last post of a page. */
  public static PostCursor after(Post post) {
    return new PostCursor(post.createdAt(), post.id().value());
  }

  /** Encodes the position as {@code createdAt|id} in base64url, which is safe in a query string. */
  public String encode() {
    String payload = createdAt + SEPARATOR + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Reads a cursor that {@link #encode()} produced; any other string fails with an {@link
   * InvalidCursorException}.
   */
  public static PostCursor decode(String cursor) {
    String payload = decodeBase64(cursor);
    String[] parts = payload.split(Pattern.quote(SEPARATOR), -1);
    if (parts.length != 2) {
      throw new InvalidCursorException(cursor, null);
    }
    return new PostCursor(parseCreatedAt(parts[0], cursor), parseId(parts[1], cursor));
  }

  private static String decodeBase64(String cursor) {
    try {
      return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      throw new InvalidCursorException(cursor, exception);
    }
  }

  private static Instant parseCreatedAt(String text, String cursor) {
    try {
      Instant createdAt = Instant.parse(text);
      if (createdAt.isBefore(EARLIEST) || createdAt.isAfter(LATEST)) {
        throw new InvalidCursorException(cursor, null);
      }
      return createdAt;
    } catch (DateTimeParseException exception) {
      throw new InvalidCursorException(cursor, exception);
    }
  }

  private static UUID parseId(String text, String cursor) {
    try {
      UUID id = UUID.fromString(text);
      // UUID.fromString accepts shapes that are not canonical; only the canonical form is one we
      // wrote.
      if (!id.toString().equals(text)) {
        throw new InvalidCursorException(cursor, null);
      }
      return id;
    } catch (IllegalArgumentException exception) {
      throw new InvalidCursorException(cursor, exception);
    }
  }
}
