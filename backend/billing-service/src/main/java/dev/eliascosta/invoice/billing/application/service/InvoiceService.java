package dev.eliascosta.invoice.billing.application.service;

import dev.eliascosta.invoice.billing.domain.exception.DuplicateInvoiceItemException;
import dev.eliascosta.invoice.billing.domain.exception.InvalidInvoiceReferenceException;
import dev.eliascosta.invoice.billing.domain.exception.InvoiceNotFoundException;
import dev.eliascosta.invoice.billing.domain.model.Customer;
import dev.eliascosta.invoice.billing.domain.model.Invoice;
import dev.eliascosta.invoice.billing.domain.model.InvoiceItem;
import dev.eliascosta.invoice.billing.domain.model.Money;
import dev.eliascosta.invoice.billing.domain.port.PageResult;
import dev.eliascosta.invoice.billing.domain.port.in.InvoiceUseCases;
import dev.eliascosta.invoice.billing.domain.port.out.CustomerRepository;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository;
import dev.eliascosta.invoice.billing.domain.port.out.StockDebitRequestPublisher;
import dev.eliascosta.invoice.billing.domain.port.out.StockGateway;
import dev.eliascosta.invoice.billing.domain.port.out.StockGateway.ProductSnapshot;
import dev.eliascosta.invoice.common.error.ConflictException;
import dev.eliascosta.invoice.common.error.ProblemTypes;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class InvoiceService implements InvoiceUseCases {

  private final CustomerRepository customers;
  private final InvoiceRepository invoices;
  private final StockGateway stock;
  private final StockDebitRequestPublisher printRequests;
  private final Clock clock;

  InvoiceService(
      CustomerRepository customers,
      InvoiceRepository invoices,
      StockGateway stock,
      StockDebitRequestPublisher printRequests,
      Clock clock) {
    this.customers = customers;
    this.invoices = invoices;
    this.stock = stock;
    this.printRequests = printRequests;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Invoice create(Create command) {
    Customer customer =
        customers
            .findById(command.customerId())
            .orElseThrow(
                () -> InvalidInvoiceReferenceException.customerNotFound(command.customerId()));
    customer.ensureActive();
    return invoices.save(
        Invoice.create(customer, snapshotItems(command.items()), command.createdBy(), now()));
  }

  @Override
  public PageResult<InvoiceRepository.Summary> list(InvoiceRepository.Search query) {
    return invoices.search(query);
  }

  @Override
  public Invoice get(UUID id) {
    return requireInvoice(id);
  }

  @Override
  @Transactional
  public Invoice replaceItems(ReplaceItems command) {
    Invoice invoice = requireInvoice(command.id());
    invoice.ensureOpen();
    if (invoice.getVersion() != command.version()) {
      throw new ConflictException(
          ProblemTypes.OPTIMISTIC_LOCK_CONFLICT,
          "Conflito de edição",
          "A nota foi alterada. Recarregue os dados e tente novamente.");
    }
    invoice.replaceItems(snapshotItems(command.items()), command.updatedBy(), now());
    return invoices.save(invoice);
  }

  @Override
  @Transactional
  public Invoice cancel(Cancel command) {
    Invoice invoice = requireInvoice(command.id());
    invoice.cancel(command.reason(), command.updatedBy(), now());
    return invoices.save(invoice);
  }

  @Override
  @Transactional
  public Invoice print(Print command) {
    Invoice invoice = requireInvoice(command.id());
    invoice.requestPrint(command.requestedBy(), now());
    Invoice saved = invoices.save(invoice);
    printRequests.enqueue(saved);
    return saved;
  }

  private Invoice requireInvoice(UUID id) {
    return invoices.findById(id).orElseThrow(() -> new InvoiceNotFoundException(id));
  }

  private Instant now() {
    // Match PostgreSQL timestamp precision so write and read responses agree.
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  private List<InvoiceItem.Input> snapshotItems(List<Item> items) {
    Set<UUID> ids = new LinkedHashSet<>();
    for (Item item : items) {
      if (!ids.add(item.productId())) {
        throw new DuplicateInvoiceItemException(item.productId());
      }
    }

    Map<UUID, ProductSnapshot> products =
        stock.findProducts(ids).stream()
            .collect(Collectors.toMap(ProductSnapshot::id, Function.identity()));
    List<InvoiceItem.Input> snapshots = new ArrayList<>(items.size());
    for (Item item : items) {
      ProductSnapshot product = products.get(item.productId());
      if (product == null) {
        throw InvalidInvoiceReferenceException.productNotFound(item.productId());
      }
      if (!product.active()) {
        throw InvalidInvoiceReferenceException.productInactive(item.productId());
      }
      snapshots.add(
          new InvoiceItem.Input(
              product.id(),
              product.code(),
              product.description(),
              new Money(product.unitPrice()),
              item.quantity()));
    }
    return snapshots;
  }
}
