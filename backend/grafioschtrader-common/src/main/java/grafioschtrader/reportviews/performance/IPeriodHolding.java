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
   * Cumulative external cash transfers (deposits less withdrawals) up to and including the date, in MC. It is a level,
   * not the flow of the day: it sums the deposit column of {@code hold_cashaccount_deposit} over the rows valid on that
   * date, so the flow between two dates is the difference of their values.
   */
  double getExternalCashTransferMC();

  /**
   * Market value of all held securities on the date, in MC.
   */
  double getSecuritiesMC();

  /**
   * Open result of the margin positions on the date, in MC:
   * {@code holdings × split_price_factor × (price − margin_average_price / split_price_factor) × exchange rate}. It is
   * complementary to {@link #getSecuritiesMC()}, which contains only positions without a margin average price, and is
   * therefore part of the total value of the day.
   */
  double getMarginCloseGainMC();

  /**
   * Security risk of the positions on the date, in MC: the market value of every held position, margin positions at
   * their full exposure, multiplied by the leverage factor of the instrument. It is the same figure as the security
   * risk of the security account reports, so a leveraged or inverse instrument contributes with its factor.
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
