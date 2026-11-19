package dev.eliascosta.invoice.stock.adapter.in.rest;

import static dev.eliascosta.invoice.stock.adapter.in.rest.ProductController.PATH;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.eliascosta.invoice.common.error.ConflictException;
import dev.eliascosta.invoice.common.error.ProblemTypes;
import dev.eliascosta.invoice.common.error.web.ErrorHandlingAutoConfiguration;
import dev.eliascosta.invoice.stock.domain.exception.ImmutableProductFieldException;
import dev.eliascosta.invoice.stock.domain.exception.ProductCodeAlreadyExistsException;
import dev.eliascosta.invoice.stock.domain.exception.ProductNotFoundException;
import dev.eliascosta.invoice.stock.domain.model.Money;
import dev.eliascosta.invoice.stock.domain.model.Product;
import dev.eliascosta.invoice.stock.domain.model.ProductCode;
import dev.eliascosta.invoice.stock.domain.model.Quantity;
import dev.eliascosta.invoice.stock.domain.port.PageResult;
import dev.eliascosta.invoice.stock.domain.port.ProductSearch;
import dev.eliascosta.invoice.stock.domain.port.in.CreateProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.DeactivateProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.GetProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.GetProductsByIdsUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.ListProductsUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.ReactivateProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.UpdateProductUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ProductRestMapperImpl.class)
@ImportAutoConfiguration(ErrorHandlingAutoConfiguration.class)
class ProductControllerTest {

  private static final UUID ID = UUID.fromString("6f14dc62-893e-437c-9c5c-80204134c409");
  private static final UUID MISSING_ID = UUID.fromString("eb12bc2a-c936-4fae-a47f-1bea845fe8ce");
  private static final ProductCode CODE = new ProductCode("PAR-M6");
  private static final Money PRICE = new Money(new BigDecimal("1.20"));
  private static final Quantity BALANCE = new Quantity(5);
  private static final String CREATE_JSON =
      """
      {"code":" par-m6 ","description":"Parafuso M6","unitPrice":1.20,"initialBalance":5}
      """;
  private static final String UPDATE_JSON =
      """
      {"description":"Parafuso M6","unitPrice":1.20,"version":0}
      """;

  @Autowired private MockMvc mvc;
  @MockitoBean private CreateProductUseCase create;
  @MockitoBean private UpdateProductUseCase update;
  @MockitoBean private ListProductsUseCase list;
  @MockitoBean private GetProductUseCase getProduct;
  @MockitoBean private GetProductsByIdsUseCase getByIds;
  @MockitoBean private DeactivateProductUseCase deactivate;
  @MockitoBean private ReactivateProductUseCase reactivate;

  @Test
  void returnsCreatedProductWithLocationAndInitialBalance() throws Exception {
    var command =
        new CreateProductUseCase.Command(CODE, "Parafuso M6", PRICE, BALANCE, "anonymous");
    when(create.create(command)).thenReturn(product(true, 0));

    mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", PATH + "/" + ID))
        .andExpect(jsonPath("$.id").value(ID.toString()))
        .andExpect(jsonPath("$.code").value("PAR-M6"))
        .andExpect(jsonPath("$.unitPrice").value(1.20))
        .andExpect(jsonPath("$.balance").value(5))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void reportsAllInvalidCreateFields() throws Exception {
    mvc.perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":" ","description":"Parafuso","unitPrice":-1,"initialBalance":-2}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"))
        .andExpect(
            jsonPath(
                "$.errors[*].field", containsInAnyOrder("code", "unitPrice", "initialBalance")));
    verifyNoInteractions(create);
  }

  @Test
  void rendersTheSpecificDuplicateCodeProblem() throws Exception {
    when(create.create(any())).thenThrow(new ProductCodeAlreadyExistsException(CODE));

    mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:problem-type:product-code-already-exists"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.5", "\"5\""})
  void rejectsInitialBalancesThatAreNotJsonIntegers(String balance) throws Exception {
    String request =
        """
        {"code":"PAR-M6","description":"Parafuso M6","unitPrice":1.20,"initialBalance":%s}
        """
            .formatted(balance);
    mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"));
    verifyNoInteractions(create);
  }

  @Test
  void returnsProductPageWithTheContractedMetadata() throws Exception {
    var query = new ProductSearch("Par", false, 1, 2, "balance", false);
    when(list.list(query)).thenReturn(new PageResult<>(List.of(product(false, 1)), 1, 2, 3));

    mvc.perform(
            get(PATH)
                .param("search", "Par")
                .param("active", "false")
                .param("page", "1")
                .param("size", "2")
                .param("sort", "balance,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].active").value(false))
        .andExpect(jsonPath("$.page.number").value(1))
        .andExpect(jsonPath("$.page.size").value(2))
        .andExpect(jsonPath("$.page.totalElements").value(3))
        .andExpect(jsonPath("$.page.totalPages").value(2));
  }

  @Test
  void defaultsToActiveProductsOnTheFirstPageSortedByCode() throws Exception {
    var query = new ProductSearch(null, true, 0, 20, "code", true);
    when(list.list(query)).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

    mvc.perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.page.size").value(20));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "notAField,asc", "balance,sideways", "description", "active,asc,code"})
  void rejectsInvalidSort(String sort) throws Exception {
    mvc.perform(get(PATH).param("sort", sort))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"))
        .andExpect(jsonPath("$.errors[0].field").value("sort"));
    verifyNoInteractions(list);
  }

  @Test
  void rejectsNegativePageAndZeroSize() throws Exception {
    mvc.perform(get(PATH).param("page", "-1").param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("page", "size")));
    verifyNoInteractions(list);
  }

  @Test
  void getsAnInactiveProductById() throws Exception {
    when(getProduct.get(ID)).thenReturn(product(false, 4));

    mvc.perform(get(PATH + "/{id}", ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.version").value(4));
  }

  @Test
  void getsNotFoundForAnUnknownProductAndBadRequestForAMalformedId() throws Exception {
    when(getProduct.get(MISSING_ID)).thenThrow(new ProductNotFoundException(MISSING_ID));

    mvc.perform(get(PATH + "/{id}", MISSING_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:problem-type:product-not-found"));
    mvc.perform(get(PATH + "/wrong"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"));
  }

  @Test
  void batchReturnsTheSixContractedFieldsAndCanIncludeInactiveProducts() throws Exception {
    when(getByIds.getByIds(List.of(ID, MISSING_ID))).thenReturn(List.of(product(false, 2)));

    mvc.perform(get(PATH + "/batch").param("ids", ID + "," + MISSING_ID))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    [{"id":"%s","code":"PAR-M6","description":"Parafuso M6","unitPrice":1.20,"balance":5,"active":false}]
                    """
                        .formatted(ID),
                    JsonCompareMode.STRICT));
  }

  @Test
  void batchRejectsInvalidIdsAndBatchesLargerThanOneHundred() throws Exception {
    mvc.perform(get(PATH + "/batch").param("ids", "bad-id")).andExpect(status().isBadRequest());
    mvc.perform(get(PATH + "/batch")).andExpect(status().isBadRequest());
    mvc.perform(
            get(PATH + "/batch")
                .param("ids", String.join(",", Collections.nCopies(101, ID.toString()))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("ids"));
    verifyNoInteractions(getByIds);
  }

  @Test
  void updatesAndReturnsTheVersionFromTheService() throws Exception {
    var command = new UpdateProductUseCase.Command(ID, null, "Parafuso M6", PRICE, 0L, "anonymous");
    when(update.update(command)).thenReturn(product(true, 1));

    mvc.perform(
            put(PATH + "/{id}", ID).contentType(MediaType.APPLICATION_JSON).content(UPDATE_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void reportsImmutableCodeAndVersionConflicts() throws Exception {
    when(update.update(any()))
        .thenThrow(new ImmutableProductFieldException("code", "O código do produto é imutável."))
        .thenThrow(
            new ConflictException(
                ProblemTypes.OPTIMISTIC_LOCK_CONFLICT, "Conflito", "Recarregue."));
    String codeChange =
        """
        {"code":"OUTRO","description":"Parafuso M6","unitPrice":1.20,"version":0}
        """;

    mvc.perform(put(PATH + "/{id}", ID).contentType(MediaType.APPLICATION_JSON).content(codeChange))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:field-immutable"));
    mvc.perform(
            put(PATH + "/{id}", ID).contentType(MediaType.APPLICATION_JSON).content(UPDATE_JSON))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:problem-type:optimistic-lock-conflict"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "null", "{}", "\"5\""})
  void rejectsAnyExplicitBalanceOnUpdate(String balance) throws Exception {
    String request =
        """
        {"description":"Parafuso M6","unitPrice":1.20,"version":0,"balance":%s}
        """
            .formatted(balance);
    mvc.perform(put(PATH + "/{id}", ID).contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:field-immutable"))
        .andExpect(jsonPath("$.errors[0].field").value("balance"));
    verifyNoInteractions(update);
  }

  @Test
  void rejectsAnUpdateWithoutVersion() throws Exception {
    mvc.perform(
            put(PATH + "/{id}", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"description":"Parafuso M6","unitPrice":1.20}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("version"));
    verifyNoInteractions(update);
  }

  @Test
  void returnsNotFoundWhenUpdatingAnUnknownProduct() throws Exception {
    when(update.update(any())).thenThrow(new ProductNotFoundException(MISSING_ID));

    mvc.perform(
            put(PATH + "/{id}", MISSING_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_JSON))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:problem-type:product-not-found"));
  }

  @Test
  void returnsNoContentOnDeactivation() throws Exception {
    mvc.perform(delete(PATH + "/{id}", ID))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    verify(deactivate).deactivate(ID, "anonymous");
  }

  @Test
  void reactivatesWithoutAnIdempotencyKey() throws Exception {
    when(reactivate.reactivate(ID, "anonymous")).thenReturn(product(true, 2));

    mvc.perform(post(PATH + "/{id}/reactivate", ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.version").value(2));
  }

  @Test
  void reportsUnknownProductsOnDeactivationAndReactivation() throws Exception {
    doThrow(new ProductNotFoundException(MISSING_ID))
        .when(deactivate)
        .deactivate(MISSING_ID, "anonymous");
    when(reactivate.reactivate(MISSING_ID, "anonymous"))
        .thenThrow(new ProductNotFoundException(MISSING_ID));

    mvc.perform(delete(PATH + "/{id}", MISSING_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:problem-type:product-not-found"));
    mvc.perform(post(PATH + "/{id}/reactivate", MISSING_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:problem-type:product-not-found"));
  }

  private static Product product(boolean active, long version) {
    return new Product(
        ID,
        CODE,
        "Parafuso M6",
        PRICE,
        BALANCE,
        active,
        version,
        "anonymous",
        Instant.parse("2026-11-19T16:00:00Z"),
        null,
        null);
  }
}
