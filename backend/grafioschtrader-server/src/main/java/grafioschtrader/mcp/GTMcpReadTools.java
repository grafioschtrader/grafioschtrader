package grafioschtrader.mcp;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

/**
 * The reading tools of the MCP server. Each method only names the tool, describes it for the model and hands its body
 * to {@link McpToolExecutor}, which applies the limits and turns the outcome into a tool result. The work is done by
 * {@link McpReadToolService}.
 *
 * <p>
 * The descriptions are part of the contract with the model: they name the fields, the currency, the scaling of
 * percentages and the meaning of the date, because a model cannot ask what a number means.
 * </p>
 */
@Component
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class GTMcpReadTools {

  private static final String DATE_RULE = """
      Dates are yyyy-MM-dd. When untilDate is omitted, today in the client's time zone (header x-time-zone, otherwise \
      UTC) is used; the answer names the resolved date and zone.""";

  private static final String PAGING_RULE = """
      Paging: limit (default 50, at most 500) and offset (default 0); page.nextOffset is null on the last page and \
      page.totalCount counts the whole selection.""";

  private static final String MC_RULE = """
      Fields ending in MC are amounts in the main currency (mainCurrency); amounts without MC are in the currency of \
      the instrument or account. Percentages are percentage points (12.5 means 12.5 %).""";

  private final McpToolExecutor executor;
  private final McpReadToolService service;

  public GTMcpReadTools(McpToolExecutor executor, McpReadToolService service) {
    this.executor = executor;
    this.service = service;
  }

  @McpTool(name = "list_portfolios", description = """
      Lists the portfolios of the user's own tenant with their security accounts and cash accounts, including the ids \
      other tools take and the currencies. tenantCurrency is the main currency of the tenant. Call this first to learn \
      the ids.""", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
  public CallToolResult listPortfolios() {
    return executor.execute("list_portfolios", false, service::listPortfolios);
  }

  @McpTool(name = "account_summary", description = """
      Values of all portfolios at a date, grouped by portfolio or by currency: per cash account the cash balance, the \
      value and gain/loss of the securities booked against it, and gainLossCurrencyMC, the currency result of the cash \
      account. totals sum the whole tenant. valuationComplete is false when an exchange rate is missing \
      (missingExchangeRates). """ + MC_RULE + " " + DATE_RULE, annotations = @McpAnnotations(readOnlyHint = true,
      destructiveHint = false, openWorldHint = false))
  public CallToolResult accountSummary(
      @McpToolParam(required = false, description = "Valuation date yyyy-MM-dd, default today") String untilDate,
      @McpToolParam(required = false, description = "portfolio (default) or currency") String groupBy) {
    return executor.execute("account_summary", true, caller -> service.accountSummary(caller, untilDate, groupBy));
  }

  @McpTool(name = "holdings", description = """
      Security positions at a date, one row per position, with units, closePrice and closeDate, value (valueSecurity \
      in the instrument currency, valueMC), gainLossSecurityMC and gainLossCurrencyMC. gainLossCurrencyMC is the \
      cumulative currency result of the position since it was opened, valued at the date; a value for a calendar year \
      cannot be derived from it, not even as the difference of two dates when positions were opened or closed in \
      between. shareOfTotalPercentage is the share of the position in the total. priceMissing true means no price or \
      exchange rate was found and the row is valued with a substitute; totals containing such a row carry \
      valuationComplete false. groupBy adds the column group and the separate groupTotals, it does not merge \
      positions. totalsOfAllPositions sum all positions, not the page. orderBy: valueMC, gainLossMC, \
      gainLossCurrencyMC, shareOfTotalPercentage or name, optionally followed by :asc or :desc (default valueMC:desc). \
      """ + MC_RULE + " " + DATE_RULE + " " + PAGING_RULE, annotations = @McpAnnotations(readOnlyHint = true,
      destructiveHint = false, openWorldHint = false))
  public CallToolResult holdings(
      @McpToolParam(required = false, description = "Valuation date yyyy-MM-dd, default today") String untilDate,
      @McpToolParam(required = false, description = "Include positions closed before the date, default false") Boolean includeClosedPositions,
      @McpToolParam(required = false, description = "currency (default), assetclasstype, specialinvestmentinstrument or assetclass") String groupBy,
      @McpToolParam(required = false, description = "Restrict to one portfolio; its currency becomes the main currency") Integer idPortfolio,
      @McpToolParam(required = false, description = "Sort field with optional :asc or :desc") String orderBy,
      @McpToolParam(required = false, description = "Rows per page, 1 to 500, default 50") Integer limit,
      @McpToolParam(required = false, description = "First row, default 0") Integer offset) {
    return executor.execute("holdings", true, caller -> service.holdings(caller, untilDate, includeClosedPositions,
        groupBy, idPortfolio, orderBy, limit, offset));
  }

  @McpTool(name = "period_performance", description = """
      Performance of the whole tenant or of one portfolio between two dates, as the period performance report of the \
      user interface calculates it. firstDay and lastDay hold the totals on the first and the last day, difference \
      the change in between: totalBalanceMC (cash plus securities), externalCashTransferMC (deposits minus \
      withdrawals), gainMC and totalGainMC, dividendRealMC, interestCashaccountRealMC, feeRealMC. metrics holds the \
      time-weighted return twrPercent, the money-weighted return mwrPercent, the maximum drawdown, the annualized \
      volatility and the fee ratio, all in percentage points; a figure that cannot be determined is null. periods \
      splits the range into weeks or years with their gain and time-weighted return. The range must lie within the \
      trading days for which prices exist. """ + MC_RULE, annotations = @McpAnnotations(readOnlyHint = true,
      destructiveHint = false, openWorldHint = false))
  public CallToolResult periodPerformance(
      @McpToolParam(description = "First day yyyy-MM-dd") String dateFrom,
      @McpToolParam(description = "Last day yyyy-MM-dd") String dateTo,
      @McpToolParam(required = false, description = "week or year (default)") String periodSplit,
      @McpToolParam(required = false, description = "Restrict to one portfolio") Integer idPortfolio) {
    return executor.execute("period_performance", true,
        caller -> service.periodPerformance(caller, dateFrom, dateTo, periodSplit, idPortfolio));
  }

  @McpTool(name = "dividends", description = """
      Income and costs per calendar year in the main currency, the same yearly totals as the dividends table of the \
      user interface: dividendNetMC and dividendTaxMC (distributions of shares and funds), interestNetMC and \
      interestTaxMC (distributions of bonds), cashInterestMC (interest of cash accounts), financeCostCfdMC and \
      financeCostForexMC, feeMC, netIncomeMC, and per month netIncomeMonthMC and taxMonthMC (index 0 is January). \
      byAssetclass breaks the distributions down by asset class. Every year between the first and the last booking \
      is listed, also a year without income. totalsOfSelectedYears sum the listed years. Omit idsSecurityaccount or \
      idsCashaccount to include all accounts. """ + MC_RULE, annotations = @McpAnnotations(readOnlyHint = true,
      destructiveHint = false, openWorldHint = false))
  public CallToolResult dividends(
      @McpToolParam(required = false, description = "First year to list") Integer yearFrom,
      @McpToolParam(required = false, description = "Last year to list") Integer yearTo,
      @McpToolParam(required = false, description = "Security account ids from list_portfolios; omit for all") List<Integer> idsSecurityaccount,
      @McpToolParam(required = false, description = "Cash account ids from list_portfolios; omit for all") List<Integer> idsCashaccount) {
    return executor.execute("dividends", true,
        caller -> service.dividends(caller, yearFrom, yearTo, idsSecurityaccount, idsCashaccount));
  }

  @McpTool(name = "security_transactions", description = """
      The transactions of the tenant in one security up to a date, with the position summary at that date: units, \
      value, gain/loss and the cumulative currency result gainLossCurrencyMC. Per transaction: type, units, \
      quotation, the amount booked on the cash account (cashaccountAmount in cashaccountCurrency), exchange rate, \
      costs and the realized gain/loss of a sale. Works for any public security and for the tenant's own private \
      ones, also without holdings. Find the id with find_instrument or holdings. orderBy: transactionTime:asc \
      (default) or transactionTime:desc. """ + MC_RULE + " " + DATE_RULE + " " + PAGING_RULE,
      annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
  public CallToolResult securityTransactions(
      @McpToolParam(description = "Id of the security (idSecuritycurrency)") Integer idSecuritycurrency,
      @McpToolParam(required = false, description = "Last day yyyy-MM-dd, default today") String untilDate,
      @McpToolParam(required = false, description = "transactionTime:asc or transactionTime:desc") String orderBy,
      @McpToolParam(required = false, description = "Rows per page, 1 to 500, default 50") Integer limit,
      @McpToolParam(required = false, description = "First row, default 0") Integer offset) {
    return executor.execute("security_transactions", false,
        caller -> service.securityTransactions(caller, idSecuritycurrency, untilDate, orderBy, limit, offset));
  }

  @McpTool(name = "cashaccount_transactions", description = """
      The bookings of one cash account in one calendar year, optionally restricted to transaction types (WITHDRAWAL, \
      DEPOSIT, INTEREST_CASHACCOUNT, FEE, ACCUMULATE, REDUCE, DIVIDEND, FINANCE_COST, ACCRUED_INTEREST). Amounts are \
      in the currency of the account. runningSumOfSelection is the chronological sum of the selected bookings up to \
      and including the row; with a type filter or because earlier years are excluded it is not the account balance \
      - the balance comes from account_summary. sumOfSelection is the sum of the whole selection, not of the page. \
      orderBy: transactionTime:asc (default) or transactionTime:desc. """ + PAGING_RULE,
      annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
  public CallToolResult cashaccountTransactions(
      @McpToolParam(description = "Id of the cash account from list_portfolios") Integer idCashaccount,
      @McpToolParam(description = "Calendar year, for example 2026") Integer year,
      @McpToolParam(required = false, description = "Transaction types to include; omit for all") List<String> transactionTypes,
      @McpToolParam(required = false, description = "transactionTime:asc or transactionTime:desc") String orderBy,
      @McpToolParam(required = false, description = "Rows per page, 1 to 500, default 50") Integer limit,
      @McpToolParam(required = false, description = "First row, default 0") Integer offset) {
    return executor.execute("cashaccount_transactions", false, caller -> service.cashaccountTransactions(caller,
        idCashaccount, year, transactionTypes, orderBy, limit, offset));
  }

  @McpTool(name = "find_instrument", description = """
      Finds securities or currency pairs that exist in Grafioschtrader. For securities the text is matched against \
      the name, the ISIN and the ticker symbol; every match carries its id, ISIN, currency and stock exchange (MIC), so \
      instruments of the same name can be told apart. For currency pairs pass a currency code or both, for example \
      EUR/CHF. At most 20 matches are returned; moreAvailable true means the list is cut and the text should be \
      narrowed.""", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
  public CallToolResult findInstrument(
      @McpToolParam(description = "Search text, at least 2 characters") String text,
      @McpToolParam(required = false, description = "security (default) or currencypair") String kind) {
    return executor.execute("find_instrument", false, caller -> service.findInstrument(caller, text, kind));
  }
}
