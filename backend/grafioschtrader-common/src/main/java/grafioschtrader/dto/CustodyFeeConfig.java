package grafioschtrader.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Dated custody schedules, independent of the transaction commission schedule. Monetary inputs use fee currency. */
@Schema(description = "Dated custody charges and expiring commission credits; omitted means custody is not modelled.")
public record CustodyFeeConfig(List<Period> periods) {
  public enum Status {
    VERIFIED, ASSUMED, UNRESOLVED
  }

  public enum Valuation {
    NONE, DAILY, MONTHLY, PERIOD_END
  }

  public enum Collection {
    START, END
  }

  public enum DayCount {
    ACTUAL_365, ACTUAL_360, ACTUAL_ACTUAL
  }

  public enum ClosingPolicy {
    ACCRUED, FULL_PERIOD, PRORATED
  }

  /**
   * amount is a billing-period amount for NONE, otherwise an annual amount. Value rules return chargeable position
   * values; the first matching rule wins. minimum/maximum apply per bill, annualCap before VAT per calendar year.
   * observationDay=0 means month end. billingDay=0 means calendar boundary; another day selects the billing month day.
   * billingMonths is 1, 3, 6 or 12, aligned to the calendar year.
   *
   * <p>
   * For valued modes the amount expression also sees exchangeCount, the number of distinct exchanges (MIC) of the
   * billing period on which a position was held or a trade was made, and positionCount, the positions observed. Only
   * exchanges for which exchangeCondition is true are counted; omitted means every exchange counts. This models a
   * per-exchange connectivity fee such as Degiro's.
   * </p>
   */
  public record Period(String validFrom, String validTo, Status status, String source, String note, String currency,
      Valuation valuation, Integer observationDay, Integer billingMonths, Collection collection, Integer billingDay,
      DayCount dayCount, String amount, List<FeeRule> valueRules, Double minimum, Double maximum, Double annualCap,
      Double vatRate, Double creditAmount, String creditEligibility, ClosingPolicy closingPolicy,
      String exchangeCondition) {
  }
}
