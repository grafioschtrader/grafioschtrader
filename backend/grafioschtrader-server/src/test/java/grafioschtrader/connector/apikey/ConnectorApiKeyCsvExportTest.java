package grafioschtrader.connector.apikey;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import grafiosch.entities.ConnectorApiKey;
import grafiosch.repository.ConnectorApiKeyJpaRepository;
import grafiosch.types.ISubscriptionType;
import grafioschtrader.test.start.GTforTest;

/**
 * Maintainer utility that exports the connector API keys of the real database into the git-ignored CSV fixture consumed
 * by the E2E suite. This is not a regression test — it produces test data.
 *
 * <p>
 * The keys are stored Jasypt-encrypted ({@code PBEWITHHMACSHA512ANDAES_256} with random salt and IV). Playwright spec
 * {@code frontend/e2e/04-connector-api-key.spec.ts} creates them through the admin UI, and the backend re-encrypts
 * whatever the dialog submits, so the fixture must contain <b>plaintext</b> keys. Decryption is not reimplemented here:
 * {@link ConnectorApiKey#getApiKey()} decrypts with the entity's own encryptor, keyed by the
 * {@code JASYPT_ENCRYPTOR_PASSWORD} environment variable.
 * </p>
 *
 * <p>
 * <b>Invocation</b> — the export never happens as a side effect of a normal test run, because it reads the real
 * production database. Both the system property and the Jasypt password must be present:
 * </p>
 *
 * <pre>
 * cd backend
 * mvn -pl grafioschtrader-server test -Dtest=ConnectorApiKeyCsvExportTest -Dgt.export.apikeys=true
 * </pre>
 *
 * <p>
 * The output file {@value #OUTPUT_RELATIVE} is deliberately git-ignored: unlike everything else under
 * {@code src/test/resources}, it holds usable credentials in the clear. A contributor without the file is the normal
 * case — the Playwright spec then skips, and so do the API-key connector tests running against
 * {@code grafioschtrader_t}.
 * </p>
 */
@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("prod")
class ConnectorApiKeyCsvExportTest {

  /** Opt-in system property; without it the export is skipped so blanket test runs never touch the real database. */
  private static final String ENABLE_PROPERTY = "gt.export.apikeys";

  /** Target file, relative to the {@code grafioschtrader-server} module directory (the surefire working directory). */
  private static final String OUTPUT_RELATIVE = "src/test/resources/local-seed/connector_apikey.csv";

  /** Pipe-delimited and double-quoted, matching testdata/generated/*.csv so e2e/helpers.ts parseCsvRow() reads it. */
  private static final String DELIMITER = "|";

  private static final Logger log = LoggerFactory.getLogger(ConnectorApiKeyCsvExportTest.class);

  private final ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository;

  @Value("${spring.datasource.url}")
  private String datasourceUrl;

  @Autowired
  ConnectorApiKeyCsvExportTest(ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository) {
    this.connectorApiKeyJpaRepository = connectorApiKeyJpaRepository;
  }

  /**
   * Writes one CSV row per configured provider as {@code idProvider|apiKey|subscriptionType}, sorted by provider so the
   * file stays diff-stable across exports. The subscription-type field is empty for providers that have no subscription
   * levels (generic connectors).
   */
  @Test
  @DisplayName("Export connector_apikey of the real database to the git-ignored CSV fixture")
  void exportConnectorApiKeysToCsv() throws IOException {
    assumeTrue(Boolean.getBoolean(ENABLE_PROPERTY),
        () -> "Export is opt-in, run with -D" + ENABLE_PROPERTY + "=true");
    assumeTrue(System.getenv("JASYPT_ENCRYPTOR_PASSWORD") != null,
        "JASYPT_ENCRYPTOR_PASSWORD is not set, the API keys could not be decrypted");
    assertRealDatabase();

    final Path output = resolveOutput();
    final List<ConnectorApiKey> connectorApiKeys = connectorApiKeyJpaRepository.findAll().stream()
        .sorted(Comparator.comparing(ConnectorApiKey::getIdProvider)).toList();

    Files.createDirectories(output.getParent());
    final StringBuilder csv = new StringBuilder();
    for (ConnectorApiKey connectorApiKey : connectorApiKeys) {
      csv.append(toCsvRow(connectorApiKey)).append(System.lineSeparator());
    }
    Files.writeString(output, csv.toString(), StandardCharsets.UTF_8);

    // Never log a key value: the count is the only safe progress information.
    log.info("Exported {} connector API key(s) to {}", connectorApiKeys.size(), output.toAbsolutePath());
  }

  /**
   * Builds one quoted, pipe-delimited row. The API key arrives decrypted from {@link ConnectorApiKey#getApiKey()}; the
   * subscription type is written as its enum name, which is also what the edit dialog's select offers.
   */
  private String toCsvRow(ConnectorApiKey connectorApiKey) {
    final ISubscriptionType subscriptionType = connectorApiKey.getSubscriptionType();
    return String.join(DELIMITER, quote(connectorApiKey.getIdProvider(), "id_provider"),
        quote(connectorApiKey.getApiKey(), "api_key"),
        quote(subscriptionType == null ? "" : ((Enum<?>) subscriptionType).name(), "subscription_type"));
  }

  /**
   * Wraps a value in double quotes. {@code parseCsvRow()} on the consuming side knows no escape sequence, so a value
   * containing a quote or the delimiter would silently corrupt the fixture — fail loudly instead.
   */
  private String quote(String value, String column) {
    if (value.contains("\"") || value.contains(DELIMITER) || value.contains("\n")) {
      fail("Value of " + column + " contains a quote, a '" + DELIMITER + "' or a newline and cannot be written as CSV");
    }
    return "\"" + value + "\"";
  }

  /**
   * Guards against exporting from the wrong database. Without this an accidental {@code test} profile run would quietly
   * overwrite the fixture with the (empty) content of {@code grafioschtrader_t}.
   */
  private void assertRealDatabase() {
    if (datasourceUrl.contains("grafioschtrader_t")) {
      fail("Connected to the test database (" + datasourceUrl + "), expected the real database. "
          + "Run this export with @ActiveProfiles(\"prod\") only.");
    }
  }

  /**
   * Resolves the output path relative to the current working directory, which Maven surefire sets to the module
   * directory. Fails with an actionable message when the test is launched from elsewhere.
   */
  private Path resolveOutput() {
    final Path output = Path.of(OUTPUT_RELATIVE);
    if (!Files.isDirectory(Path.of("src/test/resources"))) {
      fail("Working directory is " + Path.of("").toAbsolutePath() + ", expected the grafioschtrader-server module "
          + "directory. Start the export with: mvn -pl grafioschtrader-server test -Dtest="
          + getClass().getSimpleName() + " -D" + ENABLE_PROPERTY + "=true");
    }
    return output;
  }
}
