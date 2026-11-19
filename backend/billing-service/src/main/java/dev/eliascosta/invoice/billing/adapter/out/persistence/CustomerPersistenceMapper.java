package dev.eliascosta.invoice.billing.adapter.out.persistence;

import dev.eliascosta.invoice.billing.domain.model.Customer;
import dev.eliascosta.invoice.billing.domain.model.Document;
import dev.eliascosta.invoice.billing.domain.model.Email;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface CustomerPersistenceMapper {

  @Mapping(target = "documentType", source = "document")
  CustomerJpaEntity toEntity(Customer customer);

  void updateEntity(Customer customer, @MappingTarget CustomerJpaEntity entity);

  Customer toDomain(CustomerJpaEntity entity);

  default String documentValue(Document document) {
    return document.value();
  }

  default Document.Type documentType(Document document) {
    return document.type();
  }

  default Document toDocument(String value) {
    return new Document(value);
  }

  default String emailValue(Email email) {
    return email == null ? null : email.value();
  }

  default Email toEmail(String value) {
    return value == null ? null : new Email(value);
  }
}
