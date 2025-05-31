package grafioschtrader.reportviews.performance;

import java.time.LocalDate;

/**
 * Projection interface representing daily aggregated holdings and performance metrics for trading dates, usable both
 * per individual portfolio and across the entire tenant.
 * <p>
 * Each instance provides values converted to the relevant currency (portfolio or tenant), including security positions,
 * cumulative realized dividends, cumulative fees, cumulative interest, cash balances, external transfers, margin gains,
 * market risk, and net gain for the day.
 * </p>
 */
public interface IPeriodHolding {

  /**
   * Trading date of the snapshot.
   */
  LocalDate getDate();

  /**
   * Cumulative realized dividends from the start date up to and including this date, converted to the relevant currency
   * (MC).
   */
  double getDividendRealMC();

  /**
   * Cumulative realized fees (negative values) from the start date up to and including this date, converted to the
   * relevant currency (MC).
   */
  double getFeeRealMC();

  /**
   * Cumulative interest earned on cash accounts from the start date up to and including this date, converted to the
   * relevant currency (MC).
   */
  double getInterestCashaccountRealMC();

  /**
   * Net effect of security buy (accumulate) or sell (reduce) transactions, converted to cash
   */
  double getAccumulateReduceMC();

  /**
   * Cash balance on the date, in MC.
   */
  double getCashBalanceMC();

  /**
   * External cash transfers (deposits or withdrawals) on the date, in MC.
   */
  double getExternalCashTransferMC();

  /**
   * Market value of all held securities on the date, in MC.
   */
  double getSecuritiesMC();

  /**
   * Realized gain/loss from closing margin positions on the date, in MC.
   */
  double getMarginCloseGainMC();

  /**
   * Market risk (unrealized value) of positions on the date, in MC.
   */
  double getSecurityRiskMC();

  /**
   * Net gain of the day:
   *
   * <pre>
   * gainMC = cashBalanceMC + securitiesMC - externalCashTransferMC
   * </pre>
   */
  double getGainMC();
}
