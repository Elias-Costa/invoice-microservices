package dev.eliascosta.invoice.common.error.web;

import dev.eliascosta.invoice.common.error.BusinessRuleException;
import dev.eliascosta.invoice.common.error.ConflictException;
import dev.eliascosta.invoice.common.error.InvalidRequestException;
import dev.eliascosta.invoice.common.error.InvalidStateException;
import dev.eliascosta.invoice.common.error.NotFoundException;
import dev.eliascosta.invoice.common.error.UpstreamUnavailableException;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Throws one exception per route so the advice can be exercised end to end through MockMvc. */
@RestController
@RequestMapping("/test")
class ErrorTestController {

  static final String UNEXPECTED_INTERNALS = "SELECT * FROM products WHERE secret = 42";

  record SampleRequest(@NotBlank String name, @Min(1) int quantity) {}

  private final Validator validator;

  ErrorTestController(Validator validator) {
    this.validator = validator;
  }

  @PostMapping("/validation")
  SampleRequest validation(@Valid @RequestBody SampleRequest request) {
    return request;
  }

  @GetMapping("/params")
  String params(@RequestParam @Min(0) int page) {
    return "page " + page;
  }

  @GetMapping("/header")
  String header(@RequestHeader("X-Required") String required) {
    return required;
  }

  @GetMapping("/constraint-violation")
  void constraintViolation() {
    throw new ConstraintViolationException(validator.validate(new SampleRequest(" ", 0)));
  }

  @GetMapping("/not-found")
  void notFound() {
    throw new NotFoundException(
        "product-not-found", "Produto não encontrado", "Produto 7b1e não existe.");
  }

  @GetMapping("/conflict")
  void conflict() {
    throw new ConflictException(
        "product-code-already-exists",
        "Código de produto já cadastrado",
        "Já existe um produto com o código PARAFUSO-M6.");
  }

  @GetMapping("/invalid-state")
  void invalidState() {
    throw new InvalidStateException(
        "invalid-invoice-status",
        "Transição de status inválida",
        "Nota em PROCESSING não pode ser cancelada.",
        Map.of("currentStatus", "PROCESSING", "allowedTransitions", List.of("CLOSED", "OPEN")));
  }

  @GetMapping("/business-rule")
  void businessRule() {
    throw new BusinessRuleException(
        "insufficient-stock",
        "Saldo insuficiente",
        "Saldo insuficiente para PARAFUSO-M6: solicitado 3, disponível 1");
  }

  @GetMapping("/invalid-request")
  void invalidRequest() {
    throw new InvalidRequestException(
        "duplicate-invoice-item",
        "Item duplicado",
        "O produto PARAFUSO-M6 aparece mais de uma vez.");
  }

  @GetMapping("/upstream")
  void upstream() {
    throw new UpstreamUnavailableException(
        "stock-service-unavailable",
        "Serviço de estoque indisponível",
        "Não foi possível consultar o estoque. Tente novamente em instantes.",
        new IllegalStateException("connection refused"));
  }

  @GetMapping("/optimistic-lock/spring")
  void optimisticLockSpring() {
    throw new OptimisticLockingFailureException("row was updated by another transaction");
  }

  @GetMapping("/optimistic-lock/jpa")
  void optimisticLockJpa() {
    throw new OptimisticLockException("row was updated by another transaction");
  }

  @GetMapping("/data-integrity")
  void dataIntegrity(@RequestParam String sqlState) {
    SQLException root = new SQLException("ERROR: constraint violated", sqlState);
    throw new DataIntegrityViolationException(
        "could not execute statement", new RuntimeException("wrapped by the ORM", root));
  }

  @GetMapping("/duplicate-key")
  void duplicateKey() {
    throw new DuplicateKeyException("duplicate key value violates unique constraint");
  }

  @GetMapping("/unexpected")
  void unexpected() {
    throw new IllegalStateException(UNEXPECTED_INTERNALS);
  }
}
