package dev.eliascosta.invoice.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.eliascosta.invoice.billing.domain.exception.CustomerInactiveException;
import dev.eliascosta.invoice.common.error.InvalidRequestException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerTest {

  private static final Instant CREATED_AT = Instant.parse("2026-09-12T10:00:00Z");
  private static final Instant UPDATED_AT = CREATED_AT.plusSeconds(60);
  private static final Document DOCUMENT = new Document("52998224725");

  @Test
  void createsActiveCustomerWithOptionalContactFieldsAbsent() {
    Customer customer =
        Customer.create("Ana Ribeiro", DOCUMENT, null, null, "operator", CREATED_AT);

    assertThat(customer.getId()).isNotNull();
    assertThat(customer.getName()).isEqualTo("Ana Ribeiro");
    assertThat(customer.getDocument()).isEqualTo(DOCUMENT);
    assertThat(customer.getEmail()).isNull();
    assertThat(customer.getPhone()).isNull();
    assertThat(customer.isActive()).isTrue();
    assertThat(customer.getVersion()).isZero();
    assertThat(customer.getCreatedBy()).isEqualTo("operator");
    assertThat(customer.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(customer.getUpdatedBy()).isNull();
    assertThat(customer.getUpdatedAt()).isNull();
  }

  @Test
  void editsContactFieldsAndPreservesIdentityAndDocument() {
    UUID id = UUID.randomUUID();
    Customer customer =
        new Customer(
            id, "Ana Ribeiro", DOCUMENT, null, null, true, 7, "operator", CREATED_AT, null, null);
    Email email = new Email("ana.ribeiro+invoice@example.com");

    customer.edit("Ana Paula Ribeiro", email, "5585999999999", "admin", UPDATED_AT);

    assertThat(customer.getId()).isEqualTo(id);
    assertThat(customer.getDocument()).isEqualTo(DOCUMENT);
    assertThat(customer.getName()).isEqualTo("Ana Paula Ribeiro");
    assertThat(customer.getEmail()).isEqualTo(email);
    assertThat(customer.getPhone()).isEqualTo("5585999999999");
    assertThat(customer.getVersion()).isEqualTo(7);
    assertThat(customer.getUpdatedBy()).isEqualTo("admin");
    assertThat(customer.getUpdatedAt()).isEqualTo(UPDATED_AT);

    customer.edit("Ana Paula Ribeiro", null, null, "admin", UPDATED_AT.plusSeconds(1));
    assertThat(customer.getEmail()).isNull();
    assertThat(customer.getPhone()).isNull();
  }

  @Test
  void deactivatesAndReactivatesCustomerWithAudit() {
    Customer customer =
        Customer.create("Ana Ribeiro", DOCUMENT, null, null, "operator", CREATED_AT);

    customer.deactivate("admin", UPDATED_AT);

    assertThat(customer.isActive()).isFalse();
    assertThat(customer.getUpdatedBy()).isEqualTo("admin");
    assertThat(customer.getUpdatedAt()).isEqualTo(UPDATED_AT);
    assertThatThrownBy(customer::ensureActive)
        .isInstanceOfSatisfying(
            CustomerInactiveException.class,
            error -> assertThat(error.getType()).isEqualTo("customer-inactive"));

    customer.reactivate("another-admin", UPDATED_AT.plusSeconds(60));

    customer.ensureActive();
    assertThat(customer.isActive()).isTrue();
    assertThat(customer.getUpdatedBy()).isEqualTo("another-admin");
    assertThat(customer.getUpdatedAt()).isEqualTo(UPDATED_AT.plusSeconds(60));
  }

  @Test
  void rejectsMissingNameOrDocument() {
    assertViolation(
        "name", () -> Customer.create(" ", DOCUMENT, null, null, "operator", CREATED_AT));
    assertViolation(
        "document", () -> Customer.create("Ana Ribeiro", null, null, null, "operator", CREATED_AT));
  }

  @Test
  void rejectsInvalidEditsBeforeChangingNameOrAudit() {
    Customer customer =
        Customer.create("Ana Ribeiro", DOCUMENT, null, null, "operator", CREATED_AT);

    assertViolation("name", () -> customer.edit("a".repeat(151), null, null, "admin", UPDATED_AT));
    assertViolation(
        "phone", () -> customer.edit("Novo nome", null, "+5585999999999", "admin", UPDATED_AT));

    assertThat(customer.getName()).isEqualTo("Ana Ribeiro");
    assertThat(customer.getUpdatedBy()).isNull();
    assertThat(customer.getUpdatedAt()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "ana", "ana@", "ana r@example.com", "ana..r@example.com", "ana@-example.com"})
  void rejectsMalformedEmail(String email) {
    assertViolation("email", () -> new Email(email));
  }

  private static void assertViolation(String field, Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            InvalidRequestException.class,
            error -> {
              assertThat(error.getType()).isEqualTo("validation-error");
              assertThat(error.getProperties())
                  .containsEntry(
                      "errors", List.of(Map.of("field", field, "message", error.getDetail())));
            });
  }
}
