package grafioschtrader.reportviews.account;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.DataHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.reportviews.SecuritycurrencyPositionSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionCurrenyGroupSummary;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Comprehensive position summary for a single cash account including cash balances, associated security holdings,
 * currency exchange impacts, and transaction costs. Combines all financial activities and positions related to one cash
 * account.
 */
@Schema(description = """
    Complete financial position summary for an individual cash account, including cash balances, security holdings,
    currency exchange effects, fees, and transfers""")
public class CashaccountPositionSummary extends SecuritycurrencyPositionSummary<Currencypair> {

  @Schema(description = "Indicates whether this cash account has any transaction history")
  public boolean hasTransaction;

  @Schema(description = """
      Account management fees charged for maintaining the cash account and associated depot,
      excluding transaction-specific costs""")
  public double accountFeesMC;

  @Schema(description = "Interest earned on cash balances held in this account")
  public double accountInterestMC;

  @Schema(description = "Total cash transfers between accounts within the same portfolio or tenant system")
  public double cashTransferMC;

  @Schema(description = """
      External cash flows (deposits and withdrawals) to/from this account from outside sources,
      always recorded in the account's base currency to avoid cross-currency transfer complications""")
  public double externalCashTransferMC;

  @Schema(description = "Current cash balance in the account's base currency")
  public double cashBalance;

  @Schema(description = "Net amount of currency transactions (buy, sell, deposit, withdrawal) affecting foreign exchange positions")
  public double balanceCurrencyTransaction;

  @Schema(description = "Currency transaction balance converted to the main currency (portfolio or tenant currency)")
  public double balanceCurrencyTransactionMC;

  @Schema(description = "Foreign exchange gains or losses from currency fluctuations, calculated in main currency")
  public double gainLossCurrencyMC;

  @Schema(description = "Transaction fees specifically charged for cash account operations (transfers, deposits, withdrawals)")
  public double cashAccountTransactionFeeMC;

  @Schema(description = "Cash balance converted to the main currency using current exchange rates")
  public double cashBalanceMC;

  @Schema(description = "Realized and unrealized gains/losses from securities held in this account's currency")
  public double gainLossSecurities;

  @Schema(description = "Current market value of securities associated with this cash account in the account's currency")
  public double valueSecurities;

  @Schema(description = "Security gains/losses converted to main currency")
  public double gainLossSecuritiesMC;

  @Schema(description = "Current market value of securities converted to main currency")
  public double valueSecuritiesMC;

  @Schema(description = "Total value combining cash balance and securities value in main currency")
  public double valueMC;

  /**
   * The cash account entity that this position summary represents. Contains the account details, currency, portfolio
   * association, and transaction history.
   */
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
    return DataBusinessHelper.roundStandard(gainLossSecurities);
  }

  public double getValueSecurities() {
    return DataBusinessHelper.roundStandard(valueSecurities);
  }

  public double getGainLossSecuritiesMC() {
    return DataBusinessHelper.roundStandard(gainLossSecuritiesMC);
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

  /**
   * Associates security position values with this cash account summary. Links the security holdings to the appropriate
   * cash account for comprehensive reporting.
   * 
   * @param securityPositionCurrenyGroupSummary security position summary for the same currency/account
   */
  public void setSecuritiesValue(SecurityPositionCurrenyGroupSummary securityPositionCurrenyGroupSummary) {
    if (securityPositionCurrenyGroupSummary != null) {
      this.valueSecurities = securityPositionCurrenyGroupSummary.groupAccountValueSecurity;
      this.gainLossSecurities = securityPositionCurrenyGroupSummary.groupGainLossSecurity;
      this.gainLossSecuritiesMC = securityPositionCurrenyGroupSummary.groupGainLossSecurityMC;
      this.valueSecuritiesMC = securityPositionCurrenyGroupSummary.groupAccountValueSecurityMC;
    }
  }

  /**
   * Calculates final totals applying the current currency exchange rate.
   * Converts cash balance to main currency and computes total account value.
   * 
   * @param currencyExchangeRate current exchange rate from account currency to main currency
   */
  public void calcTotals(double currencyExchangeRate) {
    cashBalanceMC = DataHelper.round(cashBalance * currencyExchangeRate, GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
    this.valueMC = cashBalanceMC + valueSecuritiesMC;
  }

  @Override
  public String toString() {
    return "CashaccountPositionSummary [balanceCurrencyTransaction=" + balanceCurrencyTransaction
        + ", balanceCurrencyTransactionMC=" + balanceCurrencyTransactionMC + "]";
  }

}
