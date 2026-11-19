package dev.eliascosta.invoice.billing.adapter.in.rest;

import dev.eliascosta.invoice.billing.domain.port.PageResult;
import java.util.List;
import java.util.function.Function;

record PageResponse<T>(List<T> content, Page page) {

  static <S, T> PageResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
    return new PageResponse<>(
        result.content().stream().map(mapper).toList(),
        new Page(result.number(), result.size(), result.totalElements(), result.totalPages()));
  }

  record Page(int number, int size, long totalElements, long totalPages) {}
}
