package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@DisplayName("FX seed bytes, shipped commission/custody chain and MariaDB idempotence")
class FxFeeMigrationTest {
  private static final String MIGRATION = "V0_37_1__tenant_monitoring_account_origin_algo_assetclass_cleanup_dated_custody_fx_fee_models_and_bankrupt_trading_stop.sql";
  private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
  private static final Pattern APPEND = Pattern.compile(
      "UPDATE trading_platform_plan SET fee_model_yaml = CONCAT\\(fee_model_yaml, CONVERT\\(0x([0-9a-f]+) USING utf8mb4\\)\\) WHERE SHA2\\(fee_model_yaml, 256\\) = '([0-9a-f]+)'[^;]*;");

  private record Append(String sql, String suffix, String hash) {
  }

  private List<Append> appends() throws Exception {
    List<Append> result = new ArrayList<>();
    var matcher = APPEND.matcher(Files.readString(MIGRATIONS.resolve(MIGRATION)));
    while (matcher.find())
      result.add(new Append(matcher.group(),
          new String(HexFormat.of().parseHex(matcher.group(1)), StandardCharsets.UTF_8), matcher.group(2)));
    assertThat(result).hasSize(10);
    return result;
  }

  private Map<String, String> commissions() throws Exception {
    var matcher = Pattern.compile("fee_model_yaml\\s*=\\s*'((?:[^']++|'')*+)'")
        .matcher(Files.readString(MIGRATIONS.resolve("V0_34_0__Standing_Order_and_Fee_Model.sql")));
    Map<String, String> models = new HashMap<>();
    while (matcher.find()) {
      String yaml = matcher.group(1).replace("''", "'").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
          .replace("\\\\", "\\");
      models.put(hash(yaml), yaml);
    }
    return models;
  }

  private static String hash(String value) throws Exception {
    return HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void fxGuardsMatchExactlyTheShippedPostCustodyDocumentsAndMirrorTemplates() throws Exception {
    var models = commissions();
    int fxCount = 0;
    for (var append : appends()) {
      assertThat(models).containsKey(append.hash());
      String result = models.get(append.hash()) + append.suffix();
      models.put(hash(result), result);
      assertThat(new FeeModelYamlValidator().validate(result)).isEmpty();
      if (append.suffix().startsWith("\nfx:")) {
        fxCount++;
        assertThat(append.sql()).contains("NOT LIKE '%\\nfx:%'");
        assertThat(result.split("(?m)^fx:", -1)).hasSize(2);
        assertThat(hash(result)).isNotEqualTo(append.hash());
        var templates = new ArrayList<String>();
        for (String name : List.of("swissquote", "migros", "postfinance", "saxo"))
          templates.add("\n" + FxMarkupEngineTest.template(name));
        assertThat(templates).contains(append.suffix());
      }
    }
    assertThat(fxCount).isEqualTo(4);
  }

  @Test
  @EnabledIfSystemProperty(named = "gt.fx.jdbc.test", matches = "true")
  void realMariaDbUpdatesAreRepeatableAndLeaveCustomNullAndAccountDocumentsUntouched() throws Exception {
    var appends = appends();
    var commissions = commissions();
    // Connection-scoped temporary tables shadow the real tables and disappear on close, even after failure.
    try (
        var connection = DriverManager.getConnection("jdbc:mariadb://localhost:3306/grafioschtrader_t",
            "grafioschtrader_t", "grafioschtrader_t");
        var sql = connection.createStatement()) {
      assertThat(connection.getCatalog()).isEqualTo("grafioschtrader_t");
      sql.execute("CREATE TEMPORARY TABLE trading_platform_plan (id INT PRIMARY KEY, fee_model_yaml TEXT)");
      sql.execute("CREATE TEMPORARY TABLE securityaccount (id INT PRIMARY KEY, fee_model_yaml TEXT)");
      sql.execute("CREATE TEMPORARY TABLE algo_simulation_result (id INT PRIMARY KEY)");
      int id = 0;
      try (var insert = connection.prepareStatement("INSERT INTO trading_platform_plan VALUES (?,?)")) {
        for (var append : appends.subList(0, 6)) {
          insert.setInt(1, ++id);
          insert.setString(2, commissions.get(append.hash()));
          insert.executeUpdate();
        }
        insert.setInt(1, 7);
        insert.setString(2, FxMarkupEngineTest.COMMISSION);
        insert.executeUpdate();
        insert.setInt(1, 8);
        insert.setString(2, null);
        insert.executeUpdate();
      }
      try (var insert = connection.prepareStatement("INSERT INTO securityaccount VALUES (1,?)")) {
        insert.setString(1, commissions.get(appends.getFirst().hash()));
        insert.executeUpdate();
      }
      List<String> first = null;
      for (int pass = 0; pass < 2; pass++) {
        for (var append : appends)
          sql.execute(append.sql());
        String ddl = Files.readString(MIGRATIONS.resolve(MIGRATION));
        int start = ddl.indexOf("ALTER TABLE algo_simulation_result ADD COLUMN IF NOT EXISTS fx_markup_paid");
        sql.execute(ddl.substring(start, ddl.indexOf(';', start)));
        List<String> documents = new ArrayList<>();
        try (var rows = sql.executeQuery("SELECT fee_model_yaml FROM trading_platform_plan ORDER BY id")) {
          while (rows.next())
            documents.add(rows.getString(1));
        }
        if (pass == 0)
          first = documents;
        else
          assertThat(documents).isEqualTo(first);
        assertThat(documents.get(6)).isEqualTo(FxMarkupEngineTest.COMMISSION);
        assertThat(documents.get(7)).isNull();
        assertThat(documents.subList(0, 6).stream().filter(s -> s.contains("\nfx:")).count()).isEqualTo(4);
        for (String document : documents.subList(0, 6))
          assertThat(new FeeModelYamlValidator().validate(document)).isEmpty();
      }
      try (var rows = sql.executeQuery("SELECT fee_model_yaml FROM securityaccount")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo(commissions.get(appends.getFirst().hash()));
      }
    }
  }
}
