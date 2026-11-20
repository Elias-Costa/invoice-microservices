package dev.eliascosta.invoice.billing.application.service;

import static dev.eliascosta.invoice.billing.domain.port.in.StockDebitResultUseCase.RejectionReason.INSUFFICIENT_STOCK;
import static dev.eliascosta.invoice.billing.domain.port.in.StockDebitResultUseCase.RejectionReason.PRODUCT_INACTIVE;
import static dev.eliascosta.invoice.billing.domain.port.in.StockDebitResultUseCase.RejectionReason.PRODUCT_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;

import dev.eliascosta.invoice.billing.domain.port.in.StockDebitResultUseCase.RejectedItem;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StockDebitRejectionMessageTest {

  @Test
  void usesTheGeneralReasonAndKeepsKnownAndMissingDetailsInEventOrder() {
    UUID missing = UUID.randomUUID();
    var details =
        List.of(
            new RejectedItem(UUID.randomUUID(), "PRIMEIRO", 12, 35),
            new RejectedItem(missing, null, 3, null),
            new RejectedItem(UUID.randomUUID(), "TERCEIRO", 4, 1));

    assertThat(StockDebitRejectionMessage.format(PRODUCT_NOT_FOUND, details))
        .isEqualTo(
            "Produto não encontrado para PRIMEIRO (solicitado 12, disponível 35)"
                + " · "
                + missing
                + " (solicitado 3) · TERCEIRO (solicitado 4, disponível 1)");
    assertThat(StockDebitRejectionMessage.format(PRODUCT_INACTIVE, details.subList(0, 1)))
        .isEqualTo("Produto inativo para PRIMEIRO (solicitado 12, disponível 35)");
  }

  @Test
  void keepsWholeItemsAndCountsThoseOmittedAtTheEnd() {
    var details =
        IntStream.range(0, 12)
            .mapToObj(
                i ->
                    new RejectedItem(
                        UUID.randomUUID(), "PRODUTO-COM-CODIGO-LONGO-%02d".formatted(i), 3, 1))
            .toList();

    String reason = StockDebitRejectionMessage.format(INSUFFICIENT_STOCK, details);

    assertThat(reason.length()).isLessThanOrEqualTo(500);
    assertThat(reason)
        .startsWith(
            "Saldo insuficiente para PRODUTO-COM-CODIGO-LONGO-00 (solicitado 3, disponível 1)")
        .contains("PRODUTO-COM-CODIGO-LONGO-06 (solicitado 3, disponível 1)")
        .doesNotContain("PRODUTO-COM-CODIGO-LONGO-07")
        .endsWith("(+5 itens omitidos)");
  }
}
