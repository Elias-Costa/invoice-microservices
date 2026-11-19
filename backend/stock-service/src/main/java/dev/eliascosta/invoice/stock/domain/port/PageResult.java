package dev.eliascosta.invoice.stock.domain.port;

import java.util.List;

public record PageResult<T>(List<T> content, int number, int size, long totalElements) {

  public PageResult {
    if (size < 1) {
      throw new IllegalArgumentException("Page size must be positive");
    }
    content = List.copyOf(content);
  }

  public long totalPages() {
    return Math.ceilDiv(totalElements, size);
  }
}
