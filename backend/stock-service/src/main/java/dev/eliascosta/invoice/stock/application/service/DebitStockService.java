package dev.eliascosta.invoice.stock.application.service;

import dev.eliascosta.invoice.common.messaging.ProcessedEventGuard;
import dev.eliascosta.invoice.stock.domain.exception.InsufficientStockException;
import dev.eliascosta.invoice.stock.domain.exception.ProductInactiveException;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.Confirmed;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.ConfirmedItem;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.Rejected;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.RejectedItem;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.RejectionReason;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.Result;
import dev.eliascosta.invoice.stock.domain.model.Product;
import dev.eliascosta.invoice.stock.domain.model.StockMovement;
import dev.eliascosta.invoice.stock.domain.port.in.DebitStockUseCase;
import dev.eliascosta.invoice.stock.domain.port.out.InvoiceDebitRepository;
import dev.eliascosta.invoice.stock.domain.port.out.InvoiceDebitResultPublisher;
import dev.eliascosta.invoice.stock.domain.port.out.ProductRepository;
import dev.eliascosta.invoice.stock.domain.port.out.StockMovementRepository;
import jakarta.persistence.OptimisticLockException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class DebitStockService implements DebitStockUseCase {

  private final ProductRepository products;
  private final StockMovementRepository movements;
  private final InvoiceDebitRepository debits;
  private final InvoiceDebitResultPublisher publisher;
  private final ProcessedEventGuard processedEvents;
  private final Clock clock;
  private final String consumerGroup;
  private final TransactionTemplate transaction;
  private final RetryTemplate optimisticRetry =
      new RetryTemplate(
          RetryPolicy.builder()
              .includes(OptimisticLockException.class, OptimisticLockingFailureException.class)
              .maxRetries(3)
              .delay(Duration.ofMillis(100))
              .multiplier(2)
              .maxDelay(Duration.ofMillis(400))
              .build());

  DebitStockService(
      ProductRepository products,
      StockMovementRepository movements,
      InvoiceDebitRepository debits,
      InvoiceDebitResultPublisher publisher,
      ProcessedEventGuard processedEvents,
      Clock clock,
      PlatformTransactionManager transactionManager,
      @Value("${spring.kafka.consumer.group-id}") String consumerGroup) {
    this.products = products;
    this.movements = movements;
    this.debits = debits;
    this.publisher = publisher;
    this.processedEvents = processedEvents;
    this.clock = clock;
    this.consumerGroup = consumerGroup;
    this.transaction = new TransactionTemplate(transactionManager);
    this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public void debit(Command command) {
    // The retry surrounds the transaction so every attempt reloads products and claims the event.
    optimisticRetry.invoke(() -> transaction.executeWithoutResult(status -> process(command)));
  }

  private void process(Command command) {
    if (!processedEvents.tryMark(command.eventId(), "stock.debit-requested", consumerGroup)) {
      return;
    }

    var existing = debits.findByPrintRequestId(command.printRequestId());
    if (existing.isPresent()) {
      InvoiceDebit original = existing.get();
      if (!original.getInvoiceId().equals(command.invoiceId())) {
        throw new IllegalStateException(
            "Print request " + command.printRequestId() + " belongs to a different invoice");
      }
      publisher.enqueue(original);
      return;
    }
    if (debits.hasConfirmedDebit(command.invoiceId())) {
      throw new IllegalStateException(
          "Invoice " + command.invoiceId() + " already has a confirmed stock debit");
    }

    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Result result = applyDebit(command, now);
    InvoiceDebit debit =
        new InvoiceDebit(
            command.printRequestId(),
            command.invoiceId(),
            command.invoiceNumber(),
            command.eventId(),
            result,
            now);
    debits.append(debit);
    publisher.enqueue(debit);
  }

  private Result applyDebit(Command command, Instant now) {
    Map<UUID, Product> byId =
        products.findAllById(command.items().stream().map(Item::productId).toList()).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    List<StockMovement> pending = new ArrayList<>();
    List<Failure> failures = new ArrayList<>();
    for (Item item : command.items()) {
      Product product = byId.get(item.productId());
      if (product == null) {
        failures.add(
            new Failure(
                RejectionReason.PRODUCT_NOT_FOUND,
                new RejectedItem(item.productId(), null, item.quantity().value(), null)));
        continue;
      }
      try {
        pending.add(
            product.debitForInvoice(
                item.quantity(),
                command.invoiceId(),
                command.invoiceNumber(),
                command.requestedBy(),
                now));
      } catch (ProductInactiveException failure) {
        failures.add(failureFor(RejectionReason.PRODUCT_INACTIVE, product, item));
      } catch (InsufficientStockException failure) {
        failures.add(failureFor(RejectionReason.INSUFFICIENT_STOCK, product, item));
      }
    }
    if (!failures.isEmpty()) {
      // D-035: the enum declares reasons in priority order.
      RejectionReason reason =
          failures.stream().map(Failure::reason).min(Comparator.naturalOrder()).orElseThrow();
      return new Rejected(reason, failures.stream().map(Failure::item).toList());
    }

    // Acquire product write locks in the same order across invoices. Keep event items in input
    // order.
    pending.stream()
        .map(StockMovement::getProductId)
        .sorted()
        .map(byId::get)
        .forEach(products::save);
    pending.forEach(movements::append);
    return new Confirmed(
        pending.stream()
            .map(
                movement ->
                    new ConfirmedItem(
                        movement.getProductId(),
                        movement.getQuantity().value(),
                        movement.getBalanceAfter().value()))
            .toList());
  }

  private static Failure failureFor(RejectionReason reason, Product product, Item item) {
    return new Failure(
        reason,
        new RejectedItem(
            product.getId(),
            product.getCode().value(),
            item.quantity().value(),
            product.getBalance().value()));
  }

  private record Failure(RejectionReason reason, RejectedItem item) {}
}
