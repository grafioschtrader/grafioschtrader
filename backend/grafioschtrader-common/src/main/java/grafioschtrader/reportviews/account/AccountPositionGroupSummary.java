package grafioschtrader.reportviews.account;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import grafioschtrader.common.DataHelper;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Group summary of cash accounts which also incudes the value of securities.
 *
 * @author Hugo Graf
 *
 */
public class AccountPositionGroupSummary {

  public double groupAccountFeesMC;
  public double groupAccountInterestMC;
  public double groupAccountInterestLastCloseMC;
  public double groupExternalCashTransferMC = 0.0;
  public double groupCashTransferMC = 0.0;
  public double groupCashAccountTransactionFeeMC = 0.0;

  @Schema(description = "Total value in the main currency of a groupe including securities")
  public double groupValueMC = 0.0;

  public double groupCashBalanceMC = 0.0;
  public double groupValueSecuritiesMC = 0.0;
  public double groupGainLossSecuritiesMC = 0.0;
  public double groupGainLossCurrencyMC = 0.0;
  public String groupName;

  @Schema(description = "Currency of the group, is it grouped by portfolio then currency of portfolio, ISO 4217")
  public String currency;
  public List<CashaccountPositionSummary> accountPositionSummaryList = new ArrayList<>();

  private Map<String, Integer> currencyPrecisionMap;
  private int precisionMC;

  public AccountPositionGroupSummary(String groupName, String currency) {
    this.groupName = groupName;
    this.currency = currency;
  }

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

      double currencyExchangeRate = (accountPositionSummary.securitycurrency != null)
          ? accountPositionSummary.closePrice
          : 1.0;

      accountPositionSummary.calcTotals(currencyExchangeRate);

      groupAccountFeesMC += accountPositionSummary.accountFeesMC;
      groupAccountInterestMC += accountPositionSummary.accountInterestMC;

      groupAccountInterestLastCloseMC += accountPositionSummary.accountInterestLastCloseMC;

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

  public double getGroupAccountInterestLastCloseMC() {
    return DataHelper.round(groupAccountInterestLastCloseMC, precisionMC);
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
        + groupAccountInterestMC + ", groupAccountInterestLastCloseMC=" + groupAccountInterestLastCloseMC
        + ", groupExternalCashTransferMC=" + groupExternalCashTransferMC + ", groupCashTransferMC="
        + groupCashTransferMC + ", groupCashAccountTransactionFeeMC=" + groupCashAccountTransactionFeeMC
        + ", groupValueMC=" + groupValueMC + ", groupCashBalanceMC=" + groupCashBalanceMC + ", groupValueSecuritiesMC="
        + groupValueSecuritiesMC + ", groupGainLossSecuritiesMC=" + groupGainLossSecuritiesMC
        + ", groupGainLossCurrencyMC=" + groupGainLossCurrencyMC + ", groupName=" + groupName + ", currency=" + currency
        + ", accountPositionSummaryList=" + accountPositionSummaryList + ", currencyPrecisionMap="
        + currencyPrecisionMap + "]";
  }

}
