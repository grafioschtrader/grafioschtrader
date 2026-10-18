package grafioschtrader.connector.fundamental;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafiosch.entities.ConnectorApiKey;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.FundamentalSourceProbe;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.Metric;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.ProbeResult;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.ProbeSecurity;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.Status;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.UDFSpecialGTType;
import tools.jackson.databind.JsonNode;

/**
 * Maintainer utility that measures how well free or already licensed sources cover the fundamental data of the stocks
 * in the real database. It is not a regression test: it produces a coverage report from which the sources of a later
 * fundamental data feature are chosen.
 *
 * <p>
 * The universe is every active direct equity that is in a watchlist or has a transaction. For each source and stock
 * the report records whether the base values of {@link Metric} were delivered. The database is opened read-only with
 * plain JDBC; no Spring context starts, so no scheduled task and no Flyway can touch the real database.
 * </p>
 *
 * <p>
 * <b>Invocation</b> — opt-in, because it reads the real database and calls external services:
 * </p>
 *
 * <pre>
 * cd backend
 * $env:GT_PROBE_DB_PASSWORD = '&lt;database password&gt;'
 * $env:JASYPT_ENCRYPTOR_PASSWORD = '&lt;jasypt password&gt;'   # decrypts the stored connector keys
 * mvn -pl grafioschtrader-server test -Dtest=FundamentalSourceCoverageProbeTest -Dgt.probe.fundamentals=true
 *     -Dgt.probe.sec.contact=&lt;email&gt;
 * </pre>
 *
 * <p>
 * Optional: {@code -Dgt.probe.fundamentals.sources=sec,eulerpool,finnhub,yahoo,eodhd,simfin} selects sources (the
 * default omits {@code simfin}, whose key is not stored in {@code connector_apikey}),
 * {@code -Dgt.probe.fundamentals.max=N} limits the number of stocks, {@code -Dgt.probe.fundamentals.ids=1,2,3}
 * restricts the run to the given {@code id_securitycurrency} values, {@code -Dgt.probe.db.url=…} and
 * {@code -Dgt.probe.db.user=…} point elsewhere. Without the Jasypt password a key can be given directly as environment
 * variable {@code GT_PROBE_KEY_EULERPOOL}, {@code GT_PROBE_KEY_FINNHUB}, {@code GT_PROBE_KEY_EODHISTORICALDATA} or
 * {@code GT_PROBE_KEY_SIMFIN};
 * a source without key is reported as skipped.
 * </p>
 *
 * <p>
 * Output in {@code grafioschtrader-server/target/fundamental-probe/}: {@code coverage.csv} (one row per source and
 * stock), {@code summary.csv} (per source and exchange) and {@code samples/} (the first answers per source, keys
 * masked). No key is ever logged or written.
 * </p>
 */
class FundamentalSourceCoverageProbeTest {

  private static final String ENABLE_PROPERTY = "gt.probe.fundamentals";
  private static final String DEFAULT_SOURCES = "sec,eulerpool,finnhub,yahoo,eodhd";
  private static final Path OUTPUT = Path.of("target", "fundamental-probe");
  private static final int SAMPLES_PER_SOURCE = 3;
  private static final String DELIMITER = "|";

  private static final Logger log = LoggerFactory.getLogger(FundamentalSourceCoverageProbeTest.class);

  @Test
  @DisplayName("Measure the fundamental data coverage of external sources for the stocks of the real database")
  void probeFundamentalSources() throws Exception {
    assumeTrue(Boolean.getBoolean(ENABLE_PROPERTY), () -> "Probe is opt-in, run with -D" + ENABLE_PROPERTY + "=true");
    String dbPassword = System.getenv("GT_PROBE_DB_PASSWORD");
    assumeTrue(dbPassword != null, "GT_PROBE_DB_PASSWORD is not set");

    List<ProbeSecurity> securities;
    Map<String, String> keys;
    try (Connection connection = DriverManager.getConnection(
        System.getProperty("gt.probe.db.url", "jdbc:mariadb://localhost:3306/grafioschtrader"),
        System.getProperty("gt.probe.db.user", "grafioschtrader"), dbPassword)) {
      connection.setReadOnly(true);
      securities = loadSecurities(connection);
      keys = loadKeys(connection);
    }
    String ids = System.getProperty("gt.probe.fundamentals.ids");
    if (ids != null && !ids.isBlank()) {
      List<String> idList = Arrays.stream(ids.split(",")).map(String::trim).toList();
      securities = securities.stream().filter(s -> idList.contains(String.valueOf(s.id()))).toList();
    }
    int max = Integer.getInteger("gt.probe.fundamentals.max", Integer.MAX_VALUE);
    if (securities.size() > max) {
      securities = securities.subList(0, max);
    }
    log.info("Probing {} stocks", securities.size());

    List<ProbeResult> results = runProbes(createProbes(keys), securities);
    writeOutput(results);
  }

  private List<FundamentalSourceProbe> createProbes(Map<String, String> keys) {
    List<String> selected = Arrays.stream(System.getProperty("gt.probe.fundamentals.sources", DEFAULT_SOURCES)
        .split(",")).map(String::trim).toList();
    List<FundamentalSourceProbe> probes = new ArrayList<>();
    for (String source : selected) {
      switch (source) {
      case "sec" -> probes.add(new SecEdgarProbe(System.getProperty("gt.probe.sec.contact")));
      case "eulerpool" -> probes.add(new EulerpoolProbe(keys.get("eulerpool")));
      case "finnhub" -> probes.add(new FinnhubProbe(keys.get("finnhub")));
      case "yahoo" -> probes.add(new YahooQuoteSummaryProbe());
      case "eodhd" -> probes.add(new EodhdProbe(keys.get("eodhistoricaldata")));
      case "simfin" -> probes.add(new SimFinProbe(keys.get("simfin")));
      default -> throw new IllegalArgumentException("Unknown source: " + source);
      }
    }
    return probes;
  }

  /**
   * Active direct equities that are in a watchlist or have a transaction, with the intraday connector and its
   * extension (the symbol a connector already uses) and the system-wide Yahoo symbol UDF.
   */
  private List<ProbeSecurity> loadSecurities(Connection connection) throws SQLException {
    String sql = """
        SELECT s.id_securitycurrency, s.name, s.isin, s.ticker_symbol, st.mic, s.currency, sc.id_connector_intra,
          sc.url_intra_extend, u.json_values
        FROM security s JOIN securitycurrency sc ON sc.id_securitycurrency = s.id_securitycurrency
        JOIN assetclass a ON a.id_asset_class = s.id_asset_class
        JOIN stockexchange st ON st.id_stockexchange = s.id_stockexchange
        LEFT JOIN udf_data u ON u.id_user = 0 AND u.entity = 'Security' AND u.id_entity = s.id_securitycurrency
        WHERE a.category_type = ? AND a.spec_invest_instrument = ? AND s.active_to_date >= CURDATE()
          AND (EXISTS (SELECT 1 FROM watchlist_sec_cur w WHERE w.id_securitycurrency = s.id_securitycurrency)
            OR EXISTS (SELECT 1 FROM transaction t WHERE t.id_securitycurrency = s.id_securitycurrency))
        ORDER BY st.mic, s.name""";
    String yahooKey = "f" + yahooSymbolField(connection);
    List<ProbeSecurity> securities = new ArrayList<>();
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setByte(1, AssetclassType.EQUITIES.getValue());
      ps.setByte(2, SpecialInvestmentInstruments.DIRECT_INVESTMENT.getValue());
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          // A stock exchange without MIC exists; the immutable lookup maps of the probes reject a null key.
          securities.add(new ProbeSecurity(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
              nullToEmpty(rs.getString(5)), rs.getString(6), rs.getString(7), rs.getString(8),
              udfValue(rs.getString(9), yahooKey)));
        }
      }
    }
    return securities;
  }

  /** Id of the system-wide UDF that stores the resolved Yahoo symbol of a security. */
  private int yahooSymbolField(Connection connection) throws SQLException {
    try (PreparedStatement ps = connection
        .prepareStatement("SELECT id_udf_metadata FROM udf_metadata WHERE id_user = 0 AND udf_special_type = ?")) {
      ps.setByte(1, UDFSpecialGTType.UDF_SPEC_INTERNAL_YAHOO_SYMBOL_HIDE.getValue());
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : -1;
      }
    }
  }

  private static String udfValue(String jsonValues, String key) {
    JsonNode root = FundamentalProbeSupport.parse(jsonValues);
    return root == null || !root.has(key) ? null : root.get(key).asString(null);
  }

  /**
   * Keys of the providers by id. An environment variable {@code GT_PROBE_KEY_<PROVIDER>} wins; otherwise the stored
   * key is decrypted with the entity's own encryptor, which needs {@code JASYPT_ENCRYPTOR_PASSWORD}.
   */
  private Map<String, String> loadKeys(Connection connection) throws SQLException {
    Map<String, String> keys = new HashMap<>();
    boolean canDecrypt = System.getenv("JASYPT_ENCRYPTOR_PASSWORD") != null;
    try (PreparedStatement ps = connection.prepareStatement("SELECT id_provider, api_key FROM connector_apikey");
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        if (canDecrypt) {
          keys.put(rs.getString(1), ConnectorApiKey.stringEncryptor().decrypt(rs.getString(2)));
        }
      }
    }
    for (String provider : List.of("eulerpool", "finnhub", "eodhistoricaldata", "simfin")) {
      String override = System.getenv("GT_PROBE_KEY_" + provider.toUpperCase(Locale.ROOT));
      if (override != null && !override.isBlank()) {
        keys.put(provider, override.trim());
      }
    }
    return keys;
  }

  /** Runs every source on its own thread, so that the slowest rate limit sets the duration, not their sum. */
  private List<ProbeResult> runProbes(List<FundamentalSourceProbe> probes, List<ProbeSecurity> securities)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(probes.size());
    try {
      List<Future<List<ProbeResult>>> futures = new ArrayList<>();
      for (FundamentalSourceProbe probe : probes) {
        futures.add(executor.submit(() -> runProbe(probe, securities)));
      }
      List<ProbeResult> results = new ArrayList<>();
      for (Future<List<ProbeResult>> future : futures) {
        results.addAll(future.get());
      }
      return results;
    } finally {
      executor.shutdownNow();
    }
  }

  private List<ProbeResult> runProbe(FundamentalSourceProbe probe, List<ProbeSecurity> securities)
      throws InterruptedException {
    String skipReason = probe.prepare();
    List<ProbeResult> results = new ArrayList<>();
    for (ProbeSecurity security : securities) {
      if (skipReason != null) {
        results.add(ProbeResult.of(probe.name(), security, "", Status.NO_KEY, 0, skipReason));
        continue;
      }
      results.add(probe.probe(security));
      Thread.sleep(probe.pause());
    }
    log.info("Source {} finished{}", probe.name(), skipReason == null ? "" : ", skipped: " + skipReason);
    return results;
  }

  private void writeOutput(List<ProbeResult> results) throws IOException {
    Files.createDirectories(OUTPUT.resolve("samples"));
    Files.writeString(OUTPUT.resolve("coverage.csv"), coverageCsv(results), StandardCharsets.UTF_8);
    String summary = summaryCsv(results);
    Files.writeString(OUTPUT.resolve("summary.csv"), summary, StandardCharsets.UTF_8);
    writeSamples(results);
    log.info("Coverage written to {}\n{}", OUTPUT.toAbsolutePath(), summary);
  }

  private String coverageCsv(List<ProbeResult> results) {
    StringBuilder csv = new StringBuilder(String.join(DELIMITER, "source", "id", "mic", "isin", "ticker", "name",
        "symbol", "status", "http", metricHeader(), "fields", "latest_period", "values", "note")).append('\n');
    for (ProbeResult r : results) {
      ProbeSecurity s = r.security();
      csv.append(String.join(DELIMITER, r.source(), String.valueOf(s.id()), s.mic(), nullToEmpty(s.isin()),
          nullToEmpty(s.ticker()), clean(s.name()), clean(r.symbol()), r.status().name(),
          String.valueOf(r.httpStatus()), metricFlags(r), String.valueOf(r.fieldCount()), r.latestPeriod(),
          clean(r.values()), clean(r.note()))).append('\n');
    }
    return csv.toString();
  }

  /**
   * One row per source and exchange plus one {@code ALL} row per source: counts per status and per metric, and how
   * many answers name a reporting period no older than 400 days.
   */
  private String summaryCsv(List<ProbeResult> results) {
    StringBuilder csv = new StringBuilder(String.join(DELIMITER, "source", "mic", "total",
        Arrays.stream(Status.values()).map(Enum::name).collect(Collectors.joining(DELIMITER)), metricHeader(),
        "fresh_period")).append('\n');
    Map<String, List<ProbeResult>> bySource = results.stream()
        .collect(Collectors.groupingBy(ProbeResult::source, LinkedHashMap::new, Collectors.toList()));
    bySource.forEach((source, rows) -> {
      Map<String, List<ProbeResult>> byMic = rows.stream()
          .collect(Collectors.groupingBy(r -> nullToEmpty(r.security().mic()), TreeMap::new, Collectors.toList()));
      byMic.forEach((mic, micRows) -> csv.append(summaryRow(source, mic, micRows)).append('\n'));
      csv.append(summaryRow(source, "ALL", rows)).append('\n');
    });
    return csv.toString();
  }

  private String summaryRow(String source, String mic, List<ProbeResult> rows) {
    List<String> cells = new ArrayList<>(List.of(source, mic, String.valueOf(rows.size())));
    for (Status status : Status.values()) {
      cells.add(String.valueOf(rows.stream().filter(r -> r.status() == status).count()));
    }
    for (Metric metric : Metric.values()) {
      cells.add(String.valueOf(rows.stream().filter(r -> Boolean.TRUE.equals(r.metrics().get(metric))).count()));
    }
    String freshLimit = LocalDate.now().minusDays(400).toString();
    cells.add(String.valueOf(
        rows.stream().filter(r -> r.latestPeriod().length() >= 10 && r.latestPeriod().compareTo(freshLimit) >= 0)
            .count()));
    return String.join(DELIMITER, cells);
  }

  private void writeSamples(List<ProbeResult> results) throws IOException {
    Map<String, Integer> written = new HashMap<>();
    for (ProbeResult r : results) {
      if (r.status() == Status.FOUND && r.body() != null
          && written.merge(r.source(), 1, Integer::sum) <= SAMPLES_PER_SOURCE) {
        String file = r.source() + "-" + r.security().id() + ".json";
        Files.writeString(OUTPUT.resolve("samples").resolve(file), r.body(), StandardCharsets.UTF_8);
      }
    }
  }

  private static String metricHeader() {
    return Arrays.stream(Metric.values()).map(m -> m.name().toLowerCase(Locale.ROOT))
        .collect(Collectors.joining(DELIMITER));
  }

  private static String metricFlags(ProbeResult r) {
    return Arrays.stream(Metric.values()).map(m -> Boolean.TRUE.equals(r.metrics().get(m)) ? "1" : "0")
        .collect(Collectors.joining(DELIMITER));
  }

  private static String clean(String text) {
    return text == null ? "" : text.replace(DELIMITER, "/").replace('\n', ' ').replace('\r', ' ');
  }

  private static String nullToEmpty(String text) {
    return text == null ? "" : text;
  }
}
