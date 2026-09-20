package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import grafioschtrader.MigrationSections;

/**
 * Verifies the simulation opening migration additions on private tables in the test database, without changing Flyway
 * history.
 */
@EnabledIfSystemProperty(named = "gt.simulation.jdbc.test", matches = "true")
class SimulationOpeningMigrationJdbcTest {
  @Test
  void openingColumnsCanBeAppliedAgainWithoutChangingExistingDefinitionsOrTransactions() throws Exception {
    String section = "simulation-opening";
    String changes = MigrationSections.read(
        "src/main/resources/db/migration/V0_37_0__algo_alert_state_schedule_delivery_simulation_opening_dashboard_and_tenant_fx_convention.sql",
        section);
    String prefix = "simopening_" + UUID.randomUUID().toString().replace("-", "") + "_";
    changes = changes.replaceAll("\\btenant\\b", prefix + "tenant")
        .replaceAll("\\btransaction\\b", prefix + "transaction").replaceAll("(?m)^--.*$", "");
    try (
        var connection = DriverManager.getConnection("jdbc:mariadb://localhost:3306/grafioschtrader_t",
            "grafioschtrader_t", "grafioschtrader_t");
        var sql = connection.createStatement()) {
      assertThat(connection.getCatalog()).isEqualTo("grafioschtrader_t");
      try {
        sql.execute("CREATE TABLE " + prefix + "tenant (id_tenant INT PRIMARY KEY)");
        sql.execute("CREATE TABLE " + prefix + "transaction (id_transaction INT PRIMARY KEY, amount DOUBLE)");
        sql.execute("INSERT INTO " + prefix + "tenant VALUES (1)");
        sql.execute("INSERT INTO " + prefix + "transaction VALUES (2, 1234.5)");
        for (int repeat = 0; repeat < 2; repeat++) {
          for (String statement : changes.split(";"))
            if (!statement.isBlank())
              sql.execute(statement);
          try (var rows = sql
              .executeQuery("SELECT simulation_start_date, simulation_initialization_mode FROM " + prefix + "tenant")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isNull();
            assertThat(rows.getString(2)).isNull();
          }
          try (var rows = sql.executeQuery("SELECT amount, simulation_opening FROM " + prefix + "transaction")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getDouble(1)).isEqualTo(1234.5);
            assertThat(rows.getBoolean(2)).isFalse();
          }
        }
        sql.execute("UPDATE " + prefix
            + "tenant SET simulation_start_date='2020-06-15', simulation_initialization_mode='COPY_PORTFOLIO'");
        for (String statement : changes.split(";"))
          if (!statement.isBlank())
            sql.execute(statement);
        try (var rows = sql.executeQuery("SELECT simulation_start_date FROM " + prefix + "tenant")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getString(1)).isEqualTo("2020-06-15");
        }
      } finally {
        sql.execute("DROP TABLE IF EXISTS " + prefix + "transaction");
        sql.execute("DROP TABLE IF EXISTS " + prefix + "tenant");
      }
    }
  }
}
