package dev.eliascosta.invoice.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class BufferedBodyRequestTest {

  private static MockHttpServletRequest post(String body) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    request.setCharacterEncoding("UTF-8");
    return request;
  }

  @Test
  void bodyCanBeReadRepeatedly() throws IOException {
    BufferedBodyRequest request = new BufferedBodyRequest(post("{\"a\":1}"));

    assertThat(request.body()).asString(StandardCharsets.UTF_8).isEqualTo("{\"a\":1}");
    assertThat(request.getInputStream().readAllBytes())
        .asString(StandardCharsets.UTF_8)
        .isEqualTo("{\"a\":1}");
    assertThat(request.getInputStream().readAllBytes())
        .asString(StandardCharsets.UTF_8)
        .isEqualTo("{\"a\":1}");
    assertThat(request.getReader().readLine()).isEqualTo("{\"a\":1}");
  }

  @Test
  void emptyBodyIsAnEmptyArray() throws IOException {
    BufferedBodyRequest request = new BufferedBodyRequest(post(""));

    assertThat(request.body()).isEmpty();
    assertThat(request.getInputStream().isFinished()).isTrue();
    assertThat(request.getInputStream().read()).isEqualTo(-1);
  }

  @Test
  void streamReportsProgress() throws IOException {
    BufferedBodyRequest request = new BufferedBodyRequest(post("ab"));
    var stream = request.getInputStream();

    assertThat(stream.isReady()).isTrue();
    assertThat(stream.isFinished()).isFalse();
    assertThat(stream.read()).isEqualTo('a');
    assertThat(stream.read(new byte[8], 0, 8)).isEqualTo(1);
    assertThat(stream.isFinished()).isTrue();
  }
}
