import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Properties;

import org.flywaydb.core.Flyway;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Opt-in acceptance check against an empty, disposable MariaDB server. Uses the application's Hikari, JDBC and Flyway
 * dependencies, without starting Spring or background application tasks.
 *
 * Run with: java -cp <application runtime classpath> DatabaseInitializationCheck.java <repo> <JDBC URL> <version> The
 * URL must address the server without a database. GT_INSTALL_TEST_DB_PASSWORD supplies its root password. Existing
 * application schemas are refused; this check never drops a database.
 */
class DatabaseInitializationCheck {
  public static void main(String[] args) throws Exception {
    if (args.length != 3 || !args[1].matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/")) {
      throw new IllegalArgumentException("Expected repository path, loopback JDBC server URL and expected version");
    }
    String password = System.getenv("GT_INSTALL_TEST_DB_PASSWORD");
    if (password == null || password.isEmpty()) {
      throw new IllegalArgumentException("Set GT_INSTALL_TEST_DB_PASSWORD for the disposable server");
    }
    Path repo = Path.of(args[0]).toAbsolutePath();
    try (Connection connection = DriverManager.getConnection(args[1], "root", password);
        var statement = connection.createStatement()) {
      String version = connection.getMetaData().getDatabaseProductVersion();
      if (!version.startsWith(args[2] + ".")) {
        throw new IllegalStateException("Unexpected MariaDB version: " + version);
      }
      try (var rows = statement.executeQuery("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA"
          + " WHERE SCHEMA_NAME IN ('grafioschtrader', 'grafiosch')")) {
        if (rows.next()) {
          throw new IllegalStateException("Application schema already exists; use a fresh disposable server");
        }
      }
      for (String schema : new String[] { "grafioschtrader", "grafiosch" }) {
        statement.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        // Historical migration routines use these fixed DEFINER accounts.
        statement.execute("CREATE USER IF NOT EXISTS '" + schema + "'@'localhost'");
        statement.execute("GRANT ALL ON " + schema + ".* TO '" + schema + "'@'localhost'");
      }
    }
    verify(repo, args[1], password, "grafioschtrader", "grafioschtrader-server",
        "backend/grafioschtrader-server/src/main/resources/db/migration");
    verify(repo, args[1], password, "grafiosch", "grafiosch-test-integration",
        "backend/grafiosch-test-integration/migration-baseline");
  }

  private static void verify(Path repo, String url, String password, String schema, String module, String migrations)
      throws Exception {
    Properties properties = new Properties();
    try (var reader = Files
        .newBufferedReader(repo.resolve("backend/" + module + "/src/main/resources/application.properties"))) {
      properties.load(reader);
    }
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(url + schema);
    config.setUsername("root");
    config.setPassword(password);
    config.setConnectionInitSql(properties.getProperty("spring.datasource.hikari.connection-init-sql"));
    config.setMaximumPoolSize(3);
    config.setMinimumIdle(0);
    try (HikariDataSource pool = new HikariDataSource(config)) {
      // Hold three connections at once to test initialization on distinct physical pool connections.
      var connections = new ArrayList<Connection>();
      try {
        for (int i = 0; i < 3; i++) {
          Connection connection = pool.getConnection();
          connections.add(connection);
          verifySession(connection);
        }
      } finally {
        for (Connection connection : connections) {
          connection.close();
        }
      }
      // Exercise the entire versioned history even when a newer B-prefixed snapshot is present locally.
      Path migrationCopy = Files.createTempDirectory("gt-installer-migrations-");
      try {
        try (var files = Files.list(repo.resolve(migrations))) {
          for (Path file : files.filter(p -> p.getFileName().toString().matches("V.*\\.sql")).toList()) {
            Files.copy(file, migrationCopy.resolve(file.getFileName()));
          }
        }
        var result = Flyway.configure().dataSource(pool).locations("filesystem:" + migrationCopy)
            .failOnMissingLocations(true).load().migrate();
        if (!result.success || result.migrationsExecuted == 0) {
          throw new IllegalStateException("No successful migrations for " + schema);
        }
        try (Connection connection = pool.getConnection(); var statement = connection.createStatement()) {
          verifySession(connection);
          try (var rows = statement.executeQuery("SELECT TABLE_NAME, COLUMN_NAME, COLLATION_NAME"
              + " FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
              + " AND COLLATION_NAME LIKE '%uca1400%'")) {
            if (rows.next()) {
              throw new IllegalStateException(
                  "Unexpected collation: " + rows.getString(1) + "." + rows.getString(2) + " = " + rows.getString(3));
            }
          }
        }
        System.out.println("PASS " + schema + ": pool initialization, " + result.migrationsExecuted
            + " migrations, no uca1400 columns");
      } finally {
        try (var files = Files.list(migrationCopy)) {
          for (Path file : files.toList()) {
            Files.delete(file);
          }
        }
        Files.delete(migrationCopy);
      }
    }
  }

  private static void verifySession(Connection connection) throws Exception {
    try (var statement = connection.createStatement()) {
      try (var rows = statement.executeQuery("SELECT @@session.time_zone")) {
        if (!rows.next() || !"+00:00".equals(rows.getString(1))) {
          throw new IllegalStateException("Pool connection did not initialize UTC");
        }
      }
      // MariaDB before 11.2 has no character_set_collations variable.
      try (var rows = statement.executeQuery("SHOW SESSION VARIABLES LIKE 'character_set_collations'")) {
        if (rows.next()) {
          String mapping = rows.getString(2);
          if (!mapping.contains("utf8mb3=utf8mb3_general_ci") || !mapping.contains("utf8mb4=utf8mb4_general_ci")) {
            throw new IllegalStateException("Incorrect collation mapping: " + mapping);
          }
        }
      }
    }
  }
}
