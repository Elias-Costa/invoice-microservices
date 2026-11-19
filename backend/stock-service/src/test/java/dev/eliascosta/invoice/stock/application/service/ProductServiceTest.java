package dev.eliascosta.invoice.stock.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eliascosta.invoice.common.error.ConflictException;
import dev.eliascosta.invoice.common.error.InvalidRequestException;
import dev.eliascosta.invoice.stock.domain.exception.ImmutableProductFieldException;
import dev.eliascosta.invoice.stock.domain.exception.ProductCodeAlreadyExistsException;
import dev.eliascosta.invoice.stock.domain.exception.ProductNotFoundException;
import dev.eliascosta.invoice.stock.domain.model.Money;
import dev.eliascosta.invoice.stock.domain.model.MovementType;
import dev.eliascosta.invoice.stock.domain.model.Product;
import dev.eliascosta.invoice.stock.domain.model.ProductCode;
import dev.eliascosta.invoice.stock.domain.model.Quantity;
import dev.eliascosta.invoice.stock.domain.model.StockMovement;
import dev.eliascosta.invoice.stock.domain.port.PageResult;
import dev.eliascosta.invoice.stock.domain.port.ProductSearch;
import dev.eliascosta.invoice.stock.domain.port.in.CreateProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.RegisterStockMovementUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.UpdateProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.out.ProductRepository;
import dev.eliascosta.invoice.stock.domain.port.out.StockMovementRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-19T16:00:00Z");
  private static final ProductCode CODE = new ProductCode("Parafuso-M6");
  private static final Money PRICE = new Money(new BigDecimal("2.30"));
  private static final Quantity BALANCE = new Quantity(5);
  private static final String USER = "anonymous";
  private static final UUID ID = UUID.fromString("6f14dc62-893e-437c-9c5c-80204134c409");
  private static final long VERSION = 3L;

  @Mock private ProductRepository products;
  @Mock private StockMovementRepository movements;
  private ProductService service;

  @BeforeEach
  void setUp() {
    service = new ProductService(products, movements, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void createsTheProductAndAppendsItsInitialMovement() {
    when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Product saved = service.create(createCommand());

    ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
    verify(movements).append(movement.capture());
    assertThat(saved.getCode()).isEqualTo(CODE);
    assertThat(saved.getBalance()).isEqualTo(BALANCE);
    assertThat(movement.getValue().getProductId()).isEqualTo(saved.getId());
    assertThat(movement.getValue().getType()).isEqualTo(MovementType.INITIAL);
    assertThat(movement.getValue().getQuantity()).isEqualTo(BALANCE);
    assertThat(movement.getValue().getBalanceAfter()).isEqualTo(BALANCE);
    assertThat(movement.getValue().getCreatedAt()).isEqualTo(NOW);
    assertThat(movement.getValue().getCreatedBy()).isEqualTo(USER);
  }

  @Test
  void rejectsExistingNormalizedCodeBeforeWriting() {
    when(products.existsByCode(CODE)).thenReturn(true);

    assertThatThrownBy(() -> service.create(createCommand()))
        .isInstanceOf(ProductCodeAlreadyExistsException.class);
    verify(products, never()).save(any());
    verifyNoInteractions(movements);
  }

  @Test
  void mapsTheUniqueConstraintWhenAnotherCreationWinsTheRace(
      @Mock ConstraintViolationException violation) {
    when(violation.getConstraintName()).thenReturn("uk_products_code");
    when(products.save(any()))
        .thenThrow(new DataIntegrityViolationException("duplicate", violation));

    assertThatThrownBy(() -> service.create(createCommand()))
        .isInstanceOf(ProductCodeAlreadyExistsException.class);
    verifyNoInteractions(movements);
  }

  @Test
  void preservesAnUnrelatedIntegrityFailureAndDoesNotAppendTheMovement(
      @Mock ConstraintViolationException violation) {
    when(violation.getConstraintName()).thenReturn("ck_products_balance");
    var failure = new DataIntegrityViolationException("check constraint", violation);
    when(products.save(any())).thenThrow(failure);

    assertThatThrownBy(() -> service.create(createCommand())).isSameAs(failure);
    verifyNoInteractions(movements);
  }

  @Test
  void updatesDescriptionAndPriceWithoutChangingCodeOrBalance() {
    Product product = restoredProduct(true);
    when(products.findById(ID)).thenReturn(Optional.of(product));
    when(products.save(product)).thenReturn(product);

    Product updated = service.update(updateCommand(new ProductCode(" parafuso-M6 "), VERSION));

    assertThat(updated.getCode()).isEqualTo(CODE);
    assertThat(updated.getBalance()).isEqualTo(BALANCE);
    assertThat(updated.getDescription()).isEqualTo("Nova descrição");
    assertThat(updated.getUnitPrice().value()).isEqualByComparingTo("3.50");
    assertThat(updated.getUpdatedBy()).isEqualTo(USER);
    assertThat(updated.getUpdatedAt()).isEqualTo(NOW);
    verifyNoInteractions(movements);
  }

  @Test
  void rejectsModificationOfTheProductCode() {
    when(products.findById(ID)).thenReturn(Optional.of(restoredProduct(true)));

    assertThatThrownBy(() -> service.update(updateCommand(new ProductCode("OUTRO"), VERSION)))
        .isInstanceOf(ImmutableProductFieldException.class);
    verify(products, never()).save(any());
  }

  @Test
  void rejectsAStaleVersionBeforeWriting() {
    when(products.findById(ID)).thenReturn(Optional.of(restoredProduct(true)));

    assertThatThrownBy(() -> service.update(updateCommand(null, VERSION - 1)))
        .isInstanceOfSatisfying(
            ConflictException.class,
            failure -> assertThat(failure.getType()).isEqualTo("optimistic-lock-conflict"));
    verify(products, never()).save(any());
  }

  @Test
  void readsExistingAndMissingProductsById() {
    Product product = restoredProduct(false);
    when(products.findById(ID)).thenReturn(Optional.of(product));
    UUID missing = UUID.randomUUID();

    assertThat(service.get(ID)).isSameAs(product);
    assertThatThrownBy(() -> service.get(missing)).isInstanceOf(ProductNotFoundException.class);
    assertThatThrownBy(() -> service.deactivate(missing, USER))
        .isInstanceOf(ProductNotFoundException.class);
    assertThatThrownBy(() -> service.reactivate(missing, USER))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  void delegatesPageAndBatchReadsToTheProductRepository() {
    Product product = restoredProduct(false);
    ProductSearch query = new ProductSearch("par", false, 0, 20, "code", true);
    PageResult<Product> page = new PageResult<>(List.of(product), 0, 20, 1);
    List<UUID> ids = List.of(ID, UUID.randomUUID());
    when(products.search(query)).thenReturn(page);
    when(products.findAllById(ids)).thenReturn(List.of(product));

    assertThat(service.list(query)).isSameAs(page);
    assertThat(service.getByIds(ids)).containsExactly(product);
  }

  @Test
  void writesOnlyWhenDeactivationOrReactivationChangesTheState() {
    Product product = restoredProduct(true);
    when(products.findById(ID)).thenReturn(Optional.of(product));
    when(products.save(product)).thenReturn(product);

    service.deactivate(ID, USER);
    service.deactivate(ID, USER);
    assertThat(product.isActive()).isFalse();
    verify(products).save(product);

    assertThat(service.reactivate(ID, USER).isActive()).isTrue();
    assertThat(service.reactivate(ID, USER).isActive()).isTrue();
    verify(products, times(2)).save(product);
    verifyNoInteractions(movements);
  }

  @Test
  void rejectsAnInvoiceDebitThroughTheManualMovementPort() {
    when(products.findById(ID)).thenReturn(Optional.of(restoredProduct(true)));
    var command =
        new RegisterStockMovementUseCase.Command(
            ID, MovementType.INVOICE_DEBIT, new Quantity(1), "Venda", USER);

    assertThatThrownBy(() -> service.register(command))
        .isInstanceOfSatisfying(
            InvalidRequestException.class,
            failure -> assertThat(failure.getType()).isEqualTo("validation-error"));
    verify(products, never()).save(any());
    verifyNoInteractions(movements);
  }

  @Test
  void doesNotAppendTheMovementWhenTheProductSaveHasAnOptimisticConflict() {
    Product product = restoredProduct(true);
    when(products.findById(ID)).thenReturn(Optional.of(product));
    var conflict = new ObjectOptimisticLockingFailureException(Product.class, ID);
    when(products.save(product)).thenThrow(conflict);
    var command =
        new RegisterStockMovementUseCase.Command(
            ID, MovementType.OUTBOUND, new Quantity(1), "Ajuste", USER);

    assertThatThrownBy(() -> service.register(command)).isSameAs(conflict);
    verifyNoInteractions(movements);
  }

  private static CreateProductUseCase.Command createCommand() {
    return new CreateProductUseCase.Command(CODE, "Parafuso", PRICE, BALANCE, USER);
  }

  private static UpdateProductUseCase.Command updateCommand(ProductCode code, long version) {
    return new UpdateProductUseCase.Command(
        ID, code, "Nova descrição", new Money(new BigDecimal("3.50")), version, USER);
  }

  private static Product restoredProduct(boolean active) {
    return new Product(
        ID,
        CODE,
        "Parafuso",
        PRICE,
        BALANCE,
        active,
        VERSION,
        USER,
        NOW.minusSeconds(2),
        null,
        null);
  }
}
