package dev.eliascosta.invoice.stock.domain.port.out;

import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit;

public interface InvoiceDebitResultPublisher {

  void enqueue(InvoiceDebit debit);
}
