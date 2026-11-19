package dev.eliascosta.invoice.stock.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.eliascosta.invoice.common.error.InvalidRequestException;
import dev.eliascosta.invoice.stock.domain.exception.InsufficientStockException;
import dev.eliascosta.invoice.stock.domain.exception.ProductInactiveException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductTest {

  private static final ProductCode CODE = new ProductCode(" par-m6 ");
  private static final Money PRICE = new Money(new BigDecimal("0.35"));
  private static final Instant CREATED_AT = Instant.parse("2026-11-19T10:00:00Z");
  private static final Instant UPDATED_AT = CREATED_AT.plusSeconds(1);

  @ParameterizedTest
  @ValueSource(ints = {0, 10})
  void createsActiveProductWithAnInitialMovement(int initialBalance) {
    Product.Creation creation = create(initialBalance);
    Product product = creation.product();
    StockMovement movement = creation.initialMovement();

    assertThat(product.getId().version()).isEqualTo(4);
    assertThat(product.isActive()).isTrue();
    assertThat(product.getVersion()).isZero();
    assertThat(product.getCode().value()).isEqualTo("PAR-M6");
    assertThat(product.getDescription()).isEqualTo("Parafuso");
    assertThat(product.getUnitPrice()).isEqualTo(PRICE);
    assertThat(product.getBalance()).isEqualTo(new Quantity(initialBalance));
    assertThat(product.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(product.getCreatedBy()).isEqualTo("admin");
    assertThat(product.getUpdatedAt()).isNull();
    assertThat(product.getUpdatedBy()).isNull();
    assertThat(movement.getId().version()).isEqualTo(4);
    assertThat(movement.getId()).isNotEqualTo(product.getId());
    assertThat(movement.getProductId()).isEqualTo(product.getId());
    assertThat(movement.getType()).isEqualTo(MovementType.INITIAL);
    assertThat(movement.getQuantity()).isEqualTo(product.getBalance());
    assertThat(movement.getBalanceAfter()).isEqualTo(product.getBalance());
    assertThat(movement.getCreatedAt()).isEqualTo(product.getCreatedAt());
    assertThat(movement.getCreatedBy()).isEqualTo(product.getCreatedBy());
    assertThat(movement.getReason()).isNull();
    assertThat(movement.getReferenceId()).isNull();
    assertThat(movement.getReferenceNumber()).isNull();
  }

  @Test
  void recordsEachMovementWithTheBalanceAtTheTime() {
    Product.Creation creation = create(2);
    Product product = creation.product();
    StockMovement credit = product.credit(new Quantity(5), "Recebimento", "operator", UPDATED_AT);
    StockMovement debit =
        product.debitManual(new Quantity(7), "Ajuste", "admin", UPDATED_AT.plusSeconds(1));

    assertThat(credit.getType()).isEqualTo(MovementType.INBOUND);
    assertThat(credit.getQuantity().value()).isEqualTo(5);
    assertThat(credit.getBalanceAfter().value()).isEqualTo(7);
    assertThat(credit.getProductId()).isEqualTo(product.getId());
    assertThat(credit.getReason()).isEqualTo("Recebimento");
    assertThat(credit.getCreatedBy()).isEqualTo("operator");
    assertThat(credit.getCreatedAt()).isEqualTo(UPDATED_AT);
    assertThat(credit.getReferenceId()).isNull();
    assertThat(credit.getReferenceNumber()).isNull();
    assertThat(debit.getType()).isEqualTo(MovementType.OUTBOUND);
    assertThat(debit.getQuantity().value()).isEqualTo(7);
    assertThat(debit.getReason()).isEqualTo("Ajuste");
    assertThat(debit.getBalanceAfter().value()).isZero();
    assertThat(product.getBalance()).isEqualTo(debit.getBalanceAfter());
    assertThat(product.getUpdatedBy()).isEqualTo(debit.getCreatedBy());
    assertThat(product.getUpdatedAt()).isEqualTo(debit.getCreatedAt());
    assertThat(creation.initialMovement().getBalanceAfter().value()).isEqualTo(2);
  }

  @Test
  void recordsInvoiceReferenceAndRequester() {
    Product product = create(6).product();
    UUID invoiceId = UUID.randomUUID();

    StockMovement debit =
        product.debitForInvoice(new Quantity(2), invoiceId, 123L, "operator", UPDATED_AT);

    assertThat(debit.getType()).isEqualTo(MovementType.INVOICE_DEBIT);
    assertThat(debit.getProductId()).isEqualTo(product.getId());
    assertThat(debit.getQuantity().value()).isEqualTo(2);
    assertThat(debit.getReferenceId()).isEqualTo(invoiceId);
    assertThat(debit.getReferenceNumber()).isEqualTo(123L);
    assertThat(debit.getReason()).isNull();
    assertThat(debit.getCreatedBy()).isEqualTo("operator");
    assertThat(debit.getCreatedAt()).isEqualTo(UPDATED_AT);
    assertThat(debit.getBalanceAfter().value()).isEqualTo(4);
    assertThat(product.getBalance()).isEqualTo(debit.getBalanceAfter());
  }

  @Test
  void refusesDebitsThatWouldMakeTheBalanceNegative() {
    Product product = create(1).product();
    Quantity requested = new Quantity(2);

    assertThatExceptionOfType(InsufficientStockException.class)
        .isThrownBy(() -> product.debitManual(requested, "Ajuste", "admin", UPDATED_AT))
        .satisfies(
            error -> {
              assertThat(error.getType()).isEqualTo("insufficient-stock");
              assertThat(error.getDetail()).contains("PAR-M6", "solicitado 2", "disponível 1");
            });
    assertThatExceptionOfType(InsufficientStockException.class)
        .isThrownBy(
            () -> product.debitForInvoice(requested, UUID.randomUUID(), 1, "admin", UPDATED_AT));
    assertThat(product.getBalance().value()).isEqualTo(1);
    assertThat(product.getUpdatedAt()).isNull();
  }

  @Test
  void rejectsNegativeQuantity() {
    assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> new Quantity(-1));
  }

  @Test
  void rejectsZeroQuantityForAllPostCreationMovements() {
    Product product = create(1).product();
    Quantity zero = new Quantity(0);

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> product.credit(zero, "Ajuste", "admin", UPDATED_AT));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> product.debitManual(zero, "Ajuste", "admin", UPDATED_AT));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> product.debitForInvoice(zero, UUID.randomUUID(), 1, "admin", UPDATED_AT));
    assertThat(product.getBalance().value()).isEqualTo(1);
    assertThat(product.getUpdatedAt()).isNull();
  }

  @Test
  void allowsTheMaximumBalanceAndRejectsOverflowWithoutChangingTheProduct() {
    Product product = create(Integer.MAX_VALUE - 1).product();
    StockMovement credit = product.credit(new Quantity(1), "Recebimento", "admin", UPDATED_AT);

    assertThat(credit.getBalanceAfter().value()).isEqualTo(Integer.MAX_VALUE);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            () ->
                product.credit(
                    new Quantity(1), "Recebimento", "operator", UPDATED_AT.plusSeconds(1)))
        .satisfies(error -> assertThat(error.getType()).isEqualTo("validation-error"));
    assertThat(product.getBalance().value()).isEqualTo(Integer.MAX_VALUE);
    assertThat(product.getUpdatedAt()).isEqualTo(UPDATED_AT);
    assertThat(product.getUpdatedBy()).isEqualTo("admin");
  }

  @Test
  void blocksDebitsWhenInactiveButAllowsCreditWithoutReactivation() {
    Product product = create(2).product();
    product.deactivate("admin", UPDATED_AT);

    assertThatExceptionOfType(ProductInactiveException.class)
        .isThrownBy(product::ensureActive)
        .satisfies(error -> assertThat(error.getType()).isEqualTo("product-inactive"));
    assertThatExceptionOfType(ProductInactiveException.class)
        .isThrownBy(() -> product.debitManual(new Quantity(1), "Ajuste", "admin", UPDATED_AT));
    assertThatExceptionOfType(ProductInactiveException.class)
        .isThrownBy(
            () ->
                product.debitForInvoice(
                    new Quantity(1), UUID.randomUUID(), 1, "admin", UPDATED_AT));
    assertThat(product.getBalance().value()).isEqualTo(2);

    StockMovement credit = product.credit(new Quantity(3), "Reposição", "admin", UPDATED_AT);
    assertThat(product.isActive()).isFalse();
    assertThat(credit.getType()).isEqualTo(MovementType.INBOUND);
    assertThat(credit.getBalanceAfter().value()).isEqualTo(5);
    assertThat(product.getBalance()).isEqualTo(credit.getBalanceAfter());
  }

  @Test
  void reactivatesTheSameProductAndAllowsDebitAgain() {
    Product product = create(5).product();
    UUID id = product.getId();
    product.deactivate("admin", UPDATED_AT);
    assertThat(product.isActive()).isFalse();
    assertThat(product.getBalance().value()).isEqualTo(5);

    Instant reactivationTime = UPDATED_AT.plusSeconds(1);
    product.reactivate("operator", reactivationTime);

    assertThat(product.isActive()).isTrue();
    assertThat(product.getId()).isEqualTo(id);
    assertThat(product.getCode()).isEqualTo(CODE);
    assertThat(product.getBalance().value()).isEqualTo(5);
    assertThat(product.getUpdatedBy()).isEqualTo("operator");
    assertThat(product.getUpdatedAt()).isEqualTo(reactivationTime);
    assertThat(
            product
                .debitManual(new Quantity(1), "Ajuste", "admin", reactivationTime)
                .getBalanceAfter()
                .value())
        .isEqualTo(4);
  }

  @Test
  void editsDescriptionAndPriceWhileKeepingTheCodeAndBalance() {
    Product product = create(6).product();
    Money newPrice = new Money(new BigDecimal("1.95"));

    product.changeDescription("Parafuso sextavado", "admin", UPDATED_AT);
    product.changePrice(newPrice, "admin", UPDATED_AT);

    assertThat(product.getDescription()).isEqualTo("Parafuso sextavado");
    assertThat(product.getUnitPrice()).isEqualTo(newPrice);
    assertThat(product.getCode()).isEqualTo(CODE);
    assertThat(product.getBalance().value()).isEqualTo(6);
  }

  @Test
  void rejectsInvalidDescriptionWithoutChangingState() {
    Product product = create(1).product();

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> product.changeDescription(" ", "admin", UPDATED_AT));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> product.changeDescription("A".repeat(201), "admin", UPDATED_AT));
    assertThat(product.getDescription()).isEqualTo("Parafuso");
    assertThat(product.getUpdatedAt()).isNull();
  }

  @Test
  void rejectsInvalidMovementMetadataBeforeChangingBalance() {
    Product product = create(2).product();
    Quantity quantity = new Quantity(1);

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> product.credit(quantity, null, "admin", UPDATED_AT));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> product.debitManual(quantity, " ", "admin", UPDATED_AT));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> product.debitManual(quantity, "A".repeat(201), "admin", UPDATED_AT));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> product.debitForInvoice(quantity, null, 1, "admin", UPDATED_AT));
    assertThat(product.getBalance().value()).isEqualTo(2);
    assertThat(product.getUpdatedAt()).isNull();
    assertThat(product.getUpdatedBy()).isNull();
  }

  @Test
  void restoresVersionAndAuditForThePersistenceRoundTrip() {
    UUID id = UUID.randomUUID();
    Product product =
        new Product(
            id,
            CODE,
            "Parafuso",
            PRICE,
            new Quantity(3),
            false,
            7,
            "admin",
            CREATED_AT,
            "operator",
            UPDATED_AT);

    assertThat(product.getId()).isEqualTo(id);
    assertThat(product.getVersion()).isEqualTo(7);
    assertThat(product.isActive()).isFalse();
    assertThat(product.getCreatedBy()).isEqualTo("admin");
    assertThat(product.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(product.getUpdatedBy()).isEqualTo("operator");
    assertThat(product.getUpdatedAt()).isEqualTo(UPDATED_AT);

    product.reactivate("admin", UPDATED_AT.plusSeconds(1));
    assertThat(product.getVersion()).isEqualTo(7);
  }

  private static Product.Creation create(int initialBalance) {
    return Product.create(
        CODE, "Parafuso", PRICE, new Quantity(initialBalance), "admin", CREATED_AT);
  }
}
