package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the invariant that makes the period performance report drop a day it cannot value completely.
 *
 * <p>
 * The four {@code getPeriodHolding*} queries convert every hold row with {@code IFNULL(hqc0.close, 1)} respectively
 * {@code IFNULL(hqc1.close, 1)}. That fallback is meant for a hold row without a currency pair, which is already held
 * in the reporting currency. A row whose currency pair <em>is</em> set but has no rate for the day must never take the
 * fallback, because the foreign currency amount would then enter the report unconverted. The queries prevent this by
 * counting such rows in {@code missingFxSecurities} and {@code missingFxCash} and discarding the whole day, exactly as
 * {@code countQuotes = countSecurities} discards a day on which a held security has no price.
 * </p>
 *
 * <p>
 * The same day must also be reported to the user, so the three queries behind the missing end-of-day price view carry a
 * branch for the currency pair of the security holdings and one for the currency pair of the cash balances.
 * </p>
 *
 * <p>
 * The third invariant is the consistency check of the two holdings tables. {@code id_currency_pair_tenant} and
 * {@code id_currency_pair_portfolio} carry no foreign key and have no Java reader worth speaking of, so a wrong or
 * dangling id would only ever show up as a balance converted at the wrong rate. Both {@code countConsistencyDefects}
 * statements therefore have to compare them, and losing that branch again must not go unnoticed.
 * </p>
 *
 * <p>
 * All three properties are invisible to the compiler and are only observable with a database that actually lacks a rate
 * or holds a wrong id, which is why they are asserted here on the statement text.
 * </p>
 */
class HoldingsNamedQueryGuardTest {

  private static final String RESOURCE = "META-INF/jpa-named-queries.properties";
  private static final String PREFIX = "HoldSecurityaccountSecurity.";

  /** The queries that convert a hold row into the reporting currency and therefore need the missing rate guard. */
  private static final String[] CONVERTING_QUERIES = { "getPeriodHoldingsByTenant", "getPeriodHoldingsByPortfolio",
      "getPeriodHoldingZeroBaseByTenant", "getPeriodHoldingZeroBaseByPortfolio" };

  @Test
  @DisplayName("A hold row with a currency pair but without a rate drops the day instead of being valued at one")
  void everyConversionIsGuardedByAMissingRateCounter() throws IOException {
    Properties queries = readAllNamedQueries();
    List<String> violations = new ArrayList<>();

    for (String name : CONVERTING_QUERIES) {
      String sql = required(queries, name, violations);
      if (sql == null) {
        continue;
      }
      boolean zeroBase = name.contains("ZeroBase");
      // The cash balances are converted in every one of the four queries.
      expect(violations, name, sql.contains("IFNULL(hqc1.close, 1)"), "converts the cash balance with IFNULL");
      expect(violations, name, sql.contains("as missingFxCash"), "counts the cash rows without a rate");
      expect(violations, name, sql.contains(zeroBase ? "x.missingFxCash = 0" : "HAVING missingFxCash = 0"),
          "discards a day with a cash row without a rate");
      if (!zeroBase) {
        // Only the period queries carry security positions, the zero base day has none by definition.
        expect(violations, name, sql.contains("IFNULL(hqc0.close, 1)"), "converts the security position with IFNULL");
        expect(violations, name, sql.contains("as missingFxSecurities"), "counts the positions without a rate");
        expect(violations, name, sql.contains("countQuotes = countSecurities AND missingFxSecurities = 0"),
            "discards a day with a position without a rate");
      }
    }

    // A new query that converts with the same fallback but was not thought through would slip past the list above.
    for (String name : queries.stringPropertyNames()) {
      String sql = queries.getProperty(name);
      if ((sql.contains("IFNULL(hqc0.close, 1)") || sql.contains("IFNULL(hqc1.close, 1)"))
          && !isKnownConvertingQuery(name)) {
        violations.add(name + " converts with the exchange rate fallback but is not covered by this guard");
      }
    }

    assertThat(violations)
        .as("Missing exchange rate guard violations:%n%s", String.join(System.lineSeparator(), violations)).isEmpty();
  }

  @Test
  @DisplayName("The missing end-of-day price view reports the currency pairs of holdings and cash balances")
  void theMissingDayQueriesReportBothCurrencyPairs() throws IOException {
    Properties queries = readAllNamedQueries();
    List<String> violations = new ArrayList<>();

    assertCurrencypairBranches(queries, "getMissingsQuoteDaysByTenant", "tenant", violations);
    assertCurrencypairBranches(queries, "getMissingsQuoteDaysByPortfolio", "portfolio", violations);
    assertCurrencypairBranches(queries, "getMissingQuotesForSecurityByTenantAndPeriod", "tenant", violations);

    assertThat(violations)
        .as("Missing currency pair report violations:%n%s", String.join(System.lineSeparator(), violations)).isEmpty();
  }

  private void assertCurrencypairBranches(Properties queries, String name, String scope, List<String> violations) {
    String sql = required(queries, name, violations);
    if (sql == null) {
      return;
    }
    expect(violations, name, sql.contains("hss.id_currency_pair_" + scope + " = hqc.id_securitycurrency"),
        "reports the currency pair of a security holding");
    expect(violations, name, sql.contains("hcb.id_currency_pair_" + scope + " = hqc.id_securitycurrency"),
        "reports the currency pair of a cash balance");
  }

  @Test
  @DisplayName("The consistency check of both holdings tables compares the denormalised currency pair ids")
  void theConsistencyCheckVerifiesTheDenormalisedCurrencyPairIds() throws IOException {
    Properties queries = readAllNamedQueries();
    List<String> violations = new ArrayList<>();

    for (String name : new String[] { "HoldCashaccountBalance.countConsistencyDefects",
        "HoldSecurityaccountSecurity.countConsistencyDefects" }) {
      String sql = queries.getProperty(name);
      if (sql == null) {
        violations.add(name + " is not defined in " + RESOURCE);
        continue;
      }
      for (String column : new String[] { "id_currency_pair_tenant", "id_currency_pair_portfolio" }) {
        if (!sql.contains(column)) {
          violations.add(name + " no longer compares " + column);
        }
      }
      if (!sql.contains("'CURRENCYPAIR'")) {
        violations.add(name + " no longer emits the CURRENCYPAIR defect kind");
      }
    }

    assertThat(violations)
        .as("Missing currency pair consistency violations:%n%s", String.join(System.lineSeparator(), violations))
        .isEmpty();
  }

  private boolean isKnownConvertingQuery(String name) {
    for (String known : CONVERTING_QUERIES) {
      if (name.equals(PREFIX + known)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the statement of a named query and records a violation when the key is absent, so a renamed or lost query
   * fails this guard instead of letting it pass without having checked anything.
   */
  private String required(Properties queries, String name, List<String> violations) {
    String sql = queries.getProperty(PREFIX + name);
    if (sql == null) {
      violations.add(PREFIX + name + " is not defined in " + RESOURCE);
    }
    return sql;
  }

  private void expect(List<String> violations, String name, boolean fulfilled, String expectation) {
    if (!fulfilled) {
      violations.add(PREFIX + name + " no longer " + expectation);
    }
  }

  /**
   * Reads every {@code jpa-named-queries.properties} on the class path and merges them. The reusable library modules
   * ship a file under the same path, so a single {@code getResourceAsStream} would return whichever module comes first
   * and could silently leave this guard with nothing to check.
   */
  private Properties readAllNamedQueries() throws IOException {
    Properties merged = new Properties();
    Enumeration<URL> resources = getClass().getClassLoader().getResources(RESOURCE);
    int count = 0;
    while (resources.hasMoreElements()) {
      try (InputStream is = resources.nextElement().openStream()) {
        merged.load(is);
        count++;
      }
    }
    assertThat(count).as("No %s found on the class path", RESOURCE).isPositive();
    return merged;
  }
}
