package dev.eliascosta.invoice.common.error.web;

import static dev.eliascosta.invoice.common.error.ProblemTypes.DATA_INTEGRITY_VIOLATION;
import static dev.eliascosta.invoice.common.error.ProblemTypes.INTERNAL_ERROR;
import static dev.eliascosta.invoice.common.error.ProblemTypes.OPTIMISTIC_LOCK_CONFLICT;
import static dev.eliascosta.invoice.common.error.ProblemTypes.UNIQUE_CONSTRAINT_VIOLATION;
import static dev.eliascosta.invoice.common.error.ProblemTypes.VALIDATION_ERROR;

import dev.eliascosta.invoice.common.error.BusinessRuleException;
import dev.eliascosta.invoice.common.error.ConflictException;
import dev.eliascosta.invoice.common.error.DomainException;
import dev.eliascosta.invoice.common.error.InvalidRequestException;
import dev.eliascosta.invoice.common.error.InvalidStateException;
import dev.eliascosta.invoice.common.error.NotFoundException;
import dev.eliascosta.invoice.common.error.ProblemTypes;
import dev.eliascosta.invoice.common.error.UpstreamUnavailableException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converts every exception that escapes a controller into an RFC 9457 {@link ProblemDetail} ({@code
 * requisitos.md} §8.8): {@code type} is {@code urn:problem-type:<slug>}, {@code title} and {@code
 * detail} are user-facing (Portuguese), and every problem carries {@code instance}, {@code
 * timestamp} and — when resolvable (D-010) — {@code traceId}.
 *
 * <p>Status mapping:
 *
 * <ul>
 *   <li>{@link DomainException} subclasses → status of the generic subclass (D-009).
 *   <li>Bean Validation and malformed input → 400 {@code validation-error} with {@code errors[]}.
 *   <li>Optimistic locking → 409 {@code optimistic-lock-conflict}.
 *   <li>Database integrity (safety net, D-011) → 409 {@code unique-constraint-violation} for
 *       SQLState 23505, otherwise 422 {@code data-integrity-violation}.
 *   <li>Protocol errors already handled by {@link ResponseEntityExceptionHandler} (404 without a
 *       route, 405, 406, 415, ...) keep Spring's default problem ({@code about:blank}), enriched.
 *   <li>Anything else → 500 {@code internal-error}, generic detail, stack trace only in the log
 *       (RNF-SEC-07).
 * </ul>
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);

  static final String TRACE_ID_PROPERTY = "traceId";
  static final String TIMESTAMP_PROPERTY = "timestamp";
  static final String ERRORS_PROPERTY = "errors";

  private static final String TITLE_VALIDATION = "Dados inválidos";
  private static final String DETAIL_VALIDATION = "Um ou mais campos são inválidos.";
  private static final String DEFAULT_VIOLATION_MESSAGE = "valor inválido";
  private static final URI ABOUT_BLANK = URI.create("about:blank");
  private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

  private final TraceIdResolver traceIdResolver;

  public ProblemDetailExceptionHandler(TraceIdResolver traceIdResolver) {
    this.traceIdResolver = Objects.requireNonNull(traceIdResolver, "traceIdResolver");
  }

  // --- Domain (catalogue §8.8) ---------------------------------------------------------------

  @ExceptionHandler(DomainException.class)
  public @Nullable ResponseEntity<Object> handleDomainException(
      DomainException ex, WebRequest request) {
    HttpStatus status = statusOf(ex);
    ProblemDetail problem = problem(status, ex.getType(), ex.getTitle(), ex.getDetail());
    ex.getProperties().forEach(problem::setProperty);
    if (status.is5xxServerError()) {
      log.warn("Upstream unavailable [{}]: {}", ex.getType(), ex.getMessage(), ex);
    } else {
      log.debug("Domain error [{}] -> {}: {}", ex.getType(), status.value(), ex.getMessage());
    }
    return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
  }

  private static HttpStatus statusOf(DomainException ex) {
    return switch (ex) {
      case NotFoundException ignored -> HttpStatus.NOT_FOUND;
      case ConflictException ignored -> HttpStatus.CONFLICT;
      case InvalidStateException ignored -> HttpStatus.CONFLICT;
      case BusinessRuleException ignored -> HttpStatus.UNPROCESSABLE_CONTENT;
      case InvalidRequestException ignored -> HttpStatus.BAD_REQUEST;
      case UpstreamUnavailableException ignored -> HttpStatus.SERVICE_UNAVAILABLE;
    };
  }

  // --- Validation (400 validation-error) -----------------------------------------------------

  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldViolation> errors = new ArrayList<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.add(new FieldViolation(error.getField(), messageOf(error))));
    ex.getBindingResult()
        .getGlobalErrors()
        .forEach(error -> errors.add(new FieldViolation(null, messageOf(error))));
    return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldViolation> errors = new ArrayList<>();
    for (var result : ex.getParameterValidationResults()) {
      if (result instanceof ParameterErrors parameterErrors) {
        parameterErrors
            .getFieldErrors()
            .forEach(error -> errors.add(new FieldViolation(error.getField(), messageOf(error))));
        parameterErrors
            .getGlobalErrors()
            .forEach(error -> errors.add(new FieldViolation(null, messageOf(error))));
      } else {
        String parameter = result.getMethodParameter().getParameterName();
        result
            .getResolvableErrors()
            .forEach(error -> errors.add(new FieldViolation(parameter, messageOf(error))));
      }
    }
    return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public @Nullable ResponseEntity<Object> handleConstraintViolation(
      ConstraintViolationException ex, WebRequest request) {
    List<FieldViolation> errors =
        ex.getConstraintViolations().stream()
            .map(violation -> new FieldViolation(leafOf(violation), violation.getMessage()))
            .toList();
    return handleExceptionInternal(
        ex, validationProblem(errors), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        problem(
            status,
            VALIDATION_ERROR,
            TITLE_VALIDATION,
            "Corpo da requisição ilegível ou malformado.");
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String name =
        ex instanceof MethodArgumentTypeMismatchException mismatch
            ? mismatch.getName()
            : ex.getPropertyName();
    String detail =
        name == null
            ? "Valor de parâmetro inválido."
            : "Parâmetro '" + name + "' com valor inválido.";
    ProblemDetail problem = problem(status, VALIDATION_ERROR, TITLE_VALIDATION, detail);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleMissingServletRequestParameter(
      MissingServletRequestParameterException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        problem(
            status,
            VALIDATION_ERROR,
            TITLE_VALIDATION,
            "Parâmetro obrigatório '" + ex.getParameterName() + "' ausente.");
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  // --- Persistence (409 / 422) ---------------------------------------------------------------

  @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
  public @Nullable ResponseEntity<Object> handleOptimisticLock(Exception ex, WebRequest request) {
    log.debug("Optimistic lock conflict: {}", ex.getMessage());
    ProblemDetail problem =
        problem(
            HttpStatus.CONFLICT,
            OPTIMISTIC_LOCK_CONFLICT,
            "Conflito de edição concorrente",
            "O registro foi alterado por outra operação. Recarregue os dados e tente novamente.");
    return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public @Nullable ResponseEntity<Object> handleDataIntegrityViolation(
      DataIntegrityViolationException ex, WebRequest request) {
    String sqlState = sqlStateOf(ex);
    boolean unique =
        ex instanceof DuplicateKeyException || SQLSTATE_UNIQUE_VIOLATION.equals(sqlState);
    // Reaching here means the application did not pre-check the constraint: worth a warning.
    log.warn("Data integrity violation not handled by the application (sqlState={})", sqlState, ex);
    ProblemDetail problem =
        unique
            ? problem(
                HttpStatus.CONFLICT,
                UNIQUE_CONSTRAINT_VIOLATION,
                "Registro duplicado",
                "Já existe um registro com os mesmos dados.")
            : problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                DATA_INTEGRITY_VIOLATION,
                "Violação de integridade",
                "A operação viola uma restrição de integridade dos dados.");
    return handleExceptionInternal(
        ex, problem, new HttpHeaders(), HttpStatusCode.valueOf(problem.getStatus()), request);
  }

  // --- Fallback (500) ------------------------------------------------------------------------

  @ExceptionHandler(Exception.class)
  public @Nullable ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
    log.error("Unhandled exception", ex);
    ProblemDetail problem =
        problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            INTERNAL_ERROR,
            "Erro interno",
            "Ocorreu um erro inesperado. Informe o traceId ao suporte.");
    return handleExceptionInternal(
        ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
  }

  // --- Enrichment applied to every problem, including the ones Spring builds itself ---------

  @Override
  protected ResponseEntity<Object> createResponseEntity(
      @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    if (body instanceof ProblemDetail problem) {
      if (statusCode.value() == HttpStatus.BAD_REQUEST.value() && isUntyped(problem)) {
        // Any other 400 raised by Spring MVC (missing header, missing part, binding...) is
        // malformed
        // input from the catalogue's point of view.
        problem.setType(ProblemTypes.uri(VALIDATION_ERROR));
        problem.setTitle(TITLE_VALIDATION);
      }
      enrich(problem, request);
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }

  private void enrich(ProblemDetail problem, WebRequest request) {
    if (request instanceof ServletWebRequest servletWebRequest) {
      HttpServletRequest httpRequest = servletWebRequest.getRequest();
      if (problem.getInstance() == null) {
        problem.setInstance(URI.create(httpRequest.getRequestURI()));
      }
      traceIdResolver
          .resolve(httpRequest)
          .ifPresent(traceId -> problem.setProperty(TRACE_ID_PROPERTY, traceId));
    }
    problem.setProperty(TIMESTAMP_PROPERTY, Instant.now());
  }

  private static boolean isUntyped(ProblemDetail problem) {
    return problem.getType() == null || ABOUT_BLANK.equals(problem.getType());
  }

  private static ProblemDetail problem(
      HttpStatusCode status, String slug, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(ProblemTypes.uri(slug));
    problem.setTitle(title);
    return problem;
  }

  private static ProblemDetail validationProblem(List<FieldViolation> errors) {
    ProblemDetail problem =
        problem(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, TITLE_VALIDATION, DETAIL_VALIDATION);
    problem.setProperty(ERRORS_PROPERTY, errors);
    return problem;
  }

  private static String messageOf(MessageSourceResolvable error) {
    String message = error.getDefaultMessage();
    return message == null || message.isBlank() ? DEFAULT_VIOLATION_MESSAGE : message;
  }

  /** Last node of the property path: {@code create.request.quantity} → {@code quantity}. */
  private static @Nullable String leafOf(ConstraintViolation<?> violation) {
    String leaf = null;
    for (var node : violation.getPropertyPath()) {
      leaf = node.getName();
    }
    return leaf;
  }

  private static @Nullable String sqlStateOf(Throwable ex) {
    Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
    return root instanceof SQLException sql ? sql.getSQLState() : null;
  }
}
