package dev.eliascosta.invoice.billing.adapter.in.rest;

import static dev.eliascosta.invoice.billing.adapter.in.rest.InvoiceController.PATH;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.eliascosta.invoice.billing.domain.model.InvoiceStatus;
import dev.eliascosta.invoice.billing.domain.port.PageResult;
import dev.eliascosta.invoice.billing.domain.port.in.InvoiceUseCases;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository;
import dev.eliascosta.invoice.common.error.web.ErrorHandlingAutoConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvoiceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(InvoiceRestMapperImpl.class)
@ImportAutoConfiguration(ErrorHandlingAutoConfiguration.class)
class InvoiceControllerTest {

  private static final UUID INVOICE = UUID.fromString("024ebbf5-997c-4ee4-967c-200317e4226b");
  private static final UUID CUSTOMER = UUID.fromString("6f14dc62-893e-437c-9c5c-80204134c409");
  private static final UUID PRODUCT = UUID.fromString("719c2177-db22-4b8c-acfd-8c4002707d34");
  private static final String VALID_ITEMS =
      """
      [{"productId":"%s","quantity":1}]
      """
          .formatted(PRODUCT);

  @Autowired private MockMvc mvc;
  @MockitoBean private InvoiceUseCases invoices;

  @Test
  void listsOpenInvoicesWithTheContractedSummaryAndDefaultSort() throws Exception {
    var query =
        new InvoiceRepository.Search(InvoiceStatus.OPEN, null, null, null, 0, 20, "number", false);
    var row =
        new InvoiceRepository.Summary(
            INVOICE,
            21L,
            InvoiceStatus.OPEN,
            CUSTOMER,
            "Cliente",
            new BigDecimal("19.90"),
            Instant.parse("2026-11-19T10:00:00Z"));
    when(invoices.list(query)).thenReturn(new PageResult<>(List.of(row), 0, 20, 1));

    mvc.perform(get(PATH).param("status", "OPEN").param("page", "0").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(INVOICE.toString()))
        .andExpect(jsonPath("$.content[0].number").value(21))
        .andExpect(jsonPath("$.content[0].status").value("OPEN"))
        .andExpect(jsonPath("$.content[0].customerId").value(CUSTOMER.toString()))
        .andExpect(jsonPath("$.content[0].customerName").value("Cliente"))
        .andExpect(jsonPath("$.content[0].total").value(19.9))
        .andExpect(jsonPath("$.content[0].createdAt").value("2026-11-19T10:00:00Z"))
        .andExpect(jsonPath("$.page.number").value(0))
        .andExpect(jsonPath("$.page.size").value(20))
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.page.totalPages").value(1));
  }

  @ParameterizedTest
  @CsvSource({
    "status, UNKNOWN",
    "customerId, invalid",
    "from, invalid",
    "page, -1",
    "size, 0",
    "sort, 'version,asc'",
    "sort, 'number,unknown'"
  })
  void rejectsInvalidListParameters(String name, String value) throws Exception {
    mvc.perform(get(PATH).param(name, value))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"));
    verifyNoInteractions(invoices);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "1.5", "2147483648", "null"})
  void rejectsInvalidItemQuantities(String quantity) throws Exception {
    String items =
        """
        [{"productId":"%s","quantity":%s}]
        """
            .formatted(PRODUCT, quantity);
    mvc.perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + CUSTOMER + "\",\"items\":" + items + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"));
    verifyNoInteractions(invoices);
  }

  @ParameterizedTest
  @ValueSource(strings = {"[]", "[null]", "[{}]"})
  void rejectsEmptyOrIncompleteItemLists(String items) throws Exception {
    mvc.perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + CUSTOMER + "\",\"items\":" + items + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"));
    verifyNoInteractions(invoices);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "-1", "0.5", "9223372036854775808"})
  void rejectsAnEditWithAnInvalidVersion(String version) throws Exception {
    mvc.perform(
            put(PATH + "/{id}/items", INVOICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":" + VALID_ITEMS + ",\"version\":" + version + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("version"));
    verifyNoInteractions(invoices);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "\"6f14dc62-893e-437c-9c5c-80204134c409\""})
  void rejectsAnyCustomerIdInTheEditRequest(String customerId) throws Exception {
    mvc.perform(
            put(PATH + "/{id}/items", INVOICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"items\":"
                        + VALID_ITEMS
                        + ",\"version\":0,\"customerId\":"
                        + customerId
                        + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:field-immutable"))
        .andExpect(jsonPath("$.errors[0].field").value("customerId"));
    verifyNoInteractions(invoices);
  }

  @Test
  void rejectsBlankCancellationReason() throws Exception {
    mvc.perform(
            post(PATH + "/{id}/cancel", INVOICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("reason"));
    verifyNoInteractions(invoices);
  }
}
