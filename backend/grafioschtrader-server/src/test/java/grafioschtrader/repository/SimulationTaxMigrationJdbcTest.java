package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import grafioschtrader.MigrationSections;
import grafioschtrader.service.TaxEvalExEstimator;

/** Applies the merged tax section twice to private tables, preserving models and immutable run inputs. */
@EnabledIfSystemProperty(named = "gt.simulation.jdbc.test", matches = "true")
class SimulationTaxMigrationJdbcTest {
  private static final List<String> SEEDED_COUNTRIES = List.of("US", "CN", "DE", "JP", "IN", "GB", "FR", "IT", "CA",
      "BR", "CH", "AT", "PL");
  private static final String CUSTOM_CH_MODEL = """
      version: 1
      transactionTaxes:
        rules:
          - name: custom
            expression: "0"
      """;
  private static final String BOND_METADATA = "{\"bondTerms\":{\"couponRate\":1.2,\"couponDayCount\":\"ACT_ACT_ICMA\"}}";

  @Test
  void mergedMigrationIsIdempotentAndKeepsConfiguredData() throws Exception {
    String changes = MigrationSections.read(
        "src/main/resources/db/migration/V0_37_0__algo_alert_state_schedule_delivery_simulation_opening_dashboard_and_tenant_fx_convention.sql",
        "simulation-tax");
    String prefix = "simtax_" + UUID.randomUUID().toString().replace("-", "") + "_";
    List<String> tables = List.of("security", "securityaccount", "trading_platform_plan", "tax_country",
        "algo_simulation_result");
    for (String table : tables)
      changes = changes.replaceAll("\\b" + table + "\\b", prefix + table);
    changes = changes.replaceAll("(?m)^--.*$", "");
    try (
        var connection = DriverManager.getConnection("jdbc:mariadb://localhost:3306/grafioschtrader_t",
            "grafioschtrader_t", "grafioschtrader_t");
        var sql = connection.createStatement()) {
      assertThat(connection.getCatalog()).isEqualTo("grafioschtrader_t");
      try {
        for (String table : tables) {
          if ("tax_country".equals(table)) {
            sql.execute("CREATE TABLE " + prefix + table
                + " (id_tax_country INT AUTO_INCREMENT PRIMARY KEY, country_code CHAR(2) NOT NULL UNIQUE)");
            sql.execute("INSERT INTO " + prefix + table + " (country_code) VALUES ('CH')");
          } else if ("security".equals(table)) {
            sql.execute("CREATE TABLE " + prefix + table + " (id INT PRIMARY KEY, isin VARCHAR(12))");
            sql.execute("INSERT INTO " + prefix + table + " VALUES (1, 'CH0012032048')");
          } else {
            sql.execute("CREATE TABLE " + prefix + table + " (id INT PRIMARY KEY)");
            sql.execute("INSERT INTO " + prefix + table + " VALUES (1)");
          }
        }
        for (int pass = 0; pass < 2; pass++) {
          for (String statement : changes.split(";"))
            if (!statement.isBlank())
              sql.execute(statement);
          if (pass == 0) {
            try (var row = sql.executeQuery(
                "SELECT apply_tax_models, generate_bond_coupons FROM " + prefix + "algo_simulation_result")) {
              assertThat(row.next()).isTrue();
              assertThat(row.getBoolean(1)).isFalse();
              assertThat(row.getBoolean(2)).isFalse();
            }
            assertSeededModels(sql, prefix);
            try (var row = sql
                .executeQuery("SELECT issuer_country, simulation_metadata FROM " + prefix + "security WHERE id=1")) {
              assertThat(row.next()).isTrue();
              assertThat(row.getString(1)).isEqualTo("CH");
              assertThat(row.getString(2)).isNull();
            }
            try (var update = connection
                .prepareStatement("UPDATE " + prefix + "security SET simulation_metadata=? WHERE id=1")) {
              update.setString(1, BOND_METADATA);
              update.executeUpdate();
            }
            try (var update = connection
                .prepareStatement("UPDATE " + prefix + "tax_country SET tax_model_yaml=? WHERE country_code='CH'")) {
              update.setString(1, CUSTOM_CH_MODEL);
              update.executeUpdate();
            }
            sql.execute("UPDATE " + prefix + "tax_country SET tax_model_yaml=NULL WHERE country_code='CA'");
            sql.execute("UPDATE " + prefix + "tax_country SET tax_model_yaml='' WHERE country_code='DE'");
            sql.execute("UPDATE " + prefix
                + "algo_simulation_result SET input_assumptions_json='{\"version\":1}', apply_tax_models=TRUE");
          }
        }
        assertSeededModels(sql, prefix);
        try (var row = sql
            .executeQuery("SELECT issuer_country, simulation_metadata FROM " + prefix + "security WHERE id=1")) {
          assertThat(row.next()).isTrue();
          assertThat(row.getString(1)).isEqualTo("CH");
          assertThat(row.getString(2)).isEqualTo(BOND_METADATA);
        }
        try (var row = sql
            .executeQuery("SELECT tax_model_yaml FROM " + prefix + "tax_country WHERE country_code='CH'")) {
          assertThat(row.next()).isTrue();
          assertThat(row.getString(1)).isEqualTo(CUSTOM_CH_MODEL);
        }
        try (var row = sql.executeQuery(
            "SELECT input_assumptions_json, apply_tax_models FROM " + prefix + "algo_simulation_result")) {
          assertThat(row.next()).isTrue();
          assertThat(row.getString(1)).isEqualTo("{\"version\":1}");
          assertThat(row.getBoolean(2)).isTrue();
        }
      } finally {
        for (String table : tables)
          sql.execute("DROP TABLE IF EXISTS " + prefix + table);
      }
    }
  }

  private static void assertSeededModels(java.sql.Statement sql, String prefix) throws Exception {
    var estimator = new TaxEvalExEstimator();
    List<String> countries = new ArrayList<>();
    try (var rows = sql
        .executeQuery("SELECT country_code, tax_model_yaml FROM " + prefix + "tax_country ORDER BY country_code")) {
      while (rows.next()) {
        countries.add(rows.getString(1));
        estimator.parse(rows.getString(2));
      }
    }
    assertThat(countries).containsExactlyInAnyOrderElementsOf(SEEDED_COUNTRIES);
  }
}
