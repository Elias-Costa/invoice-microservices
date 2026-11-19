package dev.eliascosta.invoice.billing.domain.port.in;

import dev.eliascosta.invoice.billing.domain.model.Invoice;
import dev.eliascosta.invoice.billing.domain.model.Quantity;
import dev.eliascosta.invoice.billing.domain.port.PageResult;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository;
import java.util.List;
import java.util.UUID;

public interface InvoiceUseCases {

  Invoice create(Create command);

  PageResult<InvoiceRepository.Summary> list(InvoiceRepository.Search query);

  Invoice get(UUID id);

  Invoice replaceItems(ReplaceItems command);

  Invoice cancel(Cancel command);

  record Create(UUID customerId, List<Item> items, String createdBy) {}

  record ReplaceItems(UUID id, List<Item> items, long version, String updatedBy) {}

  record Cancel(UUID id, String reason, String updatedBy) {}

  record Item(UUID productId, Quantity quantity) {}
}
