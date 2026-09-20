package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import grafioschtrader.MigrationSections;

/**
 * Opt-in migration verification against grafioschtrader_t only. Uses uniquely named private tables, never application
 * records, and removes its tables in reverse FK order. No Spring context or bootstrap migration is started.
 */
@EnabledIfSystemProperty(named = "gt.alert.jdbc.test", matches = "true")
class AlgoAlertScheduleJdbcTest {
  @Test
  void migrationIsRepeatableAndClaimsAndDeleteCascadesHaveDatabaseBoundaries() throws Exception {
    String prefix = "alertschedule_" + UUID.randomUUID().toString().replace("-", "") + "_";
    List<String> tables = List.of("tenant", "algo_strategy", "securitycurrency", "globalparameters",
        "algo_alert_evaluation_state");
    try (Connection connection = connect(); Statement sql = connection.createStatement()) {
      assertThat(connection.getCatalog()).isEqualTo("grafioschtrader_t");
      try {
        sql.execute("CREATE TABLE " + prefix + "tenant (id_tenant INT PRIMARY KEY) ENGINE=InnoDB");
        sql.execute("CREATE TABLE " + prefix + "algo_strategy (id_algo_rule_strategy INT PRIMARY KEY) ENGINE=InnoDB");
        sql.execute("CREATE TABLE " + prefix + "securitycurrency (id_securitycurrency INT PRIMARY KEY) ENGINE=InnoDB");
        sql.execute("CREATE TABLE " + prefix + "globalparameters (property_name VARCHAR(100) PRIMARY KEY, "
            + "property_int INT, changed_by_system TINYINT, input_rule VARCHAR(100)) ENGINE=InnoDB");
        // Only the fenced block, never the whole script: the rest of V0_37_0 drops tables and alters
        // algo_message_alert, algo_top and entity_limit, none of which are renamed below.
        String migration = MigrationSections.read(
            "src/main/resources/db/migration/V0_37_0__algo_alert_state_schedule_delivery_simulation_opening_dashboard_and_tenant_fx_convention.sql",
            "alert-evaluation-schedule");
        for (String table : tables)
          migration = migration.replaceAll("\\b" + table + "\\b", prefix + table);
        // MariaDB foreign-key constraint names are schema-wide, so isolate them too.
        migration = migration.replace("FK_AlertEvaluation_", prefix + "fk_").replaceAll("(?m)^--.*$", "");
        for (int repeat = 0; repeat < 2; repeat++)
          for (String command : migration.split(";"))
            if (!command.isBlank())
              sql.execute(command);
        try (
            ResultSet result = sql.executeQuery("SELECT property_int,input_rule FROM " + prefix + "globalparameters")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getInt(1)).isEqualTo(4);
          assertThat(result.getString(2)).isEqualTo("min:2,max:6");
        }
        sql.execute("INSERT INTO " + prefix + "tenant VALUES (1)");
        sql.execute("INSERT INTO " + prefix + "algo_strategy VALUES (2)");
        sql.execute("INSERT INTO " + prefix + "securitycurrency VALUES (3)");
        String table = prefix + "algo_alert_evaluation_state";
        String insert = "INSERT IGNORE INTO " + table
            + " (id_tenant,id_algo_strategy,id_securitycurrency,config_fingerprint) VALUES (1,2,3,'config')";
        assertThat(sql.executeUpdate(insert)).isEqualTo(1);
        assertThat(sql.executeUpdate(insert)).isZero();
        connection.setAutoCommit(false);
        sql.executeQuery("SELECT * FROM " + table + " FOR UPDATE").close();
        try (Connection competitor = connect(); Statement other = competitor.createStatement()) {
          other.execute("SET SESSION innodb_lock_wait_timeout=1");
          assertThatThrownBy(() -> other.executeUpdate("UPDATE " + table + " SET lease_token='other'"))
              .isInstanceOf(SQLException.class);
        }
        sql.executeUpdate(
            "UPDATE " + table + " SET lease_token='owner', lease_until=DATE_ADD(UTC_TIMESTAMP(), INTERVAL 35 MINUTE)");
        connection.commit();
        connection.setAutoCommit(true);
        try (Connection restarted = connect();
            Statement query = restarted.createStatement();
            ResultSet result = query.executeQuery("SELECT lease_token, lease_until > UTC_TIMESTAMP() FROM " + table)) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1)).isEqualTo("owner");
          assertThat(result.getBoolean(2)).isTrue();
        }
        sql.executeUpdate("DELETE FROM " + prefix + "tenant WHERE id_tenant=1");
        try (ResultSet result = sql.executeQuery("SELECT COUNT(*) FROM " + table)) {
          result.next();
          assertThat(result.getInt(1)).isZero();
        }
      } finally {
        if (!connection.getAutoCommit())
          connection.rollback();
        connection.setAutoCommit(true);
        for (String table : tables.reversed())
          sql.execute("DROP TABLE IF EXISTS " + prefix + table);
      }
    }
  }

  private Connection connect() throws SQLException {
    // The dedicated test credentials are the same public defaults as application-test.properties.
    return DriverManager.getConnection("jdbc:mariadb://localhost:3306/grafioschtrader_t", "grafioschtrader_t",
        "grafioschtrader_t");
  }
}
