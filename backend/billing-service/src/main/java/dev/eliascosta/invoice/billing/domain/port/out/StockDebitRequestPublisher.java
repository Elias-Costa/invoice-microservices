package dev.eliascosta.invoice.billing.domain.port.out;

import dev.eliascosta.invoice.billing.domain.model.Invoice;

public interface StockDebitRequestPublisher {

  void enqueue(Invoice invoice);
}
