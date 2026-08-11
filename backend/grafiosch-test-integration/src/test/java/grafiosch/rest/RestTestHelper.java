package grafiosch.rest;

import grafiosch.test.rest.RestTestHelperBase;

/**
 * View on the shared integration test fixture for the reusable libraries. Everything generic — loading
 * {@code testdata/users.json}, {@code users} / {@code ALL_USERS} / {@code LIMIT_USERS}, {@code getUserByNickname} and
 * {@code inizializeUserTokens} — is inherited from {@link RestTestHelperBase} and stays reachable through this class
 * name.
 *
 * <p>
 * Only the nickname constants of this module's {@code users.json} live here. They mirror the Grafioschtrader fixture so
 * a test can be moved between the two suites without renaming users.
 */
public class RestTestHelper extends RestTestHelperBase {

  public static final String ADMIN = "admin";
  public static final String ALLEDIT = "alledit";
  public static final String USER = "user";
  public static final String LIMITED = "limited";
}
