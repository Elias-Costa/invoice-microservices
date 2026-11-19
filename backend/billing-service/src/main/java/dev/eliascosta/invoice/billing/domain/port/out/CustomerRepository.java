package dev.eliascosta.invoice.billing.domain.port.out;

import dev.eliascosta.invoice.billing.domain.model.Customer;
import dev.eliascosta.invoice.billing.domain.model.Document;
import dev.eliascosta.invoice.billing.domain.port.PageResult;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

  Optional<Customer> findById(UUID id);

  boolean existsByDocument(Document document);

  Customer save(Customer customer);

  PageResult<Summary> search(Search query);

  record Search(
      String search, boolean active, int page, int size, String sortBy, boolean ascending) {}

  record Summary(
      UUID id,
      String name,
      String document,
      Document.Type documentType,
      String email,
      String phone,
      boolean active,
      long version) {}
}
