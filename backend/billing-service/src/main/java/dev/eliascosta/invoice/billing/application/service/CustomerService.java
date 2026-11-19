package dev.eliascosta.invoice.billing.application.service;

import dev.eliascosta.invoice.billing.domain.exception.CustomerDocumentAlreadyExistsException;
import dev.eliascosta.invoice.billing.domain.exception.CustomerNotFoundException;
import dev.eliascosta.invoice.billing.domain.exception.ImmutableCustomerFieldException;
import dev.eliascosta.invoice.billing.domain.model.Customer;
import dev.eliascosta.invoice.billing.domain.port.PageResult;
import dev.eliascosta.invoice.billing.domain.port.in.CustomerUseCases;
import dev.eliascosta.invoice.billing.domain.port.out.CustomerRepository;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository;
import dev.eliascosta.invoice.common.error.ConflictException;
import dev.eliascosta.invoice.common.error.ProblemTypes;
import java.time.Clock;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CustomerService implements CustomerUseCases {

  private static final int RECENT_INVOICE_LIMIT = 5;

  private final CustomerRepository customers;
  private final InvoiceRepository invoices;
  private final Clock clock;

  CustomerService(CustomerRepository customers, InvoiceRepository invoices, Clock clock) {
    this.customers = customers;
    this.invoices = invoices;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Customer create(Create command) {
    if (customers.existsByDocument(command.document())) {
      throw new CustomerDocumentAlreadyExistsException();
    }
    Customer customer =
        Customer.create(
            command.name(),
            command.document(),
            command.email(),
            command.phone(),
            command.createdBy(),
            clock.instant());
    try {
      return customers.save(customer);
    } catch (DataIntegrityViolationException failure) {
      // A concurrent create can pass the existence check before either insert commits.
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
        if (cause instanceof ConstraintViolationException violation
            && "uk_customers_document".equals(violation.getConstraintName())) {
          throw new CustomerDocumentAlreadyExistsException();
        }
      }
      throw failure;
    }
  }

  @Override
  public PageResult<CustomerRepository.Summary> list(CustomerRepository.Search query) {
    return customers.search(query);
  }

  @Override
  public Detail get(UUID id) {
    Customer customer = requireCustomer(id);
    return new Detail(customer, invoices.findRecentByCustomerId(id, RECENT_INVOICE_LIMIT));
  }

  @Override
  @Transactional
  public Customer update(Update command) {
    Customer customer = requireCustomer(command.id());
    if (command.document() != null && !customer.getDocument().value().equals(command.document())) {
      throw new ImmutableCustomerFieldException("document", "O documento do cliente é imutável.");
    }
    if (customer.getVersion() != command.version()) {
      throw new ConflictException(
          ProblemTypes.OPTIMISTIC_LOCK_CONFLICT,
          "Conflito de edição",
          "O cliente foi alterado. Recarregue os dados e tente novamente.");
    }
    customer.edit(
        command.name(), command.email(), command.phone(), command.updatedBy(), clock.instant());
    return customers.save(customer);
  }

  @Override
  @Transactional
  public void deactivate(UUID id, String updatedBy) {
    Customer customer = requireCustomer(id);
    if (customer.isActive()) {
      customer.deactivate(updatedBy, clock.instant());
      customers.save(customer);
    }
  }

  @Override
  @Transactional
  public Customer reactivate(UUID id, String updatedBy) {
    Customer customer = requireCustomer(id);
    if (customer.isActive()) {
      return customer;
    }
    customer.reactivate(updatedBy, clock.instant());
    return customers.save(customer);
  }

  private Customer requireCustomer(UUID id) {
    return customers.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
  }
}
