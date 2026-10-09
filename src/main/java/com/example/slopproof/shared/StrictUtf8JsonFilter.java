package com.example.slopproof.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses a JSON body that is not strict UTF-8 before anything parses it. Jackson reads the
 * overlong encoding C0 BC as '&lt;', while a proxy in front of the app sees two invalid bytes, so
 * the two would read one body two ways. A raw NUL is refused too: JSON text never holds one, and
 * UTF-16 JSON does. A declared charset other than UTF-8 is refused for the same reason: Jackson
 * would read the bytes under that charset. The body is held whole, so the read stops one byte past
 * the document limit.
 */
@Component
public class StrictUtf8JsonFilter extends OncePerRequestFilter {

  /** The detail of the refusal, which a test checks without copying the text. */
  public static final String REFUSED_DETAIL =
      "the body must be strict UTF-8 JSON, with any declared charset UTF-8, with no NUL byte, "
          + "and within the size limit";

  private static final String REFUSED_BODY =
      "{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400,\"detail\":\""
          + REFUSED_DETAIL
          + "\"}";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String contentType = request.getContentType();
    if (contentType == null || !isJson(contentType)) {
      chain.doFilter(request, response);
      return;
    }
    int readLimit = (int) JsonInputConfiguration.MAX_DOCUMENT_LENGTH + 1;
    byte[] body = request.getInputStream().readNBytes(readLimit);
    // A body-less request has no text for a charset to misread, so its header is not refused.
    boolean charsetRefused = body.length > 0 && !declaresUtf8(contentType);
    if (body.length > JsonInputConfiguration.MAX_DOCUMENT_LENGTH
        || !isStrictJson(body)
        || charsetRefused) {
      refuse(response);
      return;
    }
    chain.doFilter(new BufferedBodyRequest(request, body), response);
  }

  private static boolean isJson(String contentType) {
    return contentType.toLowerCase(Locale.ROOT).startsWith(MediaType.APPLICATION_JSON_VALUE);
  }

  // Only UTF-8 may be named, and at most once: a repeated charset is read as the last one. The
  // alias utf8 names the same charset.
  private static boolean declaresUtf8(String contentType) {
    String normalised = contentType.toLowerCase(Locale.ROOT).replaceAll("[\\s\"]", "");
    int charsets = occurrences(normalised, "charset");
    int utf8 = occurrences(normalised, "charset=utf-8") + occurrences(normalised, "charset=utf8");
    return charsets == 0 || (charsets == 1 && utf8 == 1);
  }

  private static int occurrences(String text, String part) {
    int count = 0;
    for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
      count++;
    }
    return count;
  }

  // The decoder rejects overlong forms, surrogates and code points past U+10FFFF, as RFC 3629 does.
  private static boolean isStrictJson(byte[] body) {
    for (byte value : body) {
      if (value == 0) {
        return false;
      }
    }
    try {
      StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(body));
      return true;
    } catch (CharacterCodingException exception) {
      return false;
    }
  }

  private static void refuse(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.BAD_REQUEST.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getWriter().write(REFUSED_BODY);
  }

  // Serves the verified bytes to the rest of the chain, so the body is read once.
  private static final class BufferedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    BufferedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream bytes = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public boolean isFinished() {
          return bytes.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException("the body is served synchronously");
        }

        @Override
        public int read() {
          return bytes.read();
        }

        // Without this, the compiler's lint warns that bulk reads would be slow.
        @Override
        public int read(byte[] buffer, int offset, int length) {
          return bytes.read(buffer, offset, length);
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
  }
}
