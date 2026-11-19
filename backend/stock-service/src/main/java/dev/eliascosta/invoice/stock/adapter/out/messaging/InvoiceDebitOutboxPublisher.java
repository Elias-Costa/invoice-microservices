package dev.eliascosta.invoice.stock.adapter.out.messaging;

import dev.eliascosta.invoice.common.event.EventEnvelope;
import dev.eliascosta.invoice.common.outbox.OutboxWriter;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.Confirmed;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.ConfirmedItem;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.Rejected;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.RejectedItem;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.RejectionReason;
import dev.eliascosta.invoice.stock.domain.port.out.InvoiceDebitResultPublisher;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class InvoiceDebitOutboxPublisher implements InvoiceDebitResultPublisher {

  private final OutboxWriter outbox;

  InvoiceDebitOutboxPublisher(OutboxWriter outbox) {
    this.outbox = outbox;
  }

  @Override
  public void enqueue(InvoiceDebit debit) {
    String subject = debit.getInvoiceId().toString();
    EventEnvelope<?> event =
        switch (debit.getResult()) {
          case Confirmed confirmed ->
              EventEnvelope.of(
                  "stock.debit-confirmed",
                  "stock-service",
                  subject,
                  new ConfirmedPayload(
                      debit.getPrintRequestId(), debit.getInvoiceId(), confirmed.items()));
          case Rejected rejected ->
              EventEnvelope.of(
                  "stock.debit-rejected",
                  "stock-service",
                  subject,
                  new RejectedPayload(
                      debit.getPrintRequestId(),
                      debit.getInvoiceId(),
                      rejected.reason(),
                      rejected.details()));
        };
    outbox.write(event.type(), subject, "Invoice", debit.getInvoiceId(), event);
  }

  private record ConfirmedPayload(UUID printRequestId, UUID invoiceId, List<ConfirmedItem> items) {}

  private record RejectedPayload(
      UUID printRequestId, UUID invoiceId, RejectionReason reason, List<RejectedItem> details) {}
}
