package dev.eliascosta.invoice.common.idempotency;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What is kept of a completed response and replayed for the same key ({@code
 * idempotency_keys.response_*}).
 *
 * @param status HTTP status
 * @param body JSON body as written by the handler, or null when there was none
 * @param headers headers to replay ({@code Content-Type}, {@code Location}); never null
 */
record StoredResponse(int status, @Nullable String body, Map<String, String> headers) {

  StoredResponse {
    headers = Map.copyOf(headers);
  }
}
