package dev.eliascosta.invoice.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Computes {@code idempotency_keys.request_hash}: SHA-256 (lower-case hex, 64 chars) of the request
 * body in canonical JSON form — object keys sorted recursively, compact serialization — so that two
 * payloads that differ only in key order or whitespace hash the same ({@code modelo-de-dados.md}
 * §6.3 "corpo canonicalizado").
 *
 * <p>An empty body hashes the empty byte sequence; a body that is not valid JSON hashes its raw
 * bytes (the handler will reject it anyway, and two identical resends still match).
 *
 * <p>Uses a private, default-configured {@link JsonMapper} on purpose: the hash must not change
 * with the service's Jackson customizations.
 */
class CanonicalJsonHasher {

  private static final Logger log = LoggerFactory.getLogger(CanonicalJsonHasher.class);

  private final JsonMapper mapper = JsonMapper.builder().build();

  String hash(byte[] body) {
    return hex(sha256(canonicalize(body)));
  }

  byte[] canonicalize(byte[] body) {
    if (body.length == 0) {
      return body;
    }
    try {
      JsonNode root = mapper.readTree(body);
      if (root == null || root.isMissingNode()) {
        return body;
      }
      return mapper.writeValueAsBytes(sorted(root));
    } catch (JacksonException e) {
      log.debug("Request body is not JSON; hashing raw bytes: {}", e.getMessage());
      return body;
    }
  }

  private JsonNode sorted(JsonNode node) {
    if (node instanceof ObjectNode object) {
      Map<String, JsonNode> byKey = new TreeMap<>();
      object.properties().forEach(entry -> byKey.put(entry.getKey(), sorted(entry.getValue())));
      ObjectNode result = mapper.createObjectNode();
      byKey.forEach(result::set);
      return result;
    }
    if (node instanceof ArrayNode array) {
      ArrayNode result = mapper.createArrayNode();
      array.forEach(element -> result.add(sorted(element)));
      return result;
    }
    return node;
  }

  private static byte[] sha256(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is mandatory in every JVM", e);
    }
  }

  private static String hex(byte[] digest) {
    return HexFormat.of().formatHex(digest);
  }

  /** Convenience for tests and diagnostics: canonical form as text. */
  String canonicalText(byte[] body) {
    return new String(canonicalize(body), StandardCharsets.UTF_8);
  }
}
