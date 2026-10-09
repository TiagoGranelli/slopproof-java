import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

class Tokens {

  private final SecureRandom secureRandom = new SecureRandom();

  String tokenFromRandom() {
    // ruleid: insecure-random
    return Long.toHexString(new Random().nextLong());
  }

  String jitter() {
    // ruleid: insecure-random
    return ThreadLocalRandom.current().nextInt(5) + "s";
  }

  String token() {
    // ok: insecure-random
    return Long.toHexString(secureRandom.nextLong());
  }
}
