package dev.eliascosta.invoice.billing.domain.model;

import static dev.eliascosta.invoice.billing.domain.model.DomainValidation.invalid;
import static dev.eliascosta.invoice.billing.domain.model.DomainValidation.requiredText;

import dev.eliascosta.invoice.billing.domain.exception.CustomerInactiveException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Customer {

  private static final Pattern PHONE = Pattern.compile("[0-9]{1,20}");

  private final UUID id;
  private final Document document;
  private final long version;
  private final String createdBy;
  private final Instant createdAt;
  private String name;
  private Email email;
  private String phone;
  private boolean active;
  private String updatedBy;
  private Instant updatedAt;

  public Customer(
      UUID id,
      String name,
      Document document,
      Email email,
      String phone,
      boolean active,
      long version,
      String createdBy,
      Instant createdAt,
      String updatedBy,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.name = requiredText(name, "name", 150);
    if (document == null) {
      throw invalid("document", "Informe um CPF ou CNPJ.");
    }
    this.document = document;
    this.email = email;
    this.phone = validatedPhone(phone);
    this.active = active;
    this.version = version;
    this.createdBy = requiredText(createdBy, "createdBy", 100);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedBy = updatedBy == null ? null : requiredText(updatedBy, "updatedBy", 100);
    this.updatedAt = updatedAt;
  }

  public static Customer create(
      String name,
      Document document,
      Email email,
      String phone,
      String createdBy,
      Instant createdAt) {
    return new Customer(
        UUID.randomUUID(), name, document, email, phone, true, 0, createdBy, createdAt, null, null);
  }

  public void edit(String name, Email email, String phone, String updatedBy, Instant updatedAt) {
    String validatedName = requiredText(name, "name", 150);
    String validatedPhone = validatedPhone(phone);
    markUpdated(updatedBy, updatedAt);
    this.name = validatedName;
    this.email = email;
    this.phone = validatedPhone;
  }

  public void deactivate(String updatedBy, Instant updatedAt) {
    markUpdated(updatedBy, updatedAt);
    active = false;
  }

  public void reactivate(String updatedBy, Instant updatedAt) {
    markUpdated(updatedBy, updatedAt);
    active = true;
  }

  public void ensureActive() {
    if (!active) {
      throw new CustomerInactiveException(id);
    }
  }

  private static String validatedPhone(String phone) {
    if (phone != null && !PHONE.matcher(phone).matches()) {
      throw invalid("phone", "Informe um telefone com até 20 dígitos, incluindo DDI e DDD.");
    }
    return phone;
  }

  private void markUpdated(String updatedBy, Instant updatedAt) {
    Objects.requireNonNull(updatedAt, "updatedAt");
    this.updatedBy = requiredText(updatedBy, "updatedBy", 100);
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Document getDocument() {
    return document;
  }

  public Email getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public boolean isActive() {
    return active;
  }

  public long getVersion() {
    return version;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
