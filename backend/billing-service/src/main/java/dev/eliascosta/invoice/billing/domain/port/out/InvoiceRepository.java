package dev.eliascosta.invoice.billing.domain.port.out;

import dev.eliascosta.invoice.billing.domain.model.Invoice;
import dev.eliascosta.invoice.billing.domain.model.InvoiceStatus;
import dev.eliascosta.invoice.billing.domain.port.PageResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {

  Optional<Invoice> findById(UUID id);

  Invoice save(Invoice invoice);

  PageResult<Summary> search(Search query);

  record Search(
      InvoiceStatus status,
      UUID customerId,
      Instant from,
      Instant to,
      int page,
      int size,
      String sortBy,
      boolean ascending) {}

  record Summary(
      UUID id,
      Long number,
      InvoiceStatus status,
      UUID customerId,
      String customerName,
      BigDecimal total,
      Instant createdAt) {}
}
