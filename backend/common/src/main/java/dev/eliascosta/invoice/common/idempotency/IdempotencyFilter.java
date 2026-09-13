package dev.eliascosta.invoice.common.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Prepares requests that carry an {@code Idempotency-Key} for the {@link IdempotencyInterceptor}:
 * buffers the body (so it can be hashed and then read by the handler) and caches the response (so
 * it can be stored after the handler ran). Requests without the header pass through untouched.
 *
 * <p>The interceptor, not this filter, decides whether the route is {@code @Idempotent}: a stray
 * header on a non-idempotent route only costs the buffering.
 */
class IdempotencyFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getHeader(Idempotent.HEADER) == null) {
      chain.doFilter(request, response);
      return;
    }
    BufferedBodyRequest bufferedRequest = new BufferedBodyRequest(request);
    ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
    try {
      chain.doFilter(bufferedRequest, cachedResponse);
    } finally {
      cachedResponse.copyBodyToResponse();
    }
  }
}
