package dev.eliascosta.invoice.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** {@code request_hash} contract: SHA-256 hex of the canonical body (modelo-de-dados.md §6.3). */
class CanonicalJsonHasherTest {

  private static final String SHA256_OF_EMPTY =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

  private final CanonicalJsonHasher hasher = new CanonicalJsonHasher();

  private static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void producesLowerCaseHex64() {
    String hash = hasher.hash(utf8("{\"a\":1}"));
    assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
  }

  @Test
  void emptyBodyHashesEmptyByteSequence() {
    assertThat(hasher.hash(new byte[0])).isEqualTo(SHA256_OF_EMPTY);
  }

  @Test
  void keyOrderAndWhitespaceDoNotMatter() {
    byte[] compact = utf8("{\"b\":2,\"a\":1}");
    byte[] pretty = utf8("{\n  \"a\" : 1 ,\n  \"b\" : 2\n}");
    assertThat(hasher.hash(pretty)).isEqualTo(hasher.hash(compact));
    assertThat(hasher.canonicalText(pretty)).isEqualTo("{\"a\":1,\"b\":2}");
  }

  @Test
  void sortsNestedObjectsAndObjectsInsideArrays() {
    byte[] body = utf8("{\"z\":{\"y\":1,\"x\":[{\"q\":true,\"p\":null}]},\"a\":\"s\"}");
    assertThat(hasher.canonicalText(body))
        .isEqualTo("{\"a\":\"s\",\"z\":{\"x\":[{\"p\":null,\"q\":true}],\"y\":1}}");
  }

  @Test
  void arrayOrderIsSignificant() {
    assertThat(hasher.hash(utf8("[1,2]"))).isNotEqualTo(hasher.hash(utf8("[2,1]")));
  }

  @Test
  void differentValuesHashDifferently() {
    assertThat(hasher.hash(utf8("{\"a\":1}"))).isNotEqualTo(hasher.hash(utf8("{\"a\":2}")));
  }

  @Test
  void nonJsonBodyHashesRawBytes() {
    byte[] raw = utf8("not json at all");
    assertThat(hasher.canonicalize(raw)).isSameAs(raw);
    assertThat(hasher.hash(raw)).isEqualTo(hasher.hash(utf8("not json at all")));
    assertThat(hasher.hash(raw)).isNotEqualTo(hasher.hash(utf8("not json at all ")));
  }

  @Test
  void unicodeIsPreserved() {
    assertThat(hasher.canonicalText(utf8("{\"nome\":\"Parafuso ção\"}")))
        .isEqualTo("{\"nome\":\"Parafuso ção\"}");
  }
}
