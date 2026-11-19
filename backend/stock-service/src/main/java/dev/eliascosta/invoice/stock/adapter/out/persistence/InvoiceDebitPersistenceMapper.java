package dev.eliascosta.invoice.stock.adapter.out.persistence;

import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.Confirmed;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.ConfirmedItem;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.Outcome;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.Rejected;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.RejectedItem;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.RejectionReason;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.Result;
import java.util.List;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Mapper
interface InvoiceDebitPersistenceMapper {

  TypeReference<List<ConfirmedItem>> CONFIRMED_DETAILS = new TypeReference<>() {};
  TypeReference<List<RejectedItem>> REJECTED_DETAILS = new TypeReference<>() {};

  @Mapping(target = "outcome", source = "result")
  @Mapping(target = "reason", source = "result")
  @Mapping(target = "details", source = "result")
  InvoiceDebitJpaEntity toEntity(InvoiceDebit debit, @Context JsonMapper json);

  @Mapping(target = "result", source = "entity")
  InvoiceDebit toDomain(InvoiceDebitJpaEntity entity, @Context JsonMapper json);

  default Outcome toOutcome(Result result) {
    return result instanceof Confirmed ? Outcome.CONFIRMED : Outcome.REJECTED;
  }

  default RejectionReason toReason(Result result) {
    return result instanceof Rejected rejected ? rejected.reason() : null;
  }

  default String toDetails(Result result, @Context JsonMapper json) {
    return switch (result) {
      case Confirmed confirmed -> json.writeValueAsString(confirmed.items());
      case Rejected rejected -> json.writeValueAsString(rejected.details());
    };
  }

  default Result toResult(InvoiceDebitJpaEntity entity, @Context JsonMapper json) {
    String details = entity.getDetails();
    return switch (entity.getOutcome()) {
      case CONFIRMED ->
          new Confirmed(details == null ? List.of() : json.readValue(details, CONFIRMED_DETAILS));
      case REJECTED ->
          new Rejected(
              entity.getReason(),
              details == null ? List.of() : json.readValue(details, REJECTED_DETAILS));
    };
  }
}
