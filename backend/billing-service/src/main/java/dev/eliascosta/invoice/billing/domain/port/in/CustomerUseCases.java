package dev.eliascosta.invoice.billing.domain.port.in;

import dev.eliascosta.invoice.billing.domain.model.Customer;
import dev.eliascosta.invoice.billing.domain.model.Document;
import dev.eliascosta.invoice.billing.domain.model.Email;
import dev.eliascosta.invoice.billing.domain.port.PageResult;
import dev.eliascosta.invoice.billing.domain.port.out.CustomerRepository;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository;
import java.util.List;
import java.util.UUID;

public interface CustomerUseCases {

  Customer create(Create command);

  PageResult<CustomerRepository.Summary> list(CustomerRepository.Search query);

  Detail get(UUID id);

  Customer update(Update command);

  void deactivate(UUID id, String updatedBy);

  Customer reactivate(UUID id, String updatedBy);

  record Create(String name, Document document, Email email, String phone, String createdBy) {}

  record Update(
      UUID id,
      String name,
      String document,
      Email email,
      String phone,
      long version,
      String updatedBy) {}

  record Detail(Customer customer, List<InvoiceRepository.Summary> recentInvoices) {}
}
