import java.net.URI;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

class Mirror {

  RestClient client;

  void fetch(@RequestParam String target) {
    // ruleid: ssrf-request-input
    client.get().uri(target).retrieve();
  }

  void fetchHost(@PathVariable String host) {
    // ruleid: ssrf-request-input
    URI.create("https://" + host + "/status");
  }

  void fetchPost(@PathVariable String id) {
    // ok: ssrf-request-input
    client.get().uri("https://api.example.com/posts/{id}", id).retrieve();
  }
}
