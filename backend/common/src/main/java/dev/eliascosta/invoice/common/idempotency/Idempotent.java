package dev.eliascosta.invoice.common.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a handler method whose requests must carry an {@code Idempotency-Key} header (RN-16, {@code
 * requisitos.md} §8.9).
 *
 * <p>The {@link IdempotencyInterceptor} enforces the semantics of {@code modelo-de-dados.md} §6.3:
 * a new key executes the handler and stores the response; the same key with the same body and route
 * replays the stored response without executing the handler again; a different body or route is
 * rejected with {@code 422 idempotency-key-payload-mismatch}; a key still being processed is
 * rejected with {@code 409 idempotency-key-in-flight}. A response with status 5xx (or an unhandled
 * exception) releases the key so the client can retry with it.
 *
 * <p>Constraints: the annotated handler must produce a JSON body ({@code application/json} or
 * {@code application/problem+json}) or no body at all — the response is stored in a {@code jsonb}
 * column. Any other content type is not stored (the key is released and a warning logged). A
 * replayed body is the same JSON document, not the same bytes: {@code jsonb} normalizes whitespace
 * and key order (D-014).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {

  /** Name of the request header carrying the client-generated UUID. */
  String HEADER = "Idempotency-Key";
}
