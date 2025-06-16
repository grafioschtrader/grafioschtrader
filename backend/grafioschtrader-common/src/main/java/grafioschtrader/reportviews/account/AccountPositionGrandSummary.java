package grafioschtrader.reportviews.account;

import java.util.ArrayList;
import java.util.List;

import grafiosch.common.DataHelper;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Grand summary of all cash and security accounts.
 */
public class AccountPositionGrandSummary {

  @Schema(description = "Currency of security, ISO 4217")
  public String mainCurrency;

  @Schema(description = "Total value in the main currency of all portfolios including securities")
  public double grandValueMC = 0.0;

  @Schema(description = "Total external cash transfers (deposits and withdrawals) to/from outside the portfolio system")
  public double grandCashBalanceMC = 0.0;
  
  @Schema(description = "Total external cash transfers (deposits and withdrawals) to/from outside the portfolio system")
  public double grandExternalCashTransferMC = 0.0;
  
  @Schema(description = "Total internal cash transfers between accounts")
  public double grandCashTransferMC = 0.0;
  
  @Schema(description = "Total transaction fees charged on cash account operations (deposits, withdrawals, transfers)")
  public double grandCashAccountTransactionFeeMC = 0.0;
  
  @Schema(description = "Total current market value of all security positions across all portfolios")
  public double grandValueSecuritiesMC = 0.0;
  
  @Schema(description = "Total realized and unrealized gains/losses on all security positions")
  public double grandGainLossSecuritiesMC = 0.0;
  
  @Schema(description = "Total foreign exchange gains/losses from currency fluctuations on multi-currency positions")
  public double grandGainLossCurrencyMC = 0.0;
  
  @Schema(description = "Total account management fees charged across all accounts")
  public double grandAccountFeesMC = 0.0;
  
  @Schema(description = "Total interest earned on cash account balances")
  public double grandAccountInterestMC = 0.0;

 

  @Schema(description = "Number of decimal places for monetary precision in the main currency")
  private int precisionMC;

  public List<AccountPositionGroupSummary> accountPositionGroupSummaryList = new ArrayList<>();

  public AccountPositionGrandSummary(String mainCurrency, int precisionMC) {
    this.mainCurrency = mainCurrency;
    this.precisionMC = precisionMC;
  }

  public void calcTotals(final DateTransactionCurrencypairMap dateTransactionCurrencypairMap) {
    for (AccountPositionGroupSummary accountPositionGroupSummary : accountPositionGroupSummaryList) {
      accountPositionGroupSummary.calcTotals(dateTransactionCurrencypairMap);
      grandExternalCashTransferMC += accountPositionGroupSummary.groupExternalCashTransferMC;
      grandCashTransferMC += accountPositionGroupSummary.groupCashTransferMC;
      grandCashAccountTransactionFeeMC += accountPositionGroupSummary.groupCashAccountTransactionFeeMC;
      grandValueMC += accountPositionGroupSummary.groupValueMC;
      grandCashBalanceMC += accountPositionGroupSummary.groupCashBalanceMC;
      grandValueSecuritiesMC += accountPositionGroupSummary.groupValueSecuritiesMC;
      grandGainLossSecuritiesMC += accountPositionGroupSummary.groupGainLossSecuritiesMC;
      grandGainLossCurrencyMC += accountPositionGroupSummary.groupGainLossCurrencyMC;
      grandAccountFeesMC += accountPositionGroupSummary.groupAccountFeesMC;
      grandAccountInterestMC += accountPositionGroupSummary.groupAccountInterestMC;
     
    }
  }

  public double getGrandCashAccountTransactionFeeMC() {
    return DataHelper.round(grandCashAccountTransactionFeeMC, precisionMC);
  }

  public double getGrandValueMC() {
    return DataHelper.round(grandValueMC, precisionMC);
  }

  public double getGrandCashBalanceMC() {
    return DataHelper.round(grandCashBalanceMC, precisionMC);
  }

  public double getGrandExternalCashTransferMC() {
    return DataHelper.round(grandExternalCashTransferMC, precisionMC);
  }

  public double getGrandCashTransferMC() {
    return DataHelper.round(grandCashTransferMC, precisionMC);
  }

  public double getGrandValueSecuritiesMC() {
    return DataHelper.round(grandValueSecuritiesMC, precisionMC);
  }

  public double getGrandGainLossSecuritiesMC() {
    return DataHelper.round(grandGainLossSecuritiesMC, precisionMC);
  }

  public double getGrandGainLossCurrencyMC() {
    return DataHelper.round(grandGainLossCurrencyMC, precisionMC);
  }

  public double getGrandAccountFeesMC() {
    return DataHelper.round(grandAccountFeesMC, precisionMC);
  }

  public double getGrandAccountInterestMC() {
    return DataHelper.round(grandAccountInterestMC, precisionMC);
  }
  

  public int getPrecisionMC() {
    return precisionMC;
  }

  public void setPrecisionMC(int precisionMC) {
    this.precisionMC = precisionMC;
  }

  public List<AccountPositionGroupSummary> getAccountPositionGroupSummaryList() {
    return accountPositionGroupSummaryList;
  }

  public void setAccountPositionGroupSummaryList(List<AccountPositionGroupSummary> accountPositionGroupSummaryList) {
    this.accountPositionGroupSummaryList = accountPositionGroupSummaryList;
  }

  @Override
  public String toString() {
    return "AccountPositionGrandSummary [mainCurrency=" + mainCurrency + ", grandValueMC=" + grandValueMC
        + ", grandCashBalanceMC=" + grandCashBalanceMC + ", grandExternalCashTransferMC=" + grandExternalCashTransferMC
        + ", grandCashTransferMC=" + grandCashTransferMC + ", grandCashAccountTransactionFeeMC="
        + grandCashAccountTransactionFeeMC + ", grandValueSecuritiesMC=" + grandValueSecuritiesMC
        + ", grandGainLossSecuritiesMC=" + grandGainLossSecuritiesMC + ", grandGainLossCurrencyMC="
        + grandGainLossCurrencyMC + ", grandAccountFeesMC=" + grandAccountFeesMC + ", grandAccountInterestMC="
        + grandAccountInterestMC + "]";
  }

}
