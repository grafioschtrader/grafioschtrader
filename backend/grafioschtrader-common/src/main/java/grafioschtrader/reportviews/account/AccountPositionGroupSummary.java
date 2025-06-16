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

  @Schema(description = "Descriptive name identifying this group (e.g., portfolio name, currency code, asset class)")
  public String groupName;

  @Schema(description = "Primary currency of this group (ISO 4217). When grouped by portfolio, this is the portfolio's base currency")
  public String currency;

  @Schema(description = "Individual cash account position summaries that comprise this group total")
  public List<CashaccountPositionSummary> accountPositionSummaryList = new ArrayList<>();

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
   * Calculates and aggregates totals from all individual account position summaries within this group. Applies current
   * exchange rates for foreign currency conversions and updates all group totals.
   * 
   * @param dateTransactionCurrencypairMap currency exchange rate context for FX calculations
   */
  public void calcTotals(final DateTransactionCurrencypairMap dateTransactionCurrencypairMap) {
    for (CashaccountPositionSummary accountPositionSummary : accountPositionSummaryList) {
      if (currencyPrecisionMap == null) {
        currencyPrecisionMap = accountPositionSummary.currencyPrecisionMap;
        precisionMC = accountPositionSummary.precisionMC;
      }

      if (accountPositionSummary.securitycurrency != null
          && !dateTransactionCurrencypairMap.isUntilDateEqualNowOrAfterOrInActualWeekend()) {
        accountPositionSummary.closePrice = dateTransactionCurrencypairMap.getExactDateAndFromCurrency(
            dateTransactionCurrencypairMap.getUntilDate(), accountPositionSummary.securitycurrency.getFromCurrency());
      }

      double currencyExchangeRate = (accountPositionSummary.securitycurrency != null
          && accountPositionSummary.closePrice != null) ? accountPositionSummary.closePrice : 1.0;

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
