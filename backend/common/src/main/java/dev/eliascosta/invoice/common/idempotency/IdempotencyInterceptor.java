package dev.eliascosta.invoice.common.idempotency;

import dev.eliascosta.invoice.common.error.InvalidRequestException;
import dev.eliascosta.invoice.common.error.ProblemTypes;
import dev.eliascosta.invoice.common.error.web.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

/**
 * Enforces {@link Idempotent} ({@code requisitos.md} §8.9, {@code modelo-de-dados.md} §6.3).
 *
 * <p>{@code preHandle}: validates the {@code Idempotency-Key} header (missing or not a UUID → 400
 * {@code validation-error}), resolves the user and route, hashes the buffered body and claims the
 * key. A claimed key lets the handler run; a completed key with the same payload replays the stored
 * response and stops the chain; a different payload raises 422; an in-flight key raises 409.
 *
 * <p>{@code afterCompletion} (only reached when the key was claimed here): an unhandled exception
 * or a 5xx status releases the key; any other status stores the cached response — 4xx included, so
 * a business rejection is replayed instead of re-evaluated.
 *
 * <p>Relies on {@link IdempotencyFilter} having wrapped the request and response.
 */
class IdempotencyInterceptor implements HandlerInterceptor {

  static final String CLAIM_ATTRIBUTE = IdempotencyInterceptor.class.getName() + ".claim";

  private static final Logger log = LoggerFactory.getLogger(IdempotencyInterceptor.class);
  private static final Pattern UUID_FORMAT =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final String TITLE_VALIDATION = "Dados inválidos";
  private static final String ERRORS_PROPERTY = "errors"; // requisitos.md §8.8 validation-error

  private final IdempotencyKeyStore store;
  private final IdempotencyUserResolver userResolver;
  private final CanonicalJsonHasher hasher;

  IdempotencyInterceptor(
      IdempotencyKeyStore store, IdempotencyUserResolver userResolver, CanonicalJsonHasher hasher) {
    this.store = Objects.requireNonNull(store, "store");
    this.userResolver = Objects.requireNonNull(userResolver, "userResolver");
    this.hasher = Objects.requireNonNull(hasher, "hasher");
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    if (!(handler instanceof HandlerMethod method)
        || !method.hasMethodAnnotation(Idempotent.class)) {
      return true;
    }
    UUID key = requireKey(request);
    BufferedBodyRequest buffered = WebUtils.getNativeRequest(request, BufferedBodyRequest.class);
    if (buffered == null) {
      throw new IllegalStateException(
          "@Idempotent handler reached without IdempotencyFilter; is the filter registered?");
    }
    IdempotencyKeyId id = new IdempotencyKeyId(key, userResolver.resolve(request));
    String route = request.getMethod() + " " + request.getRequestURI(); // D-012
    String requestHash = hasher.hash(buffered.body());

    ClaimResult result = store.claim(id, route, requestHash);
    return switch (result) {
      case ClaimResult.Claimed claimed -> {
        request.setAttribute(CLAIM_ATTRIBUTE, claimed.id());
        yield true;
      }
      case ClaimResult.Replay replay -> {
        log.debug("Replaying stored response for idempotency key {} on {}", id, route);
        replay(replay.response(), response);
        yield false;
      }
      case ClaimResult.Mismatch ignored -> throw new IdempotencyPayloadMismatchException(key);
      case ClaimResult.InFlight ignored -> throw new IdempotencyKeyInFlightException(key);
    };
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      @Nullable Exception ex) {
    if (!(request.getAttribute(CLAIM_ATTRIBUTE) instanceof IdempotencyKeyId id)) {
      return;
    }
    int status = response.getStatus();
    if (ex != null || status >= 500) {
      log.debug("Releasing idempotency key {} after status {} / {}", id, status, ex);
      store.release(id);
      return;
    }
    ContentCachingResponseWrapper cached =
        WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
    if (cached == null) {
      log.warn("Response of @Idempotent handler is not cached; releasing key {}", id);
      store.release(id);
      return;
    }
    StoredResponse stored = capture(cached, status);
    if (stored == null) {
      log.warn(
          "@Idempotent handler produced a non-JSON body ({}); response not stored and key {}"
              + " released",
          cached.getContentType(),
          id);
      store.release(id);
      return;
    }
    store.complete(id, stored);
  }

  /** Null when the body is not JSON (only JSON is stored: {@code response_body} is jsonb). */
  private static @Nullable StoredResponse capture(
      ContentCachingResponseWrapper cached, int status) {
    byte[] bytes = cached.getContentAsByteArray();
    Map<String, String> headers = new LinkedHashMap<>();
    String location = cached.getHeader(HttpHeaders.LOCATION);
    if (location != null) {
      headers.put(HttpHeaders.LOCATION, location);
    }
    if (bytes.length == 0) {
      return new StoredResponse(status, null, headers);
    }
    MediaType mediaType = mediaTypeOf(cached.getContentType());
    if (mediaType == null || !isJson(mediaType)) {
      return null;
    }
    headers.put(HttpHeaders.CONTENT_TYPE, mediaType.toString());
    return new StoredResponse(status, new String(bytes, charsetOf(mediaType)), headers);
  }

  private static void replay(StoredResponse stored, HttpServletResponse response)
      throws IOException {
    response.setStatus(stored.status());
    stored
        .headers()
        .forEach(
            (name, value) -> {
              if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                response.setContentType(value);
              } else {
                response.setHeader(name, value);
              }
            });
    if (stored.body() != null) {
      Charset charset = charsetOf(mediaTypeOf(stored.headers().get(HttpHeaders.CONTENT_TYPE)));
      response.getOutputStream().write(stored.body().getBytes(charset));
    }
  }

  private static UUID requireKey(HttpServletRequest request) {
    String raw = request.getHeader(Idempotent.HEADER);
    if (raw == null || raw.isBlank()) {
      throw invalidKey("header obrigatório");
    }
    String value = raw.strip();
    if (!UUID_FORMAT.matcher(value).matches()) {
      throw invalidKey("deve ser um UUID");
    }
    return UUID.fromString(value);
  }

  private static InvalidRequestException invalidKey(String message) {
    return new InvalidRequestException(
        ProblemTypes.VALIDATION_ERROR,
        TITLE_VALIDATION,
        "Header " + Idempotent.HEADER + ": " + message + ".",
        Map.of(ERRORS_PROPERTY, List.of(new FieldViolation(Idempotent.HEADER, message))));
  }

  private static @Nullable MediaType mediaTypeOf(@Nullable String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return null;
    }
    try {
      return MediaType.parseMediaType(contentType);
    } catch (InvalidMediaTypeException e) {
      return null;
    }
  }

  private static Charset charsetOf(@Nullable MediaType mediaType) {
    return mediaType == null || mediaType.getCharset() == null
        ? StandardCharsets.UTF_8
        : mediaType.getCharset();
  }

  private static boolean isJson(MediaType mediaType) {
    String subtype = mediaType.getSubtype().toLowerCase();
    return "json".equals(subtype) || subtype.endsWith("+json");
  }
}
