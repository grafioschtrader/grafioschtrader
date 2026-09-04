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
   * Cumulative realized fees from the account's first transaction up to and including this date, converted to the
   * relevant currency (MC). The query negates the sum, so a cost is delivered as a positive value. It contains the
   * separately booked account and depot fees alone. The trading costs contained in a purchase or sale are part of
   * {@link #getAccumulateReduceMC()}, and the financing costs of margin positions are reported through the securities
   * result; neither is here, so this figure covers the same bookings as the fee column of the cash account summary.
   *
   * <p>
   * Like every cumulative column of this projection except the external cash transfers, the running total is kept in
   * the cash account's own currency and revalued once with the currency-pair close of the reporting day. The cash
   * account summary converts each booking with the rate of its own date instead, so the two figures differ for
   * foreign-currency accounts by the currency movement since the bookings were made.
   * </p>
   */
  double getFeeRealMC();

  /**
   * Cumulative interest earned on cash accounts from the account's first transaction up to and including this date,
   * converted to the relevant currency (MC). Revalued with the currency-pair close of the reporting day, unlike the
   * cash account summary, which converts each booking with the rate of its own date.
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
