package grafioschtrader.reportviews.account;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.reportviews.SecuritycurrencyPositionSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionCurrenyGroupSummary;

/**
 * Total per cash account
 *
 * @author Hugo Graf
 *
 */
public class CashaccountPositionSummary extends SecuritycurrencyPositionSummary<Currencypair> {

  public boolean hasTransaction;

  /**
   * Fees for managing depot and cash account without any transaction costs.
   */
  public double accountFeesMC;

  public double accountInterestMC;

  // public double accountFeesLastCloseMC;

  public double accountInterestLastCloseMC;

  public double cashTransferMC;

  /**
   * It counts the inflow and outflow to this cash account from other accounts
   * from other portfolios. It assumes that inflow and outflow happened always in
   * the currency of this cash account. This way cash transfer between cash
   * accounts with different currencies are not taken in account.
   */
  public double externalCashTransferMC;

  /**
   * Total in base or target currency
   */
  public double cashBalance;

  /**
   * Amount of currency transaction, buy, sell, deposit, withdrawal
   */
  public double balanceCurrencyTransaction;

  /**
   * Amount of currency in the target Currency (main currency of portfolio or
   * tenant currency). It is the balance of this cash account but in the target
   * currency.
   */
  public double balanceCurrencyTransactionMC;

  /**
   * Loss or gain in target currency in main currency
   */
  public double gainLossCurrencyMC;

  /**
   * Cost for cash account transfer
   */
  public double cashAccountTransactionFeeMC;

  /**
   * Balance target Currency
   */
  public double cashBalanceMC;

  /**
   * Gain/Loss from Security for this currency
   */
  public double gainLossSecurities;

  /**
   * Amount from Security for this currency
   */
  public double valueSecurities;

  /**
   * Gain/Loss of Securities in main currency
   */
  public double gainLossSecuritiesMC;

  /**
   * Amount of Securities in main currency
   */
  public double valueSecuritiesMC;

  /**
   * Amount saldoMainCurrency + valueSecuritiesMC
   */
  public double valueMC;

  private Cashaccount cashaccount;

  @JsonIgnore
  public Map<String, Integer> currencyPrecisionMap;
  @JsonIgnore
  public int precisionMC;
  private int precision;

  public CashaccountPositionSummary(Map<String, Integer> currencyPrecisionMap) {
    this.currencyPrecisionMap = currencyPrecisionMap;
  }

  public double getAccountFeesMC() {
    return DataHelper.round(accountFeesMC, precisionMC);
  }

  public double getAccountInterestMC() {
    return DataHelper.round(accountInterestMC, precisionMC);
  }

  public double getAccountInterestLastCloseMC() {
    return DataHelper.round(accountInterestLastCloseMC, precisionMC);
  }

  public double getCashAccountTransactionFeeMC() {
    return DataHelper.round(cashAccountTransactionFeeMC, precisionMC);
  }

  public double getCashTransferMC() {
    return DataHelper.round(cashTransferMC, precisionMC);
  }

  public double getExternalCashTransferMC() {
    return DataHelper.round(externalCashTransferMC, precisionMC);
  }

  public double getGainLossCurrencyMC() {
    return DataHelper.round(gainLossCurrencyMC, precisionMC);
  }

  public double getCashBalanceMC() {
    return DataHelper.round(cashBalanceMC, precisionMC);
  }

  public double getGainLossSecurities() {
    return DataHelper.roundStandard(gainLossSecurities);
  }

  public double getValueSecurities() {
    return DataHelper.roundStandard(valueSecurities);
  }

  public double getGainLossSecuritiesMC() {
    return DataHelper.roundStandard(gainLossSecuritiesMC);
  }

  public double getValueSecuritiesMC() {
    return DataHelper.round(valueSecuritiesMC, precisionMC);
  }

  public double getValueMC() {
    return DataHelper.round(valueMC, precisionMC);
  }

  public double getCashBalance() {
    return DataHelper.round(cashBalance, precision);
  }

  public Cashaccount getCashaccount() {
    return cashaccount;
  }

  public void setCashaccount(Cashaccount cashaccount) {
    this.precision = this.currencyPrecisionMap.getOrDefault(cashaccount.getCurrency(),
        GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
    this.precisionMC = this.currencyPrecisionMap.getOrDefault(cashaccount.getPortfolio().getCurrency(),
        GlobalConstants.FID_STANDARD_FRACTION_DIGITS);

    this.cashaccount = cashaccount;
  }

  public void setSecuritiesValue(SecurityPositionCurrenyGroupSummary securityPositionCurrenyGroupSummary) {
    if (securityPositionCurrenyGroupSummary != null) {
      this.valueSecurities = securityPositionCurrenyGroupSummary.groupAccountValueSecurity;
      this.gainLossSecurities = securityPositionCurrenyGroupSummary.groupGainLossSecurity;
      this.gainLossSecuritiesMC = securityPositionCurrenyGroupSummary.groupGainLossSecurityMC;
      this.valueSecuritiesMC = securityPositionCurrenyGroupSummary.groupAccountValueSecurityMC;

    }
  }

  public void calcTotals(double currencyExchangeRate) {
    // accountFeesLastCloseMC = accountFeesLastCloseMC * -1.0 *
    // currencyExchangeRate;
    accountInterestLastCloseMC = accountInterestLastCloseMC * currencyExchangeRate;
    cashBalanceMC = DataHelper.round(cashBalance * currencyExchangeRate, GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
    this.valueMC = cashBalanceMC + valueSecuritiesMC;
  }

  @Override
  public String toString() {
    return "CashaccountPositionSummary [balanceCurrencyTransaction=" + balanceCurrencyTransaction
        + ", balanceCurrencyTransactionMC=" + balanceCurrencyTransactionMC + "]";
  }

}
