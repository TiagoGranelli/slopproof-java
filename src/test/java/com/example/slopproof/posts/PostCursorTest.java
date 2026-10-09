package com.example.slopproof.posts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PostCursorTest {

  @Test
  void decodesWhatItEncoded() {
    PostCursor cursor =
        new PostCursor(Instant.parse("2026-10-09T12:00:00.123456Z"), UUID.randomUUID());

    assertEquals(cursor, PostCursor.decode(cursor.encode()));
  }

  @Test
  void refusesAnythingItDidNotEncode() {
    assertInvalid("not base64 at all!");
    assertInvalid(encodeRaw(""));
    assertInvalid(encodeRaw("no separator here"));
    assertInvalid(encodeRaw("2026-10-09T12:00:00Z|1-1-1-1-1"));
    assertInvalid(encodeRaw("0000-01-01T00:00:00Z|" + UUID.randomUUID()));
    assertInvalid(encodeRaw("9999-12-31T23:59:59.9999999Z|" + UUID.randomUUID()));
    assertInvalid(encodeRaw("yesterday|" + UUID.randomUUID()));
    assertInvalid(encodeRaw("2026-10-09T12:00:00Z|not-a-uuid"));
  }

  private static void assertInvalid(String cursor) {
    InvalidCursorException exception =
        assertThrows(InvalidCursorException.class, () -> PostCursor.decode(cursor));
    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
  }

  private static String encodeRaw(String payload) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }
}
