package dev.eliascosta.invoice.billing.application.service;

import dev.eliascosta.invoice.billing.domain.model.Invoice;
import dev.eliascosta.invoice.billing.domain.model.InvoiceStatus;
import dev.eliascosta.invoice.billing.domain.port.in.StockDebitResultUseCase;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository;
import dev.eliascosta.invoice.common.messaging.ProcessedEventGuard;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StockDebitResultService implements StockDebitResultUseCase {

  private final InvoiceRepository invoices;
  private final ProcessedEventGuard processedEvents;
  private final Clock clock;
  private final String consumerGroup;

  StockDebitResultService(
      InvoiceRepository invoices,
      ProcessedEventGuard processedEvents,
      Clock clock,
      @Value("${spring.kafka.consumer.group-id}") String consumerGroup) {
    this.invoices = invoices;
    this.processedEvents = processedEvents;
    this.clock = clock;
    this.consumerGroup = consumerGroup;
  }

  @Override
  @Transactional
  public void confirm(Result result) {
    applyResult(
        result,
        "stock.debit-confirmed",
        invoice -> invoice.confirmClose(clock.instant().truncatedTo(ChronoUnit.MICROS)));
  }

  @Override
  @Transactional
  public void reject(Result result, RejectionReason reason, List<RejectedItem> details) {
    applyResult(
        result,
        "stock.debit-rejected",
        invoice ->
            invoice.rejectClose(
                StockDebitRejectionMessage.format(reason, details),
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
  }

  private void applyResult(Result result, String eventType, Consumer<Invoice> transition) {
    if (!processedEvents.tryMark(result.eventId(), eventType, consumerGroup)) {
      return;
    }
    Invoice invoice =
        invoices
            .findById(result.invoiceId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Stock debit result has no invoice " + result.invoiceId()));
    if (invoice.getStatus() != InvoiceStatus.PROCESSING
        || !result.printRequestId().equals(invoice.getPrintRequestId())) {
      return;
    }
    transition.accept(invoice);
    invoices.save(invoice);
  }
}
