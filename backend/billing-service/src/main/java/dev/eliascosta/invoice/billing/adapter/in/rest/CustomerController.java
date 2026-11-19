package dev.eliascosta.invoice.billing.adapter.in.rest;

import dev.eliascosta.invoice.billing.domain.exception.ImmutableCustomerFieldException;
import dev.eliascosta.invoice.billing.domain.port.in.CustomerUseCases;
import dev.eliascosta.invoice.billing.domain.port.out.CustomerRepository;
import dev.eliascosta.invoice.common.error.InvalidRequestException;
import dev.eliascosta.invoice.common.error.ProblemTypes;
import dev.eliascosta.invoice.common.idempotency.IdempotencyUserResolver;
import dev.eliascosta.invoice.common.idempotency.Idempotent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(CustomerController.PATH)
class CustomerController {

  static final String PATH = "/api/billing/customers";
  private static final String AUDIT_USER = IdempotencyUserResolver.ANONYMOUS;
  private static final Set<String> SORT_FIELDS = Set.of("name", "document", "active");

  private final CustomerUseCases customers;
  private final CustomerRestMapper mapper;

  CustomerController(CustomerUseCases customers, CustomerRestMapper mapper) {
    this.customers = customers;
    this.mapper = mapper;
  }

  @PostMapping
  @Idempotent
  ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
    CustomerResponse customer =
        mapper.toResponse(customers.create(mapper.toCreate(request, AUDIT_USER)));
    return ResponseEntity.created(URI.create(PATH + "/" + customer.id())).body(customer);
  }

  @GetMapping
  PageResponse<CustomerResponse> list(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "true") boolean active,
      @RequestParam(defaultValue = "0") @PositiveOrZero int page,
      @RequestParam(defaultValue = "20") @Positive int size,
      @RequestParam(required = false) String sort) {
    String[] parts = sort == null ? new String[] {"name", "asc"} : sort.split(",", -1);
    if (parts.length != 2
        || !SORT_FIELDS.contains(parts[0])
        || !(parts[1].equals("asc") || parts[1].equals("desc"))) {
      String message = "Use name, document ou active, seguido de asc ou desc.";
      throw new InvalidRequestException(
          ProblemTypes.VALIDATION_ERROR,
          "Dados inválidos",
          "Ordenação inválida.",
          Map.of("errors", List.of(Map.of("field", "sort", "message", message))));
    }
    var query =
        new CustomerRepository.Search(search, active, page, size, parts[0], parts[1].equals("asc"));
    return PageResponse.from(customers.list(query), mapper::toResponse);
  }

  @GetMapping("/{id}")
  CustomerDetailResponse get(@PathVariable UUID id) {
    return mapper.toDetail(customers.get(id));
  }

  @PutMapping("/{id}")
  CustomerResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
    if (request.active() != null) {
      throw new ImmutableCustomerFieldException(
          "active", "Use a rota de inativação ou reativação.");
    }
    if (request.document() != null && !request.document().isString()) {
      throw new ImmutableCustomerFieldException("document", "O documento do cliente é imutável.");
    }
    return mapper.toResponse(customers.update(mapper.toUpdate(id, request, AUDIT_USER)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deactivate(@PathVariable UUID id) {
    customers.deactivate(id, AUDIT_USER);
  }

  @PostMapping("/{id}/reactivate")
  CustomerResponse reactivate(@PathVariable UUID id) {
    return mapper.toResponse(customers.reactivate(id, AUDIT_USER));
  }
}
