package grafioschtrader.reportviews.account;

import java.util.ArrayList;
import java.util.List;

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
//	public double groupAccountFeesLastCloseMC;
  public double groupAccountInterestLastCloseMC;
  public double groupExternalCashTransferMC = 0.0;
  public double groupCashTransferMC = 0.0;
  public double groupCashAccountTransactionFeeMC = 0.0;
  public double groupCashBalance = 0.0;

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

  public AccountPositionGroupSummary(String groupName, String currency) {
    this.groupName = groupName;
    this.currency = currency;
  }

  public void calcTotals(final DateTransactionCurrencypairMap dateTransactionCurrencypairMap) {
    for (CashaccountPositionSummary accountPositionSummary : accountPositionSummaryList) {
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

      // groupAccountFeesLastCloseMC += accountPositionSummary.accountFeesLastCloseMC;
      groupAccountInterestLastCloseMC += accountPositionSummary.accountInterestLastCloseMC;

      groupExternalCashTransferMC += accountPositionSummary.externalCashTransferMC;
      groupCashTransferMC += accountPositionSummary.cashTransferMC;
      groupCashAccountTransactionFeeMC += accountPositionSummary.cashAccountTransactionFeeMC;
      groupCashBalance += accountPositionSummary.cashBalance;
      groupGainLossCurrencyMC += accountPositionSummary.gainLossCurrencyMC;
      groupCashBalanceMC += accountPositionSummary.cashBalanceMC;
      groupValueMC += accountPositionSummary.valueMC;
      groupValueSecuritiesMC += accountPositionSummary.valueSecuritiesMC;
      groupGainLossSecuritiesMC += accountPositionSummary.gainLossSecuritiesMC;
    }

  }

  public double getGroupAccountFeesMC() {
    return DataHelper.round2(groupAccountFeesMC);
  }

  public double getGroupAccountInterestMC() {
    return DataHelper.round2(groupAccountInterestMC);
  }

  public double getGroupAccountInterestLastCloseMC() {
    return DataHelper.round2(groupAccountInterestLastCloseMC);
  }

  public double getGroupExternalCashTransferMC() {
    return DataHelper.round2(groupExternalCashTransferMC);
  }

  public double getGroupCashTransferMC() {
    return DataHelper.round2(groupCashTransferMC);
  }

  public double getGroupValueMC() {
    return DataHelper.round2(groupValueMC);
  }

  public double getGroupCashBalanceMC() {
    return DataHelper.round2(groupCashBalanceMC);
  }

  public double getGroupValueSecuritiesMC() {
    return DataHelper.round2(groupValueSecuritiesMC);
  }

  public double getGroupGainLossSecuritiesMC() {
    return DataHelper.round2(groupGainLossSecuritiesMC);
  }

  public double getGroupGainLossCurrencyMC() {
    return DataHelper.round2(groupGainLossCurrencyMC);
  }

  @Override
  public String toString() {
    return "AccountPositionGroupSummary [groupAccountFeesMainCurrency=" + groupAccountFeesMC
        + ", groupAccountInterestMainCurrency=" + groupAccountInterestMC + ", groupExternalTransferMC="
        + groupExternalCashTransferMC + ", groupBalance=" + groupCashBalance + ", groupValueMainCurrency="
        + groupValueMC + ", groupSaldoMainCurrency=" + groupCashBalanceMC + ", groupValueSecuritiesMainCurrency="
        + groupValueSecuritiesMC + ", groupGainLossMainCurrencySecurities=" + groupGainLossSecuritiesMC
        + ", groupGainLossCurrency=" + groupGainLossCurrencyMC + ", groupName=" + groupName + ", currency=" + currency
        + "]";
  }

}
