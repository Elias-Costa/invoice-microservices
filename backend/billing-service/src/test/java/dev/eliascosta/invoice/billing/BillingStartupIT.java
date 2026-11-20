package dev.eliascosta.invoice.billing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eliascosta.invoice.common.idempotency.IdempotencyKeyEntity;
import dev.eliascosta.invoice.common.messaging.ProcessedEventEntity;
import dev.eliascosta.invoice.common.outbox.OutboxEventEntity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.hibernate.cfg.SchemaToolingSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class BillingStartupIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("billing_db");

  @Container @ServiceConnection
  static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:4.2.1");

  @Autowired private Flyway flyway;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private KafkaAdmin kafkaAdmin;
  @LocalServerPort private int port;

  @Test
  void appliesFlywayMigrationsAndValidatesTheSharedJpaMappings() {
    assertThat(flyway.info().applied())
        .extracting(MigrationInfo::getScript)
        .containsExactly(
            "V1__extensions.sql",
            "V2__create_customers.sql",
            "V3__create_invoices.sql",
            "V4__create_invoice_items.sql",
            "V5__create_invoice_status_history.sql",
            "V6__create_technical_tables.sql",
            "R__seed_demo.sql");
    assertThat(flyway.info().pending()).isEmpty();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Long.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices", Long.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice_items", Long.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice_status_history", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT extname FROM pg_extension WHERE extname = 'pg_trgm'", String.class))
        .isEqualTo("pg_trgm");
    assertThat(
            jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class))
        .contains("customers", "invoices", "invoice_items", "invoice_status_history");
    assertThat(
            jdbc.queryForObject(
                """
                SELECT start_value FROM pg_sequences
                WHERE schemaname = 'public' AND sequencename = 'invoice_number_seq'
                """,
                Long.class))
        .isEqualTo(1L);
    assertThat(entityManagerFactory.getProperties())
        .containsEntry(SchemaToolingSettings.HBM2DDL_AUTO, "validate");
    assertThat(entityManagerFactory.getMetamodel().getEntities())
        .<Class<?>>extracting(EntityType::getJavaType)
        .contains(OutboxEventEntity.class, ProcessedEventEntity.class, IdempotencyKeyEntity.class);
  }

  @Test
  void exposesHealthAndInfoWithoutCredentials() {
    RestClient client = RestClient.create("http://127.0.0.1:" + port);
    var health = client.get().uri("/actuator/health").retrieve().toEntity(JsonNode.class);
    assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(health.getBody()).isNotNull();
    assertThat(health.getBody().path("status").asString()).isEqualTo("UP");
    assertThat(client.get().uri("/actuator/info").retrieve().toBodilessEntity().getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void createsDebitTopicsAndTheirDeadLetterTopics() {
    var topics =
        kafkaAdmin.describeTopics(
            "stock.debit-requested",
            "stock.debit-confirmed",
            "stock.debit-rejected",
            "stock.debit-requested.DLT",
            "stock.debit-confirmed.DLT",
            "stock.debit-rejected.DLT");
    assertThat(topics)
        .hasSize(6)
        .allSatisfy(
            (name, topic) -> {
              assertThat(topic.partitions()).hasSize(name.endsWith(".DLT") ? 1 : 3);
            });
  }
}
