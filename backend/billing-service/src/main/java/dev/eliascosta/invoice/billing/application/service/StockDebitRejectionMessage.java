package dev.eliascosta.invoice.billing.application.service;

import dev.eliascosta.invoice.billing.domain.model.Invoice;
import dev.eliascosta.invoice.billing.domain.port.in.StockDebitResultUseCase.RejectedItem;
import dev.eliascosta.invoice.billing.domain.port.in.StockDebitResultUseCase.RejectionReason;
import java.util.List;

final class StockDebitRejectionMessage {

  private StockDebitRejectionMessage() {}

  static String format(RejectionReason reason, List<RejectedItem> details) {
    String title =
        switch (reason) {
          case INSUFFICIENT_STOCK -> "Saldo insuficiente";
          case PRODUCT_INACTIVE -> "Produto inativo";
          case PRODUCT_NOT_FOUND -> "Produto não encontrado";
        };
    StringBuilder message = new StringBuilder(title);
    int length = title.length();
    for (int i = 0; i < details.size(); i++) {
      String detail = (i == 0 ? " para " : " · ") + formatItem(details.get(i));
      int detailLength = detail.codePointCount(0, detail.length());
      String remaining = omitted(details.size() - i - 1);
      if (length + detailLength + remaining.length() > Invoice.MAX_FAILURE_REASON_LENGTH) {
        return message.append(omitted(details.size() - i)).toString();
      }
      message.append(detail);
      length += detailLength;
    }
    return message.toString();
  }

  private static String formatItem(RejectedItem item) {
    String identifier =
        item.productCode() != null ? item.productCode() : item.productId().toString();
    String available = item.available() == null ? "" : ", disponível " + item.available();
    return identifier + " (solicitado " + item.requested() + available + ")";
  }

  private static String omitted(int count) {
    return switch (count) {
      case 0 -> "";
      case 1 -> " (+1 item omitido)";
      default -> " (+%d itens omitidos)".formatted(count);
    };
  }
}
