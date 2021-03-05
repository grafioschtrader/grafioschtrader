package grafioschtrader.reportviews.account;

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

  public Cashaccount cashaccount;

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

  public double getAccountFeesMC() {
    return DataHelper.round2(accountFeesMC);
  }

  public double getAccountInterestMC() {
    return DataHelper.round2(accountInterestMC);
  }

  public double getAccountInterestLastCloseMC() {
    return DataHelper.round2(accountInterestLastCloseMC);
  }

  public double getCashTransferMC() {
    return DataHelper.round2(cashTransferMC);
  }

  public double getExternalCashTransferMC() {
    return DataHelper.round2(externalCashTransferMC);
  }

  public double getGainLossCurrencyMC() {
    return DataHelper.round2(gainLossCurrencyMC);
  }

  public double getCashBalanceMC() {
    return DataHelper.round2(cashBalanceMC);
  }

  public double getGainLossSecurities() {
    return DataHelper.round2(gainLossSecurities);
  }

  public double getValueSecurities() {
    return DataHelper.round2(valueSecurities);
  }

  public double getGainLossSecuritiesMC() {
    return DataHelper.round2(gainLossSecuritiesMC);
  }

  public double getValueSecuritiesMC() {
    return DataHelper.round2(valueSecuritiesMC);
  }

  public double getValueMC() {
    return DataHelper.round2(valueMC);
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
    cashBalanceMC = DataHelper.round(cashBalance * currencyExchangeRate, 2);
    this.valueMC = cashBalanceMC + valueSecuritiesMC;
  }

  @Override
  public String toString() {
    return "CashaccountPositionSummary [balanceCurrencyTransaction=" + balanceCurrencyTransaction
        + ", balanceCurrencyTransactionMC=" + balanceCurrencyTransactionMC + "]";
  }

 

  
  
  
}
