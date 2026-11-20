package dev.eliascosta.invoice.billing.adapter.in.rest;

import dev.eliascosta.invoice.billing.domain.model.InvoiceStatus;
import dev.eliascosta.invoice.billing.domain.port.in.InvoiceUseCases;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository;
import dev.eliascosta.invoice.common.error.InvalidRequestException;
import dev.eliascosta.invoice.common.error.ProblemTypes;
import dev.eliascosta.invoice.common.idempotency.IdempotencyUserResolver;
import dev.eliascosta.invoice.common.idempotency.Idempotent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(InvoiceController.PATH)
class InvoiceController {

  static final String PATH = "/api/billing/invoices";
  private static final String AUDIT_USER = IdempotencyUserResolver.ANONYMOUS;
  private static final Set<String> SORT_FIELDS =
      Set.of("number", "status", "customerName", "total", "createdAt");

  private final InvoiceUseCases invoices;
  private final InvoiceRestMapper mapper;

  InvoiceController(InvoiceUseCases invoices, InvoiceRestMapper mapper) {
    this.invoices = invoices;
    this.mapper = mapper;
  }

  @PostMapping
  @Idempotent
  ResponseEntity<InvoiceResponse> create(@Valid @RequestBody CreateInvoiceRequest request) {
    InvoiceResponse invoice =
        mapper.toResponse(invoices.create(mapper.toCreate(request, AUDIT_USER)));
    return ResponseEntity.created(URI.create(PATH + "/" + invoice.id())).body(invoice);
  }

  @GetMapping
  PageResponse<InvoiceSummaryResponse> list(
      @RequestParam(required = false) InvoiceStatus status,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") @PositiveOrZero int page,
      @RequestParam(defaultValue = "20") @Positive int size,
      @RequestParam(required = false) String sort) {
    String[] parts = sort == null ? new String[] {"number", "desc"} : sort.split(",", -1);
    if (parts.length != 2
        || !SORT_FIELDS.contains(parts[0])
        || !(parts[1].equals("asc") || parts[1].equals("desc"))) {
      String message =
          "Use number, status, customerName, total ou createdAt, seguido de asc ou desc.";
      throw new InvalidRequestException(
          ProblemTypes.VALIDATION_ERROR,
          "Dados inválidos",
          "Ordenação inválida.",
          Map.of("errors", List.of(Map.of("field", "sort", "message", message))));
    }
    var query =
        new InvoiceRepository.Search(
            status, customerId, from, to, page, size, parts[0], parts[1].equals("asc"));
    return PageResponse.from(invoices.list(query), mapper::toSummary);
  }

  @GetMapping("/{id}")
  InvoiceResponse get(@PathVariable UUID id) {
    return mapper.toResponse(invoices.get(id));
  }

  @PutMapping("/{id}/items")
  InvoiceResponse replaceItems(
      @PathVariable UUID id, @Valid @RequestBody ReplaceInvoiceItemsRequest request) {
    if (request.customerId() != null) {
      String message = "O cliente da nota é imutável.";
      throw new InvalidRequestException(
          "field-immutable",
          "Campo imutável",
          message,
          Map.of("errors", List.of(Map.of("field", "customerId", "message", message))));
    }
    return mapper.toResponse(invoices.replaceItems(mapper.toReplaceItems(id, request, AUDIT_USER)));
  }

  @PostMapping("/{id}/cancel")
  InvoiceResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelInvoiceRequest request) {
    return mapper.toResponse(invoices.cancel(mapper.toCancel(id, request, AUDIT_USER)));
  }

  @PostMapping("/{id}/print")
  @Idempotent
  ResponseEntity<PrintInvoiceResponse> print(@PathVariable UUID id) {
    var invoice = invoices.print(new InvoiceUseCases.Print(id, AUDIT_USER));
    return ResponseEntity.accepted()
        .location(URI.create(PATH + "/" + id))
        .body(mapper.toPrintResponse(invoice));
  }
}
