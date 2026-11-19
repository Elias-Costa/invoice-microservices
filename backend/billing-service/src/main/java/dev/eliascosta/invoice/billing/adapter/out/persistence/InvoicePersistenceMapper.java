package dev.eliascosta.invoice.billing.adapter.out.persistence;

import dev.eliascosta.invoice.billing.domain.model.Document;
import dev.eliascosta.invoice.billing.domain.model.Invoice;
import dev.eliascosta.invoice.billing.domain.model.InvoiceItem;
import dev.eliascosta.invoice.billing.domain.model.InvoiceStatusHistory;
import dev.eliascosta.invoice.billing.domain.model.Money;
import dev.eliascosta.invoice.billing.domain.model.Quantity;
import java.math.BigDecimal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface InvoicePersistenceMapper {

  @Mapping(target = "customerDocument", source = "customerDocument.value")
  @Mapping(target = "total", source = "total.value")
  @Mapping(target = "items", ignore = true)
  @Mapping(target = "history", ignore = true)
  InvoiceJpaEntity toEntity(Invoice invoice);

  @Mapping(target = "total", source = "total.value")
  @Mapping(target = "items", ignore = true)
  @Mapping(target = "history", ignore = true)
  void updateEntity(Invoice invoice, @MappingTarget InvoiceJpaEntity entity);

  Invoice toDomain(InvoiceJpaEntity entity);

  @Mapping(target = "unitPrice", source = "unitPrice.value")
  @Mapping(target = "quantity", source = "quantity.value")
  @Mapping(target = "lineTotal", source = "lineTotal.value")
  InvoiceItemJpaEntity toEntity(InvoiceItem item);

  InvoiceItem toDomain(InvoiceItemJpaEntity entity);

  InvoiceStatusHistoryJpaEntity toEntity(InvoiceStatusHistory entry);

  InvoiceStatusHistory toDomain(InvoiceStatusHistoryJpaEntity entity);

  default Document toDocument(String value) {
    return new Document(value);
  }

  default Money toMoney(BigDecimal value) {
    return new Money(value);
  }

  default Quantity toQuantity(int value) {
    return new Quantity(value);
  }
}
