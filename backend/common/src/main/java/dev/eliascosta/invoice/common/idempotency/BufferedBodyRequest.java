package dev.eliascosta.invoice.common.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Request wrapper that reads the whole body up front and serves it again on every {@link
 * #getInputStream()} / {@link #getReader()} call.
 *
 * <p>Needed because the {@link IdempotencyInterceptor} must hash the body <em>before</em> the
 * handler runs, and the handler then needs to read it again; Spring's {@code
 * ContentCachingRequestWrapper} only caches what has already been consumed.
 */
final class BufferedBodyRequest extends HttpServletRequestWrapper {

  private final byte[] body;

  BufferedBodyRequest(HttpServletRequest request) throws IOException {
    super(request);
    this.body = request.getInputStream().readAllBytes();
  }

  /** The complete body; empty array when the request had none. */
  byte[] body() {
    return body;
  }

  @Override
  public ServletInputStream getInputStream() {
    return new ByteArrayServletInputStream(body);
  }

  @Override
  public BufferedReader getReader() {
    String encoding = getCharacterEncoding();
    Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
    return new BufferedReader(new InputStreamReader(getInputStream(), charset));
  }

  private static final class ByteArrayServletInputStream extends ServletInputStream {

    private final ByteArrayInputStream delegate;

    private ByteArrayServletInputStream(byte[] bytes) {
      this.delegate = new ByteArrayInputStream(bytes);
    }

    @Override
    public int read() {
      return delegate.read();
    }

    @Override
    public int read(byte[] b, int off, int len) {
      return delegate.read(b, off, len);
    }

    @Override
    public int available() {
      return delegate.available();
    }

    @Override
    public boolean isFinished() {
      return delegate.available() == 0;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setReadListener(ReadListener listener) {
      throw new UnsupportedOperationException(
          "Non-blocking IO is not supported on a buffered body");
    }
  }
}
