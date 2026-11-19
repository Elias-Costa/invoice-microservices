package dev.eliascosta.invoice.stock.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.eliascosta.invoice.common.error.InvalidRequestException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockMovementTest {

  private static final Instant CREATED_AT = Instant.parse("2026-11-19T10:00:00Z");

  @Test
  void restoresThePersistedIdentityAndInvoiceReference() {
    UUID id = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();
    StockMovement movement =
        new StockMovement(
            id,
            productId,
            MovementType.INVOICE_DEBIT,
            new Quantity(1),
            new Quantity(0),
            null,
            invoiceId,
            17L,
            "admin",
            CREATED_AT);

    assertThat(movement.getId()).isEqualTo(id);
    assertThat(movement.getProductId()).isEqualTo(productId);
    assertThat(movement.getReferenceId()).isEqualTo(invoiceId);
    assertThat(movement.getReferenceNumber()).isEqualTo(17L);
  }

  @Test
  void rejectsInvoiceReferencesOnManualMovements() {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            () ->
                StockMovement.create(
                    UUID.randomUUID(),
                    MovementType.OUTBOUND,
                    new Quantity(1),
                    new Quantity(0),
                    "Ajuste",
                    UUID.randomUUID(),
                    17L,
                    "admin",
                    CREATED_AT));
  }

  @Test
  void requiresTheNumberWhenRestoringAnInvoiceDebit() {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            () ->
                StockMovement.create(
                    UUID.randomUUID(),
                    MovementType.INVOICE_DEBIT,
                    new Quantity(1),
                    new Quantity(0),
                    null,
                    UUID.randomUUID(),
                    null,
                    "admin",
                    CREATED_AT));
  }

  @Test
  void initialMovementMustMatchTheInitialBalance() {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            () ->
                StockMovement.create(
                    UUID.randomUUID(),
                    MovementType.INITIAL,
                    new Quantity(1),
                    new Quantity(2),
                    null,
                    null,
                    null,
                    "admin",
                    CREATED_AT));
  }
}
