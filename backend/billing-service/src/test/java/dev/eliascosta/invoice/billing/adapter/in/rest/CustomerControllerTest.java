package dev.eliascosta.invoice.billing.adapter.in.rest;

import static dev.eliascosta.invoice.billing.adapter.in.rest.CustomerController.PATH;
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

import dev.eliascosta.invoice.billing.domain.exception.CustomerDocumentAlreadyExistsException;
import dev.eliascosta.invoice.billing.domain.exception.CustomerNotFoundException;
import dev.eliascosta.invoice.billing.domain.model.Customer;
import dev.eliascosta.invoice.billing.domain.model.Document;
import dev.eliascosta.invoice.billing.domain.model.Email;
import dev.eliascosta.invoice.billing.domain.port.PageResult;
import dev.eliascosta.invoice.billing.domain.port.in.CustomerUseCases;
import dev.eliascosta.invoice.billing.domain.port.out.CustomerRepository;
import dev.eliascosta.invoice.common.error.ConflictException;
import dev.eliascosta.invoice.common.error.ProblemTypes;
import dev.eliascosta.invoice.common.error.web.ErrorHandlingAutoConfiguration;
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

@WebMvcTest(CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomerRestMapperImpl.class)
@ImportAutoConfiguration(ErrorHandlingAutoConfiguration.class)
class CustomerControllerTest {

  private static final UUID ID = UUID.fromString("6f14dc62-893e-437c-9c5c-80204134c409");
  private static final Document DOCUMENT = new Document("52998224725");
  private static final Email EMAIL = new Email("cliente@example.com");
  private static final String USER = "anonymous";
  private static final String CREATE_JSON =
      """
      {"name":"Cliente","document":"52998224725","email":"cliente@example.com","phone":"5585999990000"}
      """;

  @Autowired private MockMvc mvc;
  @MockitoBean private CustomerUseCases customers;

  @Test
  void createsCustomerWithLocationAndContactFields() throws Exception {
    when(customers.create(createCommand())).thenReturn(customer(true, 0));

    mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", PATH + "/" + ID))
        .andExpect(jsonPath("$.id").value(ID.toString()))
        .andExpect(jsonPath("$.name").value("Cliente"))
        .andExpect(jsonPath("$.document").value(DOCUMENT.value()))
        .andExpect(jsonPath("$.documentType").value("CPF"))
        .andExpect(jsonPath("$.email").value(EMAIL.value()))
        .andExpect(jsonPath("$.phone").value("5585999990000"))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void rejectsBlankNameBeforeCallingTheUseCase() throws Exception {
    mvc.perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":" ","document":"52998224725"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
    verifyNoInteractions(customers);
  }

  @Test
  void mapsDuplicateDocumentToConflict() throws Exception {
    when(customers.create(createCommand())).thenThrow(new CustomerDocumentAlreadyExistsException());

    mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:problem-type:customer-document-already-exists"));
  }

  @Test
  void listsCustomersWithDefaultFiltersAndPageMetadata() throws Exception {
    var query = new CustomerRepository.Search(null, true, 0, 20, "name", true);
    var row =
        new CustomerRepository.Summary(
            ID,
            "Cliente",
            DOCUMENT.value(),
            Document.Type.CPF,
            EMAIL.value(),
            "5585999990000",
            true,
            0);
    when(customers.list(query)).thenReturn(new PageResult<>(List.of(row), 0, 20, 1));

    mvc.perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(ID.toString()))
        .andExpect(jsonPath("$.content[0].documentType").value("CPF"))
        .andExpect(jsonPath("$.page.number").value(0))
        .andExpect(jsonPath("$.page.size").value(20))
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.page.totalPages").value(1));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "sort|version,asc|sort",
        "sort|name,down|sort",
        "sort|''|sort",
        "page|-1|page",
        "size|0|size"
      })
  void rejectsInvalidListParameters(String parameter, String value, String field) throws Exception {
    mvc.perform(get(PATH).param(parameter, value))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"))
        .andExpect(jsonPath("$.errors[0].field").value(field));
    verifyNoInteractions(customers);
  }

  @Test
  void returnsDetailWithCustomerFieldsAtTheTopLevel() throws Exception {
    when(customers.get(ID)).thenReturn(new CustomerUseCases.Detail(customer(true, 0), List.of()));

    mvc.perform(get(PATH + "/{id}", ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ID.toString()))
        .andExpect(jsonPath("$.document").value(DOCUMENT.value()))
        .andExpect(jsonPath("$.recentInvoices").isArray())
        .andExpect(jsonPath("$.recentInvoices").isEmpty());
  }

  @Test
  void returnsNotFoundForMissingCustomer() throws Exception {
    when(customers.get(ID)).thenThrow(new CustomerNotFoundException(ID));

    mvc.perform(get(PATH + "/{id}", ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:problem-type:customer-not-found"));
  }

  @Test
  void returnsUpdatedCustomerAndMapsVersionConflict() throws Exception {
    var command =
        new CustomerUseCases.Update(
            ID, "Cliente", DOCUMENT.value(), EMAIL, "5585999990000", 0, USER);
    when(customers.update(command))
        .thenReturn(customer(true, 1))
        .thenThrow(
            new ConflictException(
                ProblemTypes.OPTIMISTIC_LOCK_CONFLICT, "Conflito", "Versão antiga."));
    String request =
        """
        {"name":"Cliente","document":"52998224725","email":"cliente@example.com","phone":"5585999990000","version":0}
        """;

    mvc.perform(put(PATH + "/{id}", ID).contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(put(PATH + "/{id}", ID).contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:problem-type:optimistic-lock-conflict"));
  }

  @Test
  void requiresVersionOnUpdate() throws Exception {
    mvc.perform(
            put(PATH + "/{id}", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Cliente"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("version"));
    verifyNoInteractions(customers);
  }

  @ParameterizedTest
  @ValueSource(strings = {"false", "null"})
  void rejectsActiveEvenWhenExplicitlyNull(String active) throws Exception {
    mvc.perform(
            put(PATH + "/{id}", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Cliente","version":0,"active":%s}
                    """
                        .formatted(active)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:field-immutable"))
        .andExpect(jsonPath("$.errors[0].field").value("active"));
    verifyNoInteractions(customers);
  }

  @Test
  void rejectsExplicitlyNullDocument() throws Exception {
    mvc.perform(
            put(PATH + "/{id}", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Cliente","version":0,"document":null}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:field-immutable"))
        .andExpect(jsonPath("$.errors[0].field").value("document"));
    verifyNoInteractions(customers);
  }

  @Test
  void deactivatesWithoutABodyAndReactivatesWithTheCustomer() throws Exception {
    when(customers.reactivate(ID, USER)).thenReturn(customer(true, 2));

    mvc.perform(delete(PATH + "/{id}", ID))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    verify(customers).deactivate(ID, USER);

    mvc.perform(post(PATH + "/{id}/reactivate", ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ID.toString()))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.version").value(2));
  }

  @Test
  void mapsMissingCustomerOnStateChanges() throws Exception {
    doThrow(new CustomerNotFoundException(ID)).when(customers).deactivate(ID, USER);
    when(customers.reactivate(ID, USER)).thenThrow(new CustomerNotFoundException(ID));

    mvc.perform(delete(PATH + "/{id}", ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:problem-type:customer-not-found"));
    mvc.perform(post(PATH + "/{id}/reactivate", ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:problem-type:customer-not-found"));
  }

  private static CustomerUseCases.Create createCommand() {
    return new CustomerUseCases.Create("Cliente", DOCUMENT, EMAIL, "5585999990000", USER);
  }

  private static Customer customer(boolean active, long version) {
    return new Customer(
        ID,
        "Cliente",
        DOCUMENT,
        EMAIL,
        "5585999990000",
        active,
        version,
        USER,
        Instant.parse("2026-11-19T12:00:00Z"),
        null,
        null);
  }
}
