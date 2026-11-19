package dev.eliascosta.invoice.stock.domain.port.out;

import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceDebitRepository {

  Optional<InvoiceDebit> findByPrintRequestId(UUID printRequestId);

  boolean hasConfirmedDebit(UUID invoiceId);

  void append(InvoiceDebit debit);
}
