import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.X509TrustManager;

class Insecure {

  void disableHostCheck(HttpsURLConnection connection) {
    // ruleid: tls-verification-disabled
    connection.setHostnameVerifier((host, session) -> true);
  }

  void disableHostCheckGlobally() {
    // ruleid: tls-verification-disabled
    HttpsURLConnection.setDefaultHostnameVerifier((host, session) -> true);
  }

  X509TrustManager trustAll() {
    return new X509TrustManager() {
      public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
      public void checkClientTrusted(X509Certificate[] chain, String authType) {}
      // ruleid: tls-verification-disabled
      public void checkServerTrusted(X509Certificate[] chain, String authType) {}
    };
  }

  void verified(HttpsURLConnection connection) {
    // ok: tls-verification-disabled
    connection.setHostnameVerifier((host, session) -> host.endsWith(".example.com"));
  }
}
