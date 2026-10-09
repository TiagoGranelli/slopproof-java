import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;

class Converter {

  void convert(@RequestParam String format) throws Exception {
    // ruleid: command-injection-request-input
    new ProcessBuilder("convert", format, "out.png").start();
  }

  void run(@RequestParam String name) throws Exception {
    // ruleid: command-injection-request-input
    Runtime.getRuntime().exec("tool " + name);
  }

  void fixed() throws Exception {
    // ok: command-injection-request-input
    new ProcessBuilder("convert", "in.png", "out.png").start();
  }
}
