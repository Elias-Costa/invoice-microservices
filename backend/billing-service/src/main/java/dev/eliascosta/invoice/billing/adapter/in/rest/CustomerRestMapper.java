package dev.eliascosta.invoice.billing.adapter.in.rest;

import dev.eliascosta.invoice.billing.domain.model.Customer;
import dev.eliascosta.invoice.billing.domain.model.Document;
import dev.eliascosta.invoice.billing.domain.model.Email;
import dev.eliascosta.invoice.billing.domain.port.in.CustomerUseCases;
import dev.eliascosta.invoice.billing.domain.port.out.CustomerRepository;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tools.jackson.databind.JsonNode;

@Mapper
interface CustomerRestMapper {

  @Mapping(target = "document", source = "document.value")
  @Mapping(target = "documentType", expression = "java(customer.getDocument().type())")
  @Mapping(target = "email", source = "email.value")
  CustomerResponse toResponse(Customer customer);

  CustomerResponse toResponse(CustomerRepository.Summary customer);

  CustomerDetailResponse toDetail(CustomerUseCases.Detail detail);

  CustomerDetailResponse.RecentInvoice toRecentInvoice(InvoiceRepository.Summary invoice);

  CustomerUseCases.Create toCreate(CreateCustomerRequest request, String createdBy);

  CustomerUseCases.Update toUpdate(UUID id, UpdateCustomerRequest request, String updatedBy);

  default Document toDocument(String value) {
    return new Document(value);
  }

  default Email toEmail(String value) {
    return value == null ? null : new Email(value);
  }

  default String toDocumentValue(JsonNode value) {
    return value == null ? null : value.asString();
  }
}
