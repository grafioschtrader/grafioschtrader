package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import grafioschtrader.MigrationSections;

/** Exercises the actual migration and report SQL on isolated tables in the test database, without Spring/Flyway. */
@EnabledIfSystemProperty(named = "gt.fundamental.jdbc.test", matches = "true")
class FundamentalSharesJdbcTest {

  private static final List<String> TABLES = List.of("security_fundamental", "task_data_change", "security",
      "securitysplit", "currencypair", "historyquote");

  @Test
  @DisplayName("Existing Yahoo stocks recover, fiscal-year counts stay separate, and the read query rejects obsolete bases")
  void migrationAndReadRules() throws Exception {
    String prefix = "fshares_" + UUID.randomUUID().toString().replace("-", "") + "_";
    String migration = rename(
        MigrationSections.read("src/main/resources/db/migration/V0_40_0__cost_basis_method.sql", "fundamental-shares"),
        prefix).replaceAll("(?m)^--.*$", "");
    Properties queries = new Properties();
    try (var reader = Files.newBufferedReader(Path.of("src/main/resources/META-INF/jpa-named-queries.properties"))) {
      queries.load(reader);
    }
    String query = rename(queries.getProperty("SecurityFundamental.findSelectedByIds"), prefix).replace("?1", "1,2")
        .replace("?2", "'2026-11-13'");
    try (
        var connection = DriverManager.getConnection("jdbc:mariadb://localhost:3306/grafioschtrader_t",
            "grafioschtrader_t", "grafioschtrader_t");
        var sql = connection.createStatement()) {
      assertThat(connection.getCatalog()).isEqualTo("grafioschtrader_t");
      try {
        createTables(sql, prefix);
        String insert = "INSERT INTO " + prefix + "security_fundamental VALUES ";
        sql.execute(insert + "(1,6,1,389200000,NULL,NULL,'2026-10-31 07:38:43',1,4),"
            + "(1,6,3,427000000,NULL,'2025-11-30','2026-10-31 07:37:47',1,4),"
            + "(2,6,1,774878436,NULL,NULL,'2026-10-31 07:37:18',1,4),"
            + "(2,6,3,815975000,NULL,'2025-12-31','2026-10-31 07:36:57',1,4)");
        run(sql, migration);
        run(sql, migration);
        assertThat(count(sql, "SELECT * FROM " + prefix + "security_fundamental WHERE metric=10 AND state=0"))
            .isEqualTo(2);
        assertThat(count(sql, "SELECT * FROM " + prefix + "task_data_change WHERE id_task=56")).isEqualTo(1);
        assertThat(count(sql, query)).isEqualTo(2);

        sql.execute(insert + "(1,6,4,389200000,NULL,'2026-09-18','2026-11-13 06:00:00',0,3)");
        sql.execute("UPDATE " + prefix + "security_fundamental SET state=0 WHERE source=1 AND id_securitycurrency=1");
        run(sql, migration);
        assertThat(count(sql, query)).isEqualTo(2);
        sql.execute("INSERT INTO " + prefix + "securitysplit VALUES (1,'2026-10-01')");
        assertThat(count(sql, query)).isEqualTo(1);
        sql.execute("UPDATE " + prefix + "securitysplit SET split_date='2026-12-01'");
        assertThat(count(sql, query)).isEqualTo(2);
        sql.execute("UPDATE " + prefix + "security_fundamental SET period_end='2026-05-12' WHERE source=4");
        assertThat(count(sql, query)).isEqualTo(1);
      } finally {
        for (String table : TABLES)
          sql.execute("DROP TABLE IF EXISTS " + prefix + table);
      }
    }
  }

  private static void createTables(Statement sql, String prefix) throws Exception {
    sql.execute("CREATE TABLE " + prefix + "security_fundamental LIKE security_fundamental");
    sql.execute("CREATE TABLE " + prefix + "task_data_change LIKE task_data_change");
    sql.execute("CREATE TABLE " + prefix + "security (id_securitycurrency INT PRIMARY KEY, currency CHAR(3))");
    sql.execute("INSERT INTO " + prefix + "security VALUES (1,'USD'),(2,'USD')");
    sql.execute("CREATE TABLE " + prefix + "securitysplit (id_securitycurrency INT, split_date DATE)");
    sql.execute("CREATE TABLE " + prefix
        + "currencypair (id_securitycurrency INT, from_currency CHAR(3), to_currency CHAR(3))");
    sql.execute("CREATE TABLE " + prefix + "historyquote (id_securitycurrency INT, date DATE, close DOUBLE)");
  }

  private static String rename(String sql, String prefix) {
    for (String table : TABLES)
      sql = sql.replaceAll("\\b" + table + "\\b", prefix + table);
    return sql;
  }

  private static void run(Statement sql, String migration) throws Exception {
    for (String statement : migration.split(";"))
      if (!statement.isBlank())
        sql.execute(statement);
  }

  private static int count(Statement sql, String query) throws Exception {
    try (var rows = sql.executeQuery(query)) {
      int count = 0;
      while (rows.next())
        count++;
      return count;
    }
  }
}
