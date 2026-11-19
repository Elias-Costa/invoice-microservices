package dev.eliascosta.invoice.billing.domain.port.out;

import dev.eliascosta.invoice.billing.domain.model.Customer;
import dev.eliascosta.invoice.billing.domain.model.Document;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

  Optional<Customer> findById(UUID id);

  boolean existsByDocument(Document document);

  Customer save(Customer customer);
}
