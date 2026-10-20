package grafioschtrader.fundamental;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import grafioschtrader.connector.fundamental.FundamentalAnswer;
import grafioschtrader.entities.SecurityFundamental;
import grafioschtrader.types.FundamentalMetric;
import grafioschtrader.types.FundamentalSource;
import grafioschtrader.types.FundamentalSourceCheck;

/**
 * The checks that need input outside the stored rows — the last price of the security, the currency pairs and fields of
 * the same answer that are not stored. They run at load time over the rows of the sources that just answered and record
 * their outcome in {@code sourceCheck}, so that the selection can stay a pure function of the stored rows.
 *
 * <p>
 * A check whose input is missing leaves {@code NOT_CHECKED}: "could not check" is never "passed". Per row the outcome
 * is {@code FAILED} when one check failed, otherwise {@code PASSED} when one passed.
 * </p>
 */
public final class FundamentalSourceChecks {

  static final double PRICE_TOLERANCE = 0.20;
  static final double MARKET_CAP_TOLERANCE = 0.20;
  static final double NET_INCOME_TOLERANCE = 0.25;

  /** Converts an amount between two currencies with the latest rate of the GT currency pairs. */
  @FunctionalInterface
  public interface ExchangeRate {
    /**
     * @return the factor that turns an amount in {@code from} into {@code to}, 1 for the same currency, null when no
     *         currency pair connects them
     */
    Double rate(String from, String to);
  }

  private FundamentalSourceChecks() {
  }

  /**
   * Sets {@code sourceCheck} of the rows that were just loaded.
   *
   * @param loaded           rows of the sources that gave a usable answer in this run; their check is overwritten
   * @param all              every row of the security after the replacement, including rows of sources that failed in
   *                         this run, from which the evidence of Yahoo is read
   * @param evidence         per source the values read from its answer without being stored
   * @param lastPrice        the last price of the security in its currency, null when unknown
   * @param securityCurrency the currency of the security
   * @param exchangeRate     the currency conversion
   */
  public static void apply(Collection<SecurityFundamental> loaded, Collection<SecurityFundamental> all,
      Map<FundamentalSource, Map<String, Double>> evidence, Double lastPrice, String securityCurrency,
      ExchangeRate exchangeRate) {
    SecurityFundamental pe = find(all, FundamentalSource.YAHOO, FundamentalMetric.PE_CHECK);
    SecurityFundamental marketCap = find(all, FundamentalSource.YAHOO, FundamentalMetric.MCAP_CHECK);
    SecurityFundamental yahooShares = find(loaded, FundamentalSource.YAHOO, FundamentalMetric.SHARES);
    SecurityFundamental eulerpoolEps = find(loaded, FundamentalSource.EULERPOOL, FundamentalMetric.EPS_FY);
    Double netIncome = evidence.getOrDefault(FundamentalSource.EULERPOOL, Map.of())
        .get(FundamentalAnswer.EVIDENCE_NET_INCOME);
    for (SecurityFundamental row : loaded) {
      Map<String, FundamentalSourceCheck> outcomes = new HashMap<>();
      if (row.getSource() == FundamentalSource.YAHOO && row.getMetric() == FundamentalMetric.EPS_TTM) {
        outcomes.put("price", checkEpsAgainstPrice(row, pe, lastPrice, securityCurrency, exchangeRate));
      }
      if (row.getSource() == FundamentalSource.YAHOO && row.getMetric() == FundamentalMetric.SPS_TTM) {
        // Revenue divided by a count of ordinary shares must not be exposed as revenue per ADR.
        outcomes.put("shareBasis",
            checkSharesAgainstMarketCap(yahooShares, marketCap, lastPrice, securityCurrency, exchangeRate));
      }
      if (row.getMetric() == FundamentalMetric.SHARES) {
        outcomes.put("marketCap",
            checkSharesAgainstMarketCap(row, marketCap, lastPrice, securityCurrency, exchangeRate));
        if (row.getSource() == FundamentalSource.EULERPOOL
            && checkEulerpoolUnits(row, eulerpoolEps, netIncome) == FundamentalSourceCheck.FAILED) {
          outcomes.put("units", FundamentalSourceCheck.FAILED);
        }
      }
      row.setSourceCheck(combine(outcomes.values()));
    }
  }

  /**
   * Check 1: Yahoo's trailing EPS must reproduce Yahoo's trailing P/E with the last price of the GT listing, both in
   * the quote currency of the Yahoo symbol. It proves that the EPS fits the share and currency of the listing.
   */
  static FundamentalSourceCheck checkEpsAgainstPrice(SecurityFundamental eps, SecurityFundamental pe, Double lastPrice,
      String securityCurrency, ExchangeRate exchangeRate) {
    if (pe == null || eps.getValue() <= 0 || pe.getValue() <= 0 || lastPrice == null || lastPrice <= 0) {
      return FundamentalSourceCheck.NOT_CHECKED;
    }
    Double price = convert(lastPrice, securityCurrency, eps.getCurrency(), exchangeRate);
    if (price == null) {
      return FundamentalSourceCheck.NOT_CHECKED;
    }
    return Math.abs(price / eps.getValue() - pe.getValue()) <= PRICE_TOLERANCE * pe.getValue()
        ? FundamentalSourceCheck.PASSED
        : FundamentalSourceCheck.FAILED;
  }

  /**
   * Check 2: a share count times the last price must reproduce Yahoo's market capitalisation. It catches a count of
   * another share class or of the ordinary shares behind an ADR.
   */
  static FundamentalSourceCheck checkSharesAgainstMarketCap(SecurityFundamental shares, SecurityFundamental marketCap,
      Double lastPrice, String securityCurrency, ExchangeRate exchangeRate) {
    if (shares == null || marketCap == null || marketCap.getValue() <= 0 || shares.getValue() <= 0 || lastPrice == null
        || lastPrice <= 0) {
      return FundamentalSourceCheck.NOT_CHECKED;
    }
    Double price = convert(lastPrice, securityCurrency, marketCap.getCurrency(), exchangeRate);
    if (price == null) {
      return FundamentalSourceCheck.NOT_CHECKED;
    }
    return Math.abs(price * shares.getValue() / marketCap.getValue() - 1) <= MARKET_CAP_TOLERANCE
        ? FundamentalSourceCheck.PASSED
        : FundamentalSourceCheck.FAILED;
  }

  /**
   * Check 3: Eulerpool's fiscal year EPS times its share count must reproduce the net income of the same period. It
   * catches a slip of the millions factor of {@code other.shares}; only its failure counts.
   */
  static FundamentalSourceCheck checkEulerpoolUnits(SecurityFundamental shares, SecurityFundamental eps,
      Double netIncome) {
    if (eps == null || netIncome == null || netIncome == 0
        || !Objects.equals(eps.getPeriodEnd(), shares.getPeriodEnd())) {
      return FundamentalSourceCheck.NOT_CHECKED;
    }
    return Math.abs(eps.getValue() * shares.getValue() / netIncome - 1) <= NET_INCOME_TOLERANCE
        ? FundamentalSourceCheck.PASSED
        : FundamentalSourceCheck.FAILED;
  }

  private static FundamentalSourceCheck combine(Collection<FundamentalSourceCheck> outcomes) {
    if (outcomes.contains(FundamentalSourceCheck.FAILED)) {
      return FundamentalSourceCheck.FAILED;
    }
    return outcomes.contains(FundamentalSourceCheck.PASSED) ? FundamentalSourceCheck.PASSED
        : FundamentalSourceCheck.NOT_CHECKED;
  }

  private static Double convert(double amount, String from, String to, ExchangeRate exchangeRate) {
    if (from == null || to == null) {
      return null;
    }
    Double rate = from.equals(to) ? Double.valueOf(1.0) : exchangeRate.rate(from, to);
    return rate == null || rate <= 0 ? null : amount * rate;
  }

  private static SecurityFundamental find(Collection<SecurityFundamental> rows, FundamentalSource source,
      FundamentalMetric metric) {
    return rows.stream().filter(r -> r.getSource() == source && r.getMetric() == metric).findFirst().orElse(null);
  }
}
