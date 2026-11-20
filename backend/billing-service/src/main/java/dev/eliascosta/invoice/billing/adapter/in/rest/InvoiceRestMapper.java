package dev.eliascosta.invoice.billing.adapter.in.rest;

import dev.eliascosta.invoice.billing.adapter.in.rest.InvoiceResponse.Action;
import dev.eliascosta.invoice.billing.domain.model.Invoice;
import dev.eliascosta.invoice.billing.domain.model.InvoiceItem;
import dev.eliascosta.invoice.billing.domain.model.InvoiceStatus;
import dev.eliascosta.invoice.billing.domain.model.Quantity;
import dev.eliascosta.invoice.billing.domain.port.in.InvoiceUseCases;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface InvoiceRestMapper {

  @Mapping(target = "customer", source = "invoice")
  @Mapping(target = "total", source = "total.value")
  @Mapping(target = "allowedActions", source = "status")
  InvoiceResponse toResponse(Invoice invoice);

  PrintInvoiceResponse toPrintResponse(Invoice invoice);

  @Mapping(target = "id", source = "customerId")
  @Mapping(target = "name", source = "customerName")
  @Mapping(target = "document", source = "customerDocument.value")
  InvoiceResponse.CustomerSnapshot toCustomerSnapshot(Invoice invoice);

  @Mapping(target = "unitPrice", source = "unitPrice.value")
  @Mapping(target = "quantity", source = "quantity.value")
  @Mapping(target = "lineTotal", source = "lineTotal.value")
  InvoiceResponse.Item toItem(InvoiceItem item);

  InvoiceSummaryResponse toSummary(InvoiceRepository.Summary invoice);

  InvoiceUseCases.Create toCreate(CreateInvoiceRequest request, String createdBy);

  InvoiceUseCases.ReplaceItems toReplaceItems(
      UUID id, ReplaceInvoiceItemsRequest request, String updatedBy);

  InvoiceUseCases.Cancel toCancel(UUID id, CancelInvoiceRequest request, String updatedBy);

  InvoiceUseCases.Item toItemCommand(InvoiceItemRequest request);

  default Quantity toQuantity(BigDecimal value) {
    return new Quantity(value.intValueExact());
  }

  default long toVersion(BigDecimal value) {
    return value.longValueExact();
  }

  default List<Action> toActions(InvoiceStatus status) {
    return status == InvoiceStatus.OPEN
        ? List.of(Action.EDIT_ITEMS, Action.CANCEL, Action.PRINT)
        : List.of();
  }
}
