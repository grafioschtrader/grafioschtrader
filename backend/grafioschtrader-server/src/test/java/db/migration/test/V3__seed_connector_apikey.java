package db.migration.test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seeds the {@code connector_apikey} table of the integration-test database
 * from a git-ignored local file produced by {@code backend/nv.bat}.
 *
 * <p>The file is expected at classpath resource
 * {@value #SEED_RESOURCE} and contains ready-to-execute {@code INSERT}
 * statements (one per line, semicolon-terminated) copying ciphertext rows
 * verbatim from the developer's prod database. If the resource is absent or
 * empty, this migration logs a notice and succeeds without touching the table
 * — connector tests then skip cleanly via {@code assumeTrue(isActivated())}.
 */
public class V3__seed_connector_apikey extends BaseJavaMigration {

  private static final Logger log = LoggerFactory.getLogger(V3__seed_connector_apikey.class);

  private static final String SEED_RESOURCE = "local-seed/connector_apikey.sql";

  @Override
  public void migrate(Context context) throws Exception {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(SEED_RESOURCE)) {
      if (in == null) {
        log.info("No {} present; leaving connector_apikey empty (API-key connector tests will be skipped).",
            SEED_RESOURCE);
        return;
      }
      String body = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
      if (body.isEmpty()) {
        log.info("{} is empty; leaving connector_apikey empty.", SEED_RESOURCE);
        return;
      }
      int applied = 0;
      try (Statement st = context.getConnection().createStatement()) {
        for (String stmt : body.split(";\\r?\\n")) {
          String trimmed = stmt.trim();
          if (trimmed.isEmpty() || trimmed.startsWith("--")) {
            continue;
          }
          st.execute(trimmed);
          applied++;
        }
      }
      log.info("connector_apikey seeded from {} ({} statement(s) applied).", SEED_RESOURCE, applied);
    }
  }
}
