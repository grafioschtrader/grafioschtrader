package grafioschtrader.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Minimal public diagnostic DTO describing the runtime environment of the backend. Intended to be consumed by
 * automated setups (in particular the Playwright E2E globalSetup) so that a test suite can fail fast when the
 * backend is connected to the wrong database or is running under an unexpected Spring profile.
 *
 * The endpoint returning this DTO is intentionally unauthenticated and deliberately exposes only non-sensitive
 * identifiers — the active profile list and the database name parsed from the JDBC URL — never credentials,
 * hosts, ports, or other configuration values.
 */
@Schema(description = """
    Minimal public diagnostic payload used to identify which database and Spring profile the backend is currently \
    using. Consumed by automated setups (e.g. Playwright E2E globalSetup) to guard against running tests against \
    the wrong environment.
    """)
public class GTInfoDTO {

  @Schema(description = """
      Comma-separated list of active Spring profiles (e.g. 'e2e' or 'test'). Empty string when no profile is \
      explicitly active and only defaults apply.""")
  private final String activeProfile;

  @Schema(description = """
      Database name extracted from the JDBC URL in 'spring.datasource.url' — the substring after the last '/', \
      with any '?...' query string stripped. For the E2E profile this is 'grafioschtrader_t'.""")
  private final String databaseName;

  public GTInfoDTO(String activeProfile, String databaseName) {
    this.activeProfile = activeProfile;
    this.databaseName = databaseName;
  }

  public String getActiveProfile() {
    return activeProfile;
  }

  public String getDatabaseName() {
    return databaseName;
  }
}
