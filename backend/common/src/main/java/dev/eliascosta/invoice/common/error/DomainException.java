package dev.eliascosta.invoice.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Base class for every error that must reach the client as an RFC 9457 {@code ProblemDetail}.
 *
 * <p>The hierarchy is sealed (D-009): services extend one of the six generic subclasses, never this
 * class directly, so the status mapping in the advice stays exhaustive.
 *
 * <p>Pure Java on purpose: the services' {@code domain} packages extend it without importing
 * Spring. The HTTP status is <em>not</em> carried here; it is derived from the generic subclass
 * ({@link NotFoundException}, {@link ConflictException}, ...) by the {@code
 * ProblemDetailExceptionHandler} in {@code common.error.web}.
 *
 * <ul>
 *   <li>{@code type}: slug from the catalogue in {@code requisitos.md} §8.8 (e.g. {@code
 *       insufficient-stock}); becomes {@code urn:problem-type:<slug>}.
 *   <li>{@code title}: short human-readable summary, in Portuguese (shown to the user).
 *   <li>{@code detail} ({@link #getMessage()}): human-readable explanation of this occurrence.
 *   <li>{@code properties}: extension members copied verbatim into the {@code ProblemDetail} (e.g.
 *       {@code currentStatus}, {@code allowedTransitions}).
 * </ul>
 */
public abstract sealed class DomainException extends RuntimeException
    permits BusinessRuleException,
        ConflictException,
        InvalidRequestException,
        InvalidStateException,
        NotFoundException,
        UpstreamUnavailableException {

  private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

  private final String type;
  private final String title;
  private final Map<String, Object> properties;

  protected DomainException(String type, String title, String detail) {
    this(type, title, detail, Map.of(), null);
  }

  protected DomainException(
      String type, String title, String detail, Map<String, Object> properties) {
    this(type, title, detail, properties, null);
  }

  protected DomainException(String type, String title, String detail, Throwable cause) {
    this(type, title, detail, Map.of(), cause);
  }

  protected DomainException(
      String type, String title, String detail, Map<String, Object> properties, Throwable cause) {
    super(requireNonBlank(detail, "detail"), cause);
    this.type = requireSlug(type);
    this.title = requireNonBlank(title, "title");
    this.properties =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(properties, "properties must not be null")));
  }

  /** Slug from the error catalogue (§8.8), without the {@code urn:problem-type:} prefix. */
  public String getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  /** Same as {@link #getMessage()}; named after the RFC 9457 member it maps to. */
  public String getDetail() {
    return getMessage();
  }

  /** Extension members to expose in the {@code ProblemDetail}. Never null, never modifiable. */
  public Map<String, Object> getProperties() {
    return properties;
  }

  private static String requireSlug(String type) {
    requireNonBlank(type, "type");
    if (!SLUG.matcher(type).matches()) {
      throw new IllegalArgumentException(
          "type must be a kebab-case slug from the error catalogue, got: " + type);
    }
    return type;
  }

  private static String requireNonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
