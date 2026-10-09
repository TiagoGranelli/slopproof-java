package com.example.slopproof.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SecretTest {

  @Test
  void printsRedactedAndNeverTheValue() {
    Secret secret = new Secret("hunter2");

    assertEquals("<redacted>", secret.toString());
    assertFalse(secret.toString().contains("hunter2"));
  }

  @Test
  void revealsTheValueOnlyWhenAskedFor() {
    assertEquals("hunter2", new Secret("hunter2").reveal());
  }

  @Test
  void comparesByValue() {
    assertEquals(new Secret("a"), new Secret("a"));
    assertEquals(new Secret("a").hashCode(), new Secret("a").hashCode());
    assertNotEquals(new Secret("a"), new Secret("b"));
    assertNotEquals(new Secret("a"), "a");
  }

  @Test
  void refusesAnEmptySecret() {
    assertThrows(IllegalArgumentException.class, () -> new Secret(""));
  }
}
