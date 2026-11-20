package dev.eliascosta.invoice.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class BillingDemoSeedIT {

  @Container
  final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("billing_db");

  private DataSource dataSource;
  private JdbcTemplate jdbc;

  @BeforeEach
  void connect() {
    dataSource =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(dataSource);
  }

  @Test
  void reappliesTheSeedWithoutRewindingTheSequenceOrRestoringReplacedItems() {
    assertThat(migrate(false)).isEqualTo(7);
    assertThat(migrate(true)).isEqualTo(1);
    assertThat(jdbc.queryForList("SELECT number FROM invoices ORDER BY number", Long.class))
        .containsExactly(1L, 2L, 3L, 4L, 5L);
    assertThat(insertInvoice()).isEqualTo(6L);

    // Replacing an item gives it a new id while retaining its position in the invoice.
    jdbc.update(
        "UPDATE invoice_items SET id = ? WHERE id = ?",
        UUID.randomUUID(),
        UUID.fromString("10000000-0000-4000-8000-000000000005"));

    assertThat(migrate(false)).isEqualTo(1);
    assertThat(migrate(true)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Long.class)).isEqualTo(5L);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice_items", Long.class)).isEqualTo(9L);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice_status_history", Long.class))
        .isEqualTo(8L);
    assertThat(insertInvoice()).isEqualTo(7L);
  }

  @Test
  void reportsAReservedNumberConflictWithoutChangingExistingData() {
    migrate(false);
    jdbc.update(
        """
        INSERT INTO customers (id, name, document, document_type, created_by)
        VALUES (?, 'Existing customer', '11144477735', 'CPF', 'operator')
        """,
        UUID.randomUUID());
    assertThat(insertInvoice()).isEqualTo(1L);

    assertThatThrownBy(() -> migrate(true))
        .isInstanceOf(FlywayException.class)
        .hasMessageContaining("Demo invoice number 1 is already in use");
    assertThat(jdbc.queryForList("SELECT number FROM invoices", Long.class)).containsExactly(1L);
    assertThat(jdbc.queryForList("SELECT name FROM customers", String.class))
        .containsExactly("Existing customer");
    assertThat(insertInvoice()).isEqualTo(2L);
  }

  private int migrate(boolean seed) {
    return Flyway.configure()
        .dataSource(dataSource)
        .placeholders(Map.of("seed", Boolean.toString(seed)))
        .load()
        .migrate()
        .migrationsExecuted;
  }

  private long insertInvoice() {
    return jdbc.queryForObject(
        """
        INSERT INTO invoices (id, status, customer_id, customer_name, customer_document, created_by)
        SELECT ?, 'OPEN', id, name, document, 'operator' FROM customers ORDER BY id LIMIT 1
        RETURNING number
        """,
        Long.class,
        UUID.randomUUID());
  }
}
