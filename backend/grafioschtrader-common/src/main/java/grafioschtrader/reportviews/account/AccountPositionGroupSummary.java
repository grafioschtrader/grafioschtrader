package grafioschtrader.reportviews.account;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import grafiosch.common.DataHelper;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Grouped summary of cash accounts and their associated security positions, aggregated by
    specified criteria (currency, portfolio, asset class, etc.) with all values converted to main currency""")
public class AccountPositionGroupSummary {

  @Schema(description = "Total account management fees charged within this group")
  public double groupAccountFeesMC;

  @Schema(description = "Total interest earned on cash accounts within this group")
  public double groupAccountInterestMC;

  @Schema(description = "Total external cash transfers (deposits and withdrawals from outside the system) within this group")
  public double groupExternalCashTransferMC = 0.0;

  @Schema(description = "Total internal cash transfers between accounts within this group")
  public double groupCashTransferMC = 0.0;

  @Schema(description = "Total transaction fees on cash account operations (deposits, withdrawals, transfers) within this group")
  public double groupCashAccountTransactionFeeMC = 0.0;

  @Schema(description = "Total combined value (cash + securities) of all accounts in this group converted to main currency")
  public double groupValueMC = 0.0;

  @Schema(description = "Total cash balance across all cash accounts in this group converted to main currency")
  public double groupCashBalanceMC = 0.0;

  @Schema(description = "Total current market value of all security positions within this group")
  public double groupValueSecuritiesMC = 0.0;

  @Schema(description = "Total realized and unrealized gains/losses on security positions within this group")
  public double groupGainLossSecuritiesMC = 0.0;

  @Schema(description = "Total foreign exchange gains/losses from currency fluctuations within this group")
  public double groupGainLossCurrencyMC = 0.0;

  @Schema(description = "Total excluded dividend tax within this group in main currency")
  public double groupExcludedDivTaxMC = 0.0;

  @Schema(description = "Whether dividend tax exclusion is enabled for the tenant")
  public boolean excludeDivTax;

  @Schema(description = "Descriptive name identifying this group (e.g., portfolio name, currency code, asset class)")
  public String groupName;

  @Schema(description = "Primary currency of this group (ISO 4217). When grouped by portfolio, this is the portfolio's base currency")
  public String currency;

  @Schema(description = "Individual cash account position summaries that comprise this group total")
  public List<CashaccountPositionSummary> accountPositionSummaryList = new ArrayList<>();

  @Schema(description = """
      Currencies of this group that could not be converted into the main currency because no exchange rate was
      available. The corresponding cash accounts are still listed with their own balance, but they contribute nothing
      to any of the group totals above, so those totals are incomplete whenever this list is not empty.""")
  public List<MissingExchangeRate> missingExchangeRates = new ArrayList<>();

  /**
   * Currency precision configuration map that defines the number of decimal places for each currency code. Used to
   * ensure proper rounding of monetary values according to the standard precision requirements of each currency.
   * Initialized from the first processed account position summary.
   */
  private Map<String, Integer> currencyPrecisionMap;

  /**
   * Number of decimal places for monetary precision in the main currency. Determines how all main currency values are
   * rounded for display and reporting. Inherited from the currency precision map based on the main currency code.
   */
  private int precisionMC;

  /**
   * Creates a new group summary with the specified group name and currency.
   * 
   * @param groupName descriptive name for this group
   * @param currency  the primary currency for this group (ISO 4217)
   */
  public AccountPositionGroupSummary(String groupName, String currency) {
    this.groupName = groupName;
    this.currency = currency;
  }

  /**
   * Calculates and aggregates totals from all individual account position summaries within this group. Applies the
   * exchange rate of the reporting date for foreign currency conversions and updates all group totals.
   *
   * <p>
   * An exchange rate of one is applied only to an account that is already denominated in the main currency. An account
   * in a foreign currency whose rate could not be determined is recorded in {@link #missingExchangeRates} and skipped:
   * it keeps its own balance for display but contributes to none of the group totals. Converting it at a substitute
   * rate would silently produce a result that is wrong by orders of magnitude.
   * </p>
   *
   * @param dateTransactionCurrencypairMap currency exchange rate context for FX calculations
   */
  public void calcTotals(final DateTransactionCurrencypairMap dateTransactionCurrencypairMap) {
    for (CashaccountPositionSummary accountPositionSummary : accountPositionSummaryList) {
      if (currencyPrecisionMap == null) {
        currencyPrecisionMap = accountPositionSummary.currencyPrecisionMap;
        precisionMC = accountPositionSummary.precisionMC;
      }

      // For a reporting date in the past only the rate of that very day is acceptable. Falling back to the latest
      // known rate would value a historical position with today's rate, which is why a failed lookup marks the
      // account even when a newer rate happens to be at hand.
      if (accountPositionSummary.securitycurrency != null
          && !dateTransactionCurrencypairMap.isUntilDateEqualNowOrAfterOrInActualWeekend()) {
        Double exactClosePrice = dateTransactionCurrencypairMap.getExactDateAndFromCurrency(
            dateTransactionCurrencypairMap.getUntilDate(), accountPositionSummary.securitycurrency.getFromCurrency());
        if (exactClosePrice == null) {
          accountPositionSummary.priceMissing = true;
        } else {
          accountPositionSummary.closePrice = exactClosePrice;
        }
      }

      // A foreign currency without a currency pair at all never reaches the pricing step, so it arrives here with a
      // null closePrice; it is as unconvertible as a pair that carries no quote for the date. The flag is never
      // cleared: one unavailable rate, be it of the reporting date or of a transaction date, is enough to make the
      // main currency amounts of this account wrong.
      if (accountPositionSummary.securitycurrency != null && accountPositionSummary.closePrice == null) {
        accountPositionSummary.priceMissing = true;
      }

      if (accountPositionSummary.priceMissing) {
        accountPositionSummary.closePrice = null;
        missingExchangeRates.add(new MissingExchangeRate(accountPositionSummary.getCashaccount().getCurrency(),
            dateTransactionCurrencypairMap.getMainCurrency(), dateTransactionCurrencypairMap.getUntilDate(),
            accountPositionSummary.securitycurrency == null ? null
                : accountPositionSummary.securitycurrency.getIdSecuritycurrency()));
        continue;
      }

      double currencyExchangeRate = accountPositionSummary.securitycurrency != null
          ? accountPositionSummary.closePrice : 1.0;

      accountPositionSummary.calcTotals(currencyExchangeRate);
      groupAccountFeesMC += accountPositionSummary.accountFeesMC;
      groupAccountInterestMC += accountPositionSummary.accountInterestMC;
      groupExternalCashTransferMC += accountPositionSummary.externalCashTransferMC;
      groupCashTransferMC += accountPositionSummary.cashTransferMC;
      groupCashAccountTransactionFeeMC += accountPositionSummary.cashAccountTransactionFeeMC;
      groupGainLossCurrencyMC += accountPositionSummary.gainLossCurrencyMC;
      groupCashBalanceMC += accountPositionSummary.cashBalanceMC;
      groupValueMC += accountPositionSummary.valueMC;
      groupValueSecuritiesMC += accountPositionSummary.valueSecuritiesMC;
      groupGainLossSecuritiesMC += accountPositionSummary.gainLossSecuritiesMC;
      groupExcludedDivTaxMC += accountPositionSummary.excludedDivTaxMC;
    }
  }

  public double getGroupAccountFeesMC() {
    return DataHelper.round(groupAccountFeesMC, precisionMC);
  }

  public double getGroupAccountInterestMC() {
    return DataHelper.round(groupAccountInterestMC, precisionMC);
  }

  public double getGroupCashAccountTransactionFeeMC() {
    return DataHelper.round(groupCashAccountTransactionFeeMC, precisionMC);
  }

  public double getGroupExternalCashTransferMC() {
    return DataHelper.round(groupExternalCashTransferMC, precisionMC);
  }

  public double getGroupCashTransferMC() {
    return DataHelper.round(groupCashTransferMC, precisionMC);
  }

  public double getGroupValueMC() {
    return DataHelper.round(groupValueMC, precisionMC);
  }

  public double getGroupCashBalanceMC() {
    return DataHelper.round(groupCashBalanceMC, precisionMC);
  }

  public double getGroupValueSecuritiesMC() {
    return DataHelper.round(groupValueSecuritiesMC, precisionMC);
  }

  public double getGroupGainLossSecuritiesMC() {
    return DataHelper.round(groupGainLossSecuritiesMC, precisionMC);
  }

  public double getGroupGainLossCurrencyMC() {
    return DataHelper.round(groupGainLossCurrencyMC, precisionMC);
  }

  public double getGroupExcludedDivTaxMC() {
    return DataHelper.round(groupExcludedDivTaxMC, precisionMC);
  }

  @Override
  public String toString() {
    return "AccountPositionGroupSummary [groupAccountFeesMC=" + groupAccountFeesMC + ", groupAccountInterestMC="
        + groupAccountInterestMC + ", groupExternalCashTransferMC=" + groupExternalCashTransferMC
        + ", groupCashTransferMC=" + groupCashTransferMC + ", groupCashAccountTransactionFeeMC="
        + groupCashAccountTransactionFeeMC + ", groupValueMC=" + groupValueMC + ", groupCashBalanceMC="
        + groupCashBalanceMC + ", groupValueSecuritiesMC=" + groupValueSecuritiesMC + ", groupGainLossSecuritiesMC="
        + groupGainLossSecuritiesMC + ", groupGainLossCurrencyMC=" + groupGainLossCurrencyMC + ", groupName="
        + groupName + ", currency=" + currency + ", accountPositionSummaryList=" + accountPositionSummaryList
        + ", currencyPrecisionMap=" + currencyPrecisionMap + "]";
  }

}
