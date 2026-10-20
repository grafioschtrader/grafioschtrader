package grafioschtrader.mcp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import grafioschtrader.mcp.McpToolExecutor.RowCount;
import grafioschtrader.reportviews.performance.PerformancePeriodMetrics;

/**
 * The compact answers of the reading tools. The tools never hand out the report objects themselves: those carry
 * entities, notes and internal fields, and their shape is made for the user interface. Amounts are the rounded values
 * of the reports and come with their currency; ids are included so that a follow-up call can go into detail.
 *
 * <p>
 * Every text - names of instruments, accounts and portfolios - is a JSON value limited to 100 characters. Notes of
 * transactions and accounts, values of user-defined fields and GTNet texts are never part of an answer.
 * </p>
 */
public final class McpToolResults {

  private McpToolResults() {
  }

  /** Paging state of a list answer. {@code nextOffset} is null on the last page. */
  public record Page(int totalCount, int offset, int limit, Integer nextOffset, String orderBy) {
  }

  // ---------------------------------------------------------------------------------------------------------------
  // list_portfolios
  // ---------------------------------------------------------------------------------------------------------------

  public record PortfolioList(String tenantCurrency, List<PortfolioItem> portfolios) implements RowCount {
    @Override
    public int rowCount() {
      return portfolios.size();
    }
  }

  public record PortfolioItem(Integer idPortfolio, String name, String currency,
      List<SecurityaccountItem> securityaccounts, List<CashaccountItem> cashaccounts) {
  }

  public record SecurityaccountItem(Integer idSecurityaccount, String name) {
  }

  public record CashaccountItem(Integer idCashaccount, String name, String currency) {
  }

  // ---------------------------------------------------------------------------------------------------------------
  // account_summary
  // ---------------------------------------------------------------------------------------------------------------

  public record AccountSummary(LocalDate asOfDate, String timeZone, String mainCurrency, String groupBy,
      AccountTotals totals, List<AccountGroup> groups, boolean valuationComplete, List<String> missingExchangeRates)
      implements RowCount {
    @Override
    public int rowCount() {
      return groups.stream().mapToInt(g -> g.accounts().size()).sum();
    }
  }

  public record AccountTotals(double valueMC, double cashBalanceMC, double valueSecuritiesMC,
      double gainLossSecuritiesMC, double gainLossCurrencyMC, double accountFeesMC, double accountInterestMC,
      double externalCashTransferMC, double cashTransferMC) {
  }

  public record AccountGroup(String groupName, String currency, AccountTotals totals, List<AccountRow> accounts) {
  }

  public record AccountRow(Integer idCashaccount, String name, String currency, double cashBalance,
      double cashBalanceMC, double valueSecuritiesMC, double gainLossSecuritiesMC, double gainLossCurrencyMC,
      double valueMC) {
  }

  // ---------------------------------------------------------------------------------------------------------------
  // holdings
  // ---------------------------------------------------------------------------------------------------------------

  public record Holdings(LocalDate asOfDate, String timeZone, String mainCurrency, String groupBy, Integer idPortfolio,
      boolean includeClosedPositions, HoldingTotals totalsOfAllPositions, List<HoldingGroup> groupTotals,
      List<HoldingRow> positions, Page page) implements RowCount {
    @Override
    public int rowCount() {
      return positions.size();
    }
  }

  public record HoldingTotals(double valueMC, double gainLossSecurityMC, double gainLossCurrencyMC,
      boolean valuationComplete) {
  }

  public record HoldingGroup(String group, String groupLabel, int positionCount, double valueMC,
      double gainLossSecurityMC, double gainLossCurrencyMC, Double shareOfTotalPercentage, boolean valuationComplete) {
  }

  public record HoldingRow(Integer idSecuritycurrency, String name, String isin, String currency, String group,
      double units, Double closePrice, LocalDate closeDate, String closePriceOrigin, boolean priceMissing,
      double valueSecurity, double valueMC, double gainLossSecurity, double gainLossSecurityMC,
      double gainLossCurrencyMC, Double shareOfTotalPercentage) {
  }

  // ---------------------------------------------------------------------------------------------------------------
  // period_performance
  // ---------------------------------------------------------------------------------------------------------------

  public record PeriodPerformance(LocalDate dateFrom, LocalDate dateTo, String timeZone, String mainCurrency,
      Integer idPortfolio, String periodSplit, PeriodTotals firstDay, PeriodTotals lastDay, PeriodTotals difference,
      PerformancePeriodMetrics metrics, List<PeriodWindowRow> periods) implements RowCount {
    @Override
    public int rowCount() {
      return periods.size();
    }
  }

  public record PeriodTotals(LocalDate date, double totalBalanceMC, double cashBalanceMC, double securitiesMC,
      double externalCashTransferMC, double gainMC, double marginCloseGainMC, double totalGainMC,
      double dividendRealMC, double interestCashaccountRealMC, double feeRealMC, double accumulateReduceMC) {
  }

  public record PeriodWindowRow(LocalDate startDate, LocalDate endDate, Double gainPeriodMC, Double twrPercent) {
  }

  // ---------------------------------------------------------------------------------------------------------------
  // dividends
  // ---------------------------------------------------------------------------------------------------------------

  public record Dividends(String mainCurrency, Integer yearFrom, Integer yearTo, List<Integer> idsSecurityaccount,
      List<Integer> idsCashaccount, List<DividendYear> years, List<DividendAssetclass> byAssetclass,
      DividendTotals totalsOfSelectedYears) implements RowCount {
    @Override
    public int rowCount() {
      return years.size();
    }
  }

  public record DividendYear(int year, double dividendNetMC, double dividendTaxMC, double interestNetMC,
      double interestTaxMC, double cashInterestMC, double financeCostCfdMC, double financeCostForexMC, double feeMC,
      double netIncomeMC, double[] netIncomeMonthMC, double[] taxMonthMC) {
  }

  public record DividendAssetclass(int year, int idAssetclass, String category, String subCategory, double netMC,
      double taxMC) {
  }

  public record DividendTotals(double dividendNetMC, double dividendTaxMC, double interestNetMC, double cashInterestMC,
      double feeMC, double netIncomeMC) {
  }

  // ---------------------------------------------------------------------------------------------------------------
  // security_transactions
  // ---------------------------------------------------------------------------------------------------------------

  public record SecurityTransactions(LocalDate untilDate, String timeZone, String mainCurrency,
      InstrumentItem security, SecurityPositionSummaryRow positionSummary, List<SecurityTransactionRow> transactions,
      Page page) implements RowCount {
    @Override
    public int rowCount() {
      return transactions.size();
    }
  }

  public record SecurityPositionSummaryRow(double units, Double closePrice, LocalDate closeDate, boolean priceMissing,
      double valueSecurity, double valueMC, double gainLossSecurity, double gainLossSecurityMC,
      double gainLossCurrencyMC, double transactionCost, double taxCost) {
  }

  public record SecurityTransactionRow(Integer idTransaction, LocalDateTime transactionTime, String transactionType,
      Double units, Double quotation, Double cashaccountAmount, String cashaccountCurrency, Double currencyExRate,
      Double transactionCost, Double taxCost, Integer idSecurityaccount, Integer idCashaccount,
      Double transactionGainLoss, Double transactionGainLossMC, Double transactionGainLossCurrencyMC) {
  }

  // ---------------------------------------------------------------------------------------------------------------
  // cashaccount_transactions
  // ---------------------------------------------------------------------------------------------------------------

  public record CashaccountTransactions(Integer idCashaccount, String name, String currency, int year,
      List<String> transactionTypes, double sumOfSelection, List<CashaccountTransactionRow> transactions, Page page)
      implements RowCount {
    @Override
    public int rowCount() {
      return transactions.size();
    }
  }

  public record CashaccountTransactionRow(Integer idTransaction, LocalDateTime transactionTime,
      String transactionType, double cashaccountAmount, double runningSumOfSelection, Integer idSecuritycurrency,
      String securityName, Integer idSecurityaccount, Integer connectedIdTransaction) {
  }

  // ---------------------------------------------------------------------------------------------------------------
  // find_instrument
  // ---------------------------------------------------------------------------------------------------------------

  public record InstrumentItem(Integer idSecuritycurrency, String name, String isin, String tickerSymbol,
      String currency, String stockexchangeMic, String assetclassCategory, LocalDate activeToDate,
      boolean privateOfTenant) {
  }

  public record CurrencypairItem(Integer idSecuritycurrency, String fromCurrency, String toCurrency) {
  }

  public record InstrumentSearch(String kind, String text, List<InstrumentItem> securities,
      List<CurrencypairItem> currencypairs, boolean moreAvailable, String hint) implements RowCount {
    @Override
    public int rowCount() {
      return securities.size() + currencypairs.size();
    }
  }
}
