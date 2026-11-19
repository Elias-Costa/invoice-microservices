package dev.eliascosta.invoice.billing.adapter.out.persistence;

import dev.eliascosta.invoice.billing.domain.model.Document;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class CustomerJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(nullable = false, length = 14, updatable = false)
  private String document;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false, length = 4, updatable = false)
  private Document.Type documentType;

  @Column(length = 150)
  private String email;

  @Column(length = 20)
  private String phone;

  @Column(nullable = false)
  private boolean active;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_by", nullable = false, length = 100, updatable = false)
  private String createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_by", length = 100)
  private String updatedBy;

  @Column(name = "updated_at")
  private Instant updatedAt;

  protected CustomerJpaEntity() {}

  public CustomerJpaEntity(
      UUID id, String document, Document.Type documentType, String createdBy, Instant createdAt) {
    this.id = id;
    this.document = document;
    this.documentType = documentType;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDocument() {
    return document;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
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

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
