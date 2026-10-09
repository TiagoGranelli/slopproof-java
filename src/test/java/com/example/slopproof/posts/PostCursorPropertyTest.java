package com.example.slopproof.posts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.http.HttpStatus;

/**
 * Properties of the cursor codec over generated input. The example cases live in {@link
 * PostCursorTest}.
 */
class PostCursorPropertyTest {

  // The bounds PostCursor accepts; the generators stay inside them.
  private static final long EARLIEST_SECOND =
      Instant.parse("0001-01-01T00:00:00Z").getEpochSecond();
  private static final long LATEST_SECOND = Instant.parse("9999-12-31T23:59:59Z").getEpochSecond();

  @Property
  void decodeInvertsEncodeForAnyNanosecondInstantAndId(
      @ForAll("nanosecondInstants") Instant createdAt, @ForAll("uuids") UUID id) {
    PostCursor cursor = new PostCursor(createdAt, id);

    assertEquals(cursor, PostCursor.decode(cursor.encode()));
  }

  @Property
  void decodeInvertsEncodeForMicrosecondInstants(
      @ForAll("microsecondInstants") Instant createdAt, @ForAll("uuids") UUID id) {
    // Postgres keeps microseconds, so every stored post's cursor has no finer digits.
    PostCursor cursor = new PostCursor(createdAt, id);

    assertEquals(cursor, PostCursor.decode(cursor.encode()));
  }

  @Property
  void decodingAnyStringRefusesOnlyWithAnInvalidCursor(@ForAll String cursor) {
    refusesOnlyAsInvalidCursor(cursor);
  }

  @Property
  void decodingBase64OfAnyBytesRefusesOnlyWithAnInvalidCursor(@ForAll byte[] payload) {
    // Random bytes that are valid base64 get past the decoding step into the parsing of the
    // payload.
    refusesOnlyAsInvalidCursor(Base64.getUrlEncoder().withoutPadding().encodeToString(payload));
  }

  private static void refusesOnlyAsInvalidCursor(String cursor) {
    try {
      PostCursor.decode(cursor);
    } catch (InvalidCursorException exception) {
      assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
  }

  @Provide
  Arbitrary<UUID> uuids() {
    return Arbitraries.create(UUID::randomUUID);
  }

  @Provide
  Arbitrary<Instant> nanosecondInstants() {
    return Combinators.combine(
            Arbitraries.longs().between(EARLIEST_SECOND, LATEST_SECOND - 1),
            Arbitraries.integers().between(0, 999_999_999))
        .as((seconds, nanos) -> Instant.ofEpochSecond(seconds, nanos));
  }

  @Provide
  Arbitrary<Instant> microsecondInstants() {
    return Combinators.combine(
            Arbitraries.longs().between(EARLIEST_SECOND, LATEST_SECOND - 1),
            Arbitraries.integers().between(0, 999_999))
        .as((seconds, micros) -> Instant.ofEpochSecond(seconds, 1_000L * micros));
  }
}
