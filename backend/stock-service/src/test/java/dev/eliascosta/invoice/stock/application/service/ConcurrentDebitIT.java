package dev.eliascosta.invoice.stock.application.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.Confirmed;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.ConfirmedItem;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.Rejected;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.RejectedItem;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.RejectionReason;
import dev.eliascosta.invoice.stock.domain.model.Money;
import dev.eliascosta.invoice.stock.domain.model.MovementType;
import dev.eliascosta.invoice.stock.domain.model.Product;
import dev.eliascosta.invoice.stock.domain.model.ProductCode;
import dev.eliascosta.invoice.stock.domain.model.Quantity;
import dev.eliascosta.invoice.stock.domain.port.in.CreateProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.DebitStockUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.DebitStockUseCase.Command;
import dev.eliascosta.invoice.stock.domain.port.in.DebitStockUseCase.Item;
import dev.eliascosta.invoice.stock.domain.port.out.InvoiceDebitRepository;
import dev.eliascosta.invoice.stock.domain.port.out.ProductRepository;
import dev.eliascosta.invoice.stock.domain.port.out.StockMovementRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ConcurrentDebitIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("stock_db");

  @Container @ServiceConnection
  static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:4.2.1");

  @Autowired private CreateProductUseCase createProduct;
  @Autowired private DebitStockUseCase debitStock;
  @Autowired private InvoiceDebitRepository debits;
  @Autowired private StockMovementRepository movements;
  @MockitoSpyBean private ProductRepository products;

  @RepeatedTest(5)
  void confirmsOnlyOneDebitWhenTwoInvoicesCompeteForTheLastUnit(RepetitionInfo repetition)
      throws Exception {
    Product initial =
        createProduct.create(
            new CreateProductUseCase.Command(
                new ProductCode("RACE-" + repetition.getCurrentRepetition()),
                "Last unit",
                new Money(BigDecimal.ONE),
                new Quantity(1),
                "operator"));
    UUID productId = initial.getId();
    var first = request(productId, 3);
    var second = request(productId, 4);

    var bothRead = new CountDownLatch(2);
    var snapshots = new ConcurrentLinkedQueue<StockSnapshot>();
    doAnswer(
            invocation -> {
              var found = (List<?>) invocation.callRealMethod();
              Product product = (Product) found.getFirst();
              snapshots.add(new StockSnapshot(product.getVersion(), product.getBalance().value()));
              // Let both transactions load the same version before either can write it.
              bothRead.countDown();
              assertThat(bothRead.await(10, SECONDS)).as("both transactions loaded stock").isTrue();
              return found;
            })
        .when(products)
        .findAllById(List.of(productId));

    // Invoke the use case directly so the retry can only come from the local transaction loop.
    try (var workers = Executors.newFixedThreadPool(2)) {
      var firstResult = workers.submit(() -> debitStock.debit(first));
      var secondResult = workers.submit(() -> debitStock.debit(second));
      firstResult.get(20, SECONDS);
      secondResult.get(20, SECONDS);
    }

    var before = new StockSnapshot(initial.getVersion(), 1);
    assertThat(snapshots)
        .containsExactly(before, before, new StockSnapshot(initial.getVersion() + 1, 0));

    var results =
        List.of(
            debits.findByPrintRequestId(first.printRequestId()).orElseThrow(),
            debits.findByPrintRequestId(second.printRequestId()).orElseThrow());
    assertThat(results)
        .extracting(InvoiceDebit::getResult)
        .containsExactlyInAnyOrder(
            new Confirmed(List.of(new ConfirmedItem(productId, 1, 0))),
            new Rejected(
                RejectionReason.INSUFFICIENT_STOCK,
                List.of(new RejectedItem(productId, initial.getCode().value(), 1, 0))));
    var confirmed =
        results.stream()
            .filter(debit -> debit.getResult() instanceof Confirmed)
            .findFirst()
            .orElseThrow();

    Product current = products.findById(productId).orElseThrow();
    assertThat(current.getBalance().value()).isZero();
    assertThat(current.getVersion()).isEqualTo(initial.getVersion() + 1);
    assertThat(movements.findByProductId(productId, 0, 10).content())
        .filteredOn(movement -> movement.getType() == MovementType.INVOICE_DEBIT)
        .singleElement()
        .satisfies(
            movement -> {
              assertThat(movement.getReferenceId()).isEqualTo(confirmed.getInvoiceId());
              assertThat(movement.getQuantity().value()).isOne();
              assertThat(movement.getBalanceAfter().value()).isZero();
            });
  }

  private static Command request(UUID productId, long invoiceNumber) {
    return new Command(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        invoiceNumber,
        "operator",
        List.of(new Item(productId, new Quantity(1))));
  }

  private record StockSnapshot(long version, int balance) {}
}
