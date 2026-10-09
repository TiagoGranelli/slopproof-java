import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Auth {

  private static final Logger log = LoggerFactory.getLogger(Auth.class);

  void login(String password) {
    // ruleid: secret-logging
    log.info("login with {}", password);
  }

  void refresh(String refreshToken) {
    // ruleid: secret-logging
    log.debug("refreshing with", refreshToken);
  }

  void reset(String user) {
    // ok: secret-logging
    log.info("password reset requested for {}", user);
  }

  void resetMessage() {
    // ok: secret-logging
    log.warn("password reset");
  }
}
