package grafioschtrader.tools;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import grafioschtrader.service.YamlConfigurationValidation;
import grafioschtrader.service.YamlConfigurationValidation.Format;

/** Opt-in JDBC-only audit: no Spring bootstrap, migrations, background jobs or writes. */
public final class YamlConfigurationAudit {
  private record Source(String table, String id, String field, Format format) {
  }

  private static final List<Source> SOURCES = List.of(
      new Source("trading_platform_plan", "id_trading_platform_plan", "fee_model_yaml", Format.FEES),
      new Source("securityaccount", "id_securitycash_account", "fee_model_yaml", Format.FEES_ACCOUNT),
      new Source("generic_connector_def", "id_generic_connector", "token_config_yaml", Format.TOKENS),
      new Source("trading_calendar_rule_set", "id_trading_calendar_rule_set", "rule_yaml", Format.CALENDAR),
      new Source("tax_country", "id_tax_country", "tax_model_yaml", Format.TAXES));

  private YamlConfigurationAudit() {
  }

  /** Requires explicit URL and user arguments; the password is read only from GT_YAML_AUDIT_PASSWORD. */
  public static void main(String[] args) {
    if (args.length != 2 || !args[0].startsWith("jdbc:mariadb:")) {
      System.err
          .println("Usage: YamlConfigurationAudit jdbc:mariadb://host/database user (GT_YAML_AUDIT_PASSWORD required)");
      System.exit(2);
    }
    String password = System.getenv("GT_YAML_AUDIT_PASSWORD");
    if (password == null) {
      System.err.println("GT_YAML_AUDIT_PASSWORD is required");
      System.exit(2);
    }
    int status;
    try (Connection connection = DriverManager.getConnection(args[0], args[1], password)) {
      status = audit(connection, System.out) == 0 ? 0 : 1;
    } catch (Exception e) {
      // JDBC errors can echo connection strings. Keep credentials and record content out of console output.
      System.err.println("YAML audit could not complete (" + e.getClass().getSimpleName() + ")");
      status = 2;
    }
    System.exit(status);
  }

  /** Executes fixed SELECT statements inside a read-only transaction, rolling back even after a failed scan. */
  public static int audit(Connection connection, PrintStream out) throws SQLException {
    connection.setReadOnly(true);
    connection.setAutoCommit(false);
    int invalid = 0;
    try {
      for (Source source : SOURCES) {
        try (var statement = connection.createStatement();
            var rows = statement.executeQuery("SELECT " + source.id + ", " + source.field + " FROM " + source.table
                + " WHERE " + source.field + " IS NOT NULL")) {
          while (rows.next()) {
            var errors = YamlConfigurationValidation.validate(source.format, rows.getString(2), true);
            if (!errors.isEmpty()) {
              invalid++;
              // Messages can quote expression literals. Report only category and source location in audit output.
              for (var error : errors)
                out.println(source.table + " id=" + rows.getInt(1) + " field=" + source.field + " " + error.category()
                    + (error.line() == null ? "" : " line=" + error.line() + " column=" + error.column()));
            }
          }
        }
      }
      // Only complex strategies are YAML-authored; their stored representation is JSON.
      try (var statement = connection.createStatement();
          var rows = statement.executeQuery(
              "SELECT id_algo_rule_strategy, strategy_config, activatable FROM algo_strategy WHERE algo_strategy_impl = 68")) {
        while (rows.next()) {
          if (!YamlConfigurationValidation.validate(Format.STRATEGY, rows.getString(2), rows.getBoolean(3)).isEmpty()) {
            invalid++;
            out.println("algo_strategy id=" + rows.getInt(1) + " field=strategy_config DOMAIN");
          }
        }
      }
      out.println("Invalid configurations: " + invalid);
      return invalid;
    } finally {
      connection.rollback();
    }
  }
}
