package dev.eliascosta.invoice.sampleservice;

import dev.eliascosta.invoice.common.error.BusinessRuleException;
import dev.eliascosta.invoice.common.idempotency.Idempotent;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Routes exercising every idempotency outcome. Each execution generates a fresh {@code id}/{@code
 * token}, so a replayed response is distinguishable from a re-executed one; {@link #invocations}
 * counts executions per route.
 */
@RestController
@RequestMapping("/orders")
public class IdempotencyTestController {

  public record OrderRequest(String name, Integer quantity) {}

  public record OrderResponse(UUID id, String name, Integer quantity) {}

  private final ConcurrentHashMap<String, AtomicInteger> invocations = new ConcurrentHashMap<>();
  private volatile CountDownLatch slowEntered = new CountDownLatch(1);
  private volatile CountDownLatch slowRelease = new CountDownLatch(1);

  public int invocations(String route) {
    return invocations.getOrDefault(route, new AtomicInteger()).get();
  }

  public void reset() {
    invocations.clear();
    slowEntered = new CountDownLatch(1);
    slowRelease = new CountDownLatch(1);
  }

  /** Blocks until the slow handler has started executing. */
  public boolean awaitSlowEntered(long timeout, TimeUnit unit) throws InterruptedException {
    return slowEntered.await(timeout, unit);
  }

  public void releaseSlow() {
    slowRelease.countDown();
  }

  private void count(String route) {
    invocations.computeIfAbsent(route, ignored -> new AtomicInteger()).incrementAndGet();
  }

  @PostMapping
  @Idempotent
  ResponseEntity<OrderResponse> create(@RequestBody OrderRequest request) {
    count("create");
    UUID id = UUID.randomUUID();
    return ResponseEntity.created(URI.create("/orders/" + id))
        .body(new OrderResponse(id, request.name(), request.quantity()));
  }

  @PostMapping("/{id}/confirm")
  @Idempotent
  Map<String, Object> confirm(@PathVariable UUID id) {
    count("confirm");
    return Map.of("id", id, "token", UUID.randomUUID());
  }

  @PostMapping("/{id}/archive")
  @Idempotent
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void archive(@PathVariable UUID id) {
    count("archive");
  }

  @PostMapping("/slow")
  @Idempotent
  Map<String, Object> slow(@RequestBody OrderRequest request) throws InterruptedException {
    count("slow");
    slowEntered.countDown();
    if (!slowRelease.await(10, TimeUnit.SECONDS)) {
      throw new IllegalStateException("slow handler was never released");
    }
    return Map.of("id", UUID.randomUUID());
  }

  @PostMapping("/fail")
  @Idempotent
  Map<String, Object> fail(@RequestBody OrderRequest request) {
    count("fail");
    throw new IllegalStateException("database on fire");
  }

  @PostMapping("/reject")
  @Idempotent
  Map<String, Object> reject(@RequestBody OrderRequest request) {
    count("reject");
    throw new BusinessRuleException(
        "insufficient-stock",
        "Saldo insuficiente",
        "Saldo insuficiente para " + request.name() + ": solicitado " + request.quantity());
  }

  @PostMapping(value = "/text", produces = MediaType.TEXT_PLAIN_VALUE)
  @Idempotent
  String text(@RequestBody OrderRequest request) {
    count("text");
    return "created " + UUID.randomUUID();
  }

  /** Not annotated: the header, if sent, is ignored. */
  @PostMapping("/plain")
  ResponseEntity<Map<String, Object>> plain(@RequestBody OrderRequest request) {
    count("plain");
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", UUID.randomUUID()));
  }
}
