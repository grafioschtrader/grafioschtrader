package grafioschtrader.mcp;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import grafiosch.common.DataHelper;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.mcp.McpToolResults.AccountGroup;
import grafioschtrader.mcp.McpToolResults.AccountRow;
import grafioschtrader.mcp.McpToolResults.AccountSummary;
import grafioschtrader.mcp.McpToolResults.AccountTotals;
import grafioschtrader.mcp.McpToolResults.CashaccountItem;
import grafioschtrader.mcp.McpToolResults.CashaccountTransactionRow;
import grafioschtrader.mcp.McpToolResults.CashaccountTransactions;
import grafioschtrader.mcp.McpToolResults.CurrencypairItem;
import grafioschtrader.mcp.McpToolResults.DividendAssetclass;
import grafioschtrader.mcp.McpToolResults.DividendTotals;
import grafioschtrader.mcp.McpToolResults.DividendYear;
import grafioschtrader.mcp.McpToolResults.Dividends;
import grafioschtrader.mcp.McpToolResults.HoldingGroup;
import grafioschtrader.mcp.McpToolResults.HoldingRow;
import grafioschtrader.mcp.McpToolResults.HoldingTotals;
import grafioschtrader.mcp.McpToolResults.Holdings;
import grafioschtrader.mcp.McpToolResults.InstrumentItem;
import grafioschtrader.mcp.McpToolResults.InstrumentSearch;
import grafioschtrader.mcp.McpToolResults.Page;
import grafioschtrader.mcp.McpToolResults.PeriodPerformance;
import grafioschtrader.mcp.McpToolResults.PeriodTotals;
import grafioschtrader.mcp.McpToolResults.PeriodWindowRow;
import grafioschtrader.mcp.McpToolResults.PortfolioItem;
import grafioschtrader.mcp.McpToolResults.PortfolioList;
import grafioschtrader.mcp.McpToolResults.SecurityPositionSummaryRow;
import grafioschtrader.mcp.McpToolResults.SecurityTransactionRow;
import grafioschtrader.mcp.McpToolResults.SecurityTransactions;
import grafioschtrader.mcp.McpToolResults.SecurityaccountItem;
import grafioschtrader.reports.AccountPositionGroupSummaryReport;
import grafioschtrader.reports.PerformanceReport;
import grafioschtrader.reports.SecruityTransactionsReport;
import grafioschtrader.reports.SecruityTransactionsReport.SecruityTransactionsReportOptions;
import grafioschtrader.reports.SecurityDividendsReport;
import grafioschtrader.reports.SecurityGroupByBaseReport;
import grafioschtrader.reports.SecurityPositionByCurrencyGrandSummaryReport;
import grafioschtrader.reports.SecurityPositionSummaryReport;
import grafioschtrader.reportviews.account.AccountPositionGrandSummary;
import grafioschtrader.reportviews.account.AccountPositionGroupSummary;
import grafioschtrader.reportviews.account.CashaccountPositionSummary;
import grafioschtrader.reportviews.performance.PerformancePeriod;
import grafioschtrader.reportviews.performance.PeriodHoldingAndDiff;
import grafioschtrader.reportviews.performance.WeekYear;
import grafioschtrader.reportviews.securityaccount.SecurityPositionCurrenyGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.securitydividends.SecurityDividendsChart;
import grafioschtrader.reportviews.securitydividends.SecurityDividendsChart.IncomeYear;
import grafioschtrader.reportviews.transaction.CashaccountTransactionPosition;
import grafioschtrader.reportviews.transaction.SecurityTransactionPosition;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.repository.helper.GroupCurrency;
import grafioschtrader.repository.helper.GroupPortfolio;
import grafioschtrader.search.CurrencyPairSearchBuilder;
import grafioschtrader.search.SecuritySearchBuilder;
import grafioschtrader.search.SecuritycurrencySearch;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TransactionType;

/**
 * The reading tools of the MCP server. Each method calls the same report the user interface uses, through the overload
 * that takes the tenant and the day explicitly, and projects its result into a compact answer.
 *
 * <p>
 * Every id passed by the client is checked by {@link McpInstrumentAccess} before a report sees it. No method changes
 * transactions or master data, and none starts a price download.
 * </p>
 */
@Service
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class McpReadToolService {

  /** Longest text value in an answer. */
  public static final int MAX_TEXT_LENGTH = 100;
  public static final int DEFAULT_LIMIT = 50;
  public static final int MAX_LIMIT = 500;
  /** Most matches {@code find_instrument} returns; one more row is read to know whether more exist. */
  public static final int MAX_SEARCH_RESULTS = 20;

  public static final List<String> ACCOUNT_GROUP_BY = List.of("portfolio", "currency");
  public static final List<String> HOLDING_GROUP_BY = List.of("currency", "assetclasstype",
      "specialinvestmentinstrument", "assetclass");
  public static final List<String> HOLDING_ORDER_BY = List.of("valueMC", "gainLossMC", "gainLossCurrencyMC",
      "shareOfTotalPercentage", "name");
  public static final List<String> TRANSACTION_ORDER_BY = List.of("transactionTime");
  public static final List<String> PERIOD_SPLITS = List.of("week", "year");
  public static final List<String> INSTRUMENT_KINDS = List.of("security", "currencypair");

  private final PortfolioJpaRepository portfolioJpaRepository;
  private final TenantJpaRepository tenantJpaRepository;
  private final AccountPositionGroupSummaryReport accountPositionGroupSummaryReport;
  private final SecurityPositionByCurrencyGrandSummaryReport securityPositionByCurrencyGrandSummaryReport;
  private final PerformanceReport performanceReport;
  private final SecurityDividendsReport securityDividendsReport;
  private final SecruityTransactionsReport secruityTransactionsReport;
  private final TransactionJpaRepository transactionJpaRepository;
  private final SecurityJpaRepository securityJpaRepository;
  private final CurrencypairJpaRepository currencypairJpaRepository;
  private final GlobalparametersService globalparametersService;
  private final McpInstrumentAccess access;
  private final AutowireCapableBeanFactory beanFactory;

  public McpReadToolService(PortfolioJpaRepository portfolioJpaRepository, TenantJpaRepository tenantJpaRepository,
      AccountPositionGroupSummaryReport accountPositionGroupSummaryReport,
      SecurityPositionByCurrencyGrandSummaryReport securityPositionByCurrencyGrandSummaryReport,
      PerformanceReport performanceReport, SecurityDividendsReport securityDividendsReport,
      SecruityTransactionsReport secruityTransactionsReport, TransactionJpaRepository transactionJpaRepository,
      SecurityJpaRepository securityJpaRepository, CurrencypairJpaRepository currencypairJpaRepository,
      GlobalparametersService globalparametersService, McpInstrumentAccess access,
      AutowireCapableBeanFactory beanFactory) {
    this.portfolioJpaRepository = portfolioJpaRepository;
    this.tenantJpaRepository = tenantJpaRepository;
    this.accountPositionGroupSummaryReport = accountPositionGroupSummaryReport;
    this.securityPositionByCurrencyGrandSummaryReport = securityPositionByCurrencyGrandSummaryReport;
    this.performanceReport = performanceReport;
    this.securityDividendsReport = securityDividendsReport;
    this.secruityTransactionsReport = secruityTransactionsReport;
    this.transactionJpaRepository = transactionJpaRepository;
    this.securityJpaRepository = securityJpaRepository;
    this.currencypairJpaRepository = currencypairJpaRepository;
    this.globalparametersService = globalparametersService;
    this.access = access;
    this.beanFactory = beanFactory;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // list_portfolios
  // ---------------------------------------------------------------------------------------------------------------

  public PortfolioList listPortfolios(McpCaller caller) {
    List<PortfolioItem> items = portfolioJpaRepository.setExistingTransactionOnSecurityaccount(caller.idTenant())
        .stream()
        .map(p -> new PortfolioItem(p.getIdPortfolio(), cap(p.getName()), p.getCurrency(),
            p.getSecurityaccountList().stream()
                .map(s -> new SecurityaccountItem(s.getIdSecuritycashAccount(), cap(s.getName()))).toList(),
            p.getCashaccountList().stream()
                .map(c -> new CashaccountItem(c.getIdSecuritycashAccount(), cap(c.getName()), c.getCurrency()))
                .toList()))
        .toList();
    return new PortfolioList(tenantJpaRepository.getReferenceById(caller.idTenant()).getCurrency(), items);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // account_summary
  // ---------------------------------------------------------------------------------------------------------------

  public AccountSummary accountSummary(McpCaller caller, String untilDateStr, String groupBy) {
    String group = requireOneOf("groupBy", groupBy == null ? "portfolio" : groupBy, ACCOUNT_GROUP_BY);
    LocalDate untilDate = parseDateOrToday(caller, "untilDate", untilDateStr);
    AccountPositionGrandSummary grand = accountPositionGroupSummaryReport.getAccountGrandSummaryIdTenant(
        caller.idTenant(), group.equals("portfolio") ? new GroupPortfolio() : new GroupCurrency(), untilDate);
    List<AccountGroup> groups = grand.getAccountPositionGroupSummaryList().stream().map(this::toAccountGroup)
        .toList();
    AccountTotals totals = new AccountTotals(grand.getGrandValueMC(), grand.getGrandCashBalanceMC(),
        grand.getGrandValueSecuritiesMC(), grand.getGrandGainLossSecuritiesMC(), grand.getGrandGainLossCurrencyMC(),
        grand.getGrandAccountFeesMC(), grand.getGrandAccountInterestMC(), grand.getGrandExternalCashTransferMC(),
        grand.getGrandCashTransferMC());
    List<String> missing = grand.missingExchangeRates.stream().map(m -> cap(String.valueOf(m))).toList();
    return new AccountSummary(untilDate, caller.zoneId().getId(), grand.mainCurrency, group, totals, groups,
        missing.isEmpty(), missing);
  }

  private AccountGroup toAccountGroup(AccountPositionGroupSummary g) {
    AccountTotals totals = new AccountTotals(g.getGroupValueMC(), g.getGroupCashBalanceMC(),
        g.getGroupValueSecuritiesMC(), g.getGroupGainLossSecuritiesMC(), g.getGroupGainLossCurrencyMC(),
        g.getGroupAccountFeesMC(), g.getGroupAccountInterestMC(), g.getGroupExternalCashTransferMC(),
        g.getGroupCashTransferMC());
    List<AccountRow> rows = g.accountPositionSummaryList.stream().filter(c -> c.getCashaccount() != null)
        .map(this::toAccountRow).toList();
    return new AccountGroup(cap(g.groupName), g.currency, totals, rows);
  }

  private AccountRow toAccountRow(CashaccountPositionSummary c) {
    Cashaccount cashaccount = c.getCashaccount();
    return new AccountRow(cashaccount.getIdSecuritycashAccount(), cap(cashaccount.getName()),
        cashaccount.getCurrency(), c.getCashBalance(), c.getCashBalanceMC(), c.getValueSecuritiesMC(),
        c.getGainLossSecuritiesMC(), c.getGainLossCurrencyMC(), c.getValueMC());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // holdings
  // ---------------------------------------------------------------------------------------------------------------

  public Holdings holdings(McpCaller caller, String untilDateStr, Boolean includeClosedPositions, String groupBy,
      Integer idPortfolio, String orderBy, Integer limit, Integer offset) throws Exception {
    String group = requireOneOf("groupBy", groupBy == null ? "currency" : groupBy, HOLDING_GROUP_BY);
    LocalDate untilDate = parseDateOrToday(caller, "untilDate", untilDateStr);
    boolean includeClosed = Boolean.TRUE.equals(includeClosedPositions);
    OrderBy order = parseOrderBy(orderBy, "valueMC:desc", HOLDING_ORDER_BY);
    PageRequest pageRequest = pageRequest(limit, offset);
    if (idPortfolio != null) {
      access.requireOwnPortfolio(caller, idPortfolio, "idPortfolio");
    }
    SecurityPositionSummaryReport report = holdingsReport(group);
    SecurityPositionGrandSummary grand = idPortfolio == null
        ? report.getSecurityPositionGrandSummaryIdTenant(caller.idTenant(), includeClosed, untilDate, caller.today())
        : report.getSecurityPositionGrandSummaryIdPortfolio(caller.idTenant(), idPortfolio, includeClosed, untilDate,
            caller.today());
    List<HoldingGroup> groupTotals = new ArrayList<>();
    List<HoldingRow> rows = new ArrayList<>();
    for (SecurityPositionGroupSummary groupSummary : grand.securityPositionGroupSummaryList) {
      String groupKey = groupKey(groupSummary);
      groupTotals.add(toHoldingGroup(groupKey, groupLabel(caller, group, groupSummary), groupSummary));
      groupSummary.securityPositionSummaryList.forEach(p -> rows.add(toHoldingRow(p, groupKey)));
    }
    rows.sort(holdingComparator(order));
    HoldingTotals totals = new HoldingTotals(grand.getGrandAccountValueSecurityMC(),
        grand.getGrandGainLossSecurityMC(), grand.getGrandGainLossCurrencyMC(),
        rows.stream().noneMatch(HoldingRow::priceMissing));
    return new Holdings(untilDate, caller.zoneId().getId(), grand.currency, group, idPortfolio, includeClosed, totals,
        groupTotals, pageRequest.slice(rows), pageRequest.page(rows.size(), order.text()));
  }

  private SecurityPositionSummaryReport holdingsReport(String group) {
    String fieldName = switch (group) {
    case "assetclasstype" -> SecurityGroupByBaseReport.ASSETCLASS_CATEGORY_FIELD_NAME;
    case "specialinvestmentinstrument" -> SecurityGroupByBaseReport.ASSETCLASS_SPEC_INVEST_INST_FIELD_NAME;
    case "assetclass" -> SecurityGroupByBaseReport.ASSETCLASS_ID_ASSETCLASS_FIELD_NAME;
    default -> null;
    };
    if (fieldName == null) {
      return securityPositionByCurrencyGrandSummaryReport;
    }
    // The grouping reports carry their field per instance, the same way SecurityaccountResource builds them.
    SecurityGroupByBaseReport<Object> report = new SecurityGroupByBaseReport<>(fieldName);
    beanFactory.autowireBean(report);
    return report;
  }

  private static String groupKey(SecurityPositionGroupSummary groupSummary) {
    if (groupSummary instanceof SecurityPositionCurrenyGroupSummary currencyGroup) {
      return currencyGroup.currency;
    }
    if (groupSummary instanceof SecurityPositionDynamicGroupSummary<?> dynamicGroup) {
      return dynamicGroup.groupField == null ? null : String.valueOf(dynamicGroup.groupField);
    }
    return null;
  }

  private String groupLabel(McpCaller caller, String group, SecurityPositionGroupSummary groupSummary) {
    if (!group.equals("assetclass") || groupSummary.securityPositionSummaryList.isEmpty()) {
      return null;
    }
    Assetclass assetclass = groupSummary.securityPositionSummaryList.getFirst().getSecurity().getAssetClass();
    return assetclassLabel(caller, assetclass);
  }

  private String assetclassLabel(McpCaller caller, Assetclass assetclass) {
    String subCategory = assetclass.getSubCategoryNLS() == null ? null
        : assetclass.getSubCategoryNLS().getText(caller.language().getKey());
    return cap(assetclass.getCategoryType() + (subCategory == null ? "" : " / " + subCategory));
  }

  private static HoldingGroup toHoldingGroup(String key, String label, SecurityPositionGroupSummary g) {
    return new HoldingGroup(key, label, g.securityPositionSummaryList.size(), g.getGroupAccountValueSecurityMC(),
        g.getGroupGainLossSecurityMC(), g.getGroupGainLossCurrencyMC(), g.getGroupShareOfTotalPercentage(),
        g.securityPositionSummaryList.stream().noneMatch(p -> p.priceMissing));
  }

  private static HoldingRow toHoldingRow(SecurityPositionSummary p, String groupKey) {
    Security security = p.getSecurity();
    return new HoldingRow(security.getIdSecuritycurrency(), cap(security.getName()), security.getIsin(),
        security.getCurrency(), groupKey, p.getUnits(), p.closePrice, p.closeDate,
        p.closePriceOrigin == null ? null : p.closePriceOrigin.name(), p.priceMissing, p.getAccountValueSecurity(),
        p.getAccountValueSecurityMC(), p.getGainLossSecurity(), p.getGainLossSecurityMC(), p.getGainLossCurrencyMC(),
        p.getShareOfTotalPercentage());
  }

  private static Comparator<HoldingRow> holdingComparator(OrderBy order) {
    Comparator<HoldingRow> comparator = switch (order.field()) {
    case "gainLossMC" -> Comparator.comparingDouble(HoldingRow::gainLossSecurityMC);
    case "gainLossCurrencyMC" -> Comparator.comparingDouble(HoldingRow::gainLossCurrencyMC);
    case "shareOfTotalPercentage" -> Comparator.comparing(HoldingRow::shareOfTotalPercentage,
        Comparator.nullsFirst(Comparator.naturalOrder()));
    case "name" -> Comparator.comparing((HoldingRow r) -> r.name() == null ? "" : r.name().toLowerCase(Locale.ROOT));
    default -> Comparator.comparingDouble(HoldingRow::valueMC);
    };
    if (order.descending()) {
      comparator = comparator.reversed();
    }
    return comparator.thenComparing(HoldingRow::idSecuritycurrency);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // period_performance
  // ---------------------------------------------------------------------------------------------------------------

  public PeriodPerformance periodPerformance(McpCaller caller, String dateFromStr, String dateToStr,
      String periodSplit, Integer idPortfolio) throws Exception {
    LocalDate dateFrom = parseRequiredDate("dateFrom", dateFromStr);
    LocalDate dateTo = parseRequiredDate("dateTo", dateToStr);
    if (dateTo.isBefore(dateFrom)) {
      throw McpToolException.invalidArgument("dateTo", "must not be before dateFrom");
    }
    String split = requireOneOf("periodSplit", periodSplit == null ? "year" : periodSplit, PERIOD_SPLITS);
    WeekYear weekYear = split.equals("week") ? WeekYear.WM_WEEK : WeekYear.WM_YEAR;
    String currency;
    PerformancePeriod period;
    if (idPortfolio == null) {
      currency = tenantJpaRepository.getReferenceById(caller.idTenant()).getCurrency();
      period = performanceReport.getPeriodPerformanceByTenant(caller.idTenant(), caller.localeStr(), caller.today(),
          dateFrom, dateTo, weekYear);
    } else {
      currency = access.requireOwnPortfolio(caller, idPortfolio, "idPortfolio").getCurrency();
      period = performanceReport.getPeriodPerformanceByPortfolio(caller.idTenant(), caller.localeStr(),
          caller.today(), idPortfolio, dateFrom, dateTo, weekYear);
    }
    int precision = globalparametersService.getPrecisionForCurrency(currency);
    List<PeriodWindowRow> windows = period.getPeriodWindows().stream()
        .map(w -> new PeriodWindowRow(w.startDate, w.endDate, roundNullable(w.gainPeriodMC, precision), w.twrPercent))
        .toList();
    return new PeriodPerformance(dateFrom, dateTo, caller.zoneId().getId(), currency, idPortfolio, split,
        toPeriodTotals(period.getFirstDayTotals(), precision), toPeriodTotals(period.getLastDayTotals(), precision),
        toPeriodTotals(period.getDifference(), precision), period.getMetrics(), windows);
  }

  private static PeriodTotals toPeriodTotals(PeriodHoldingAndDiff p, int precision) {
    if (p == null) {
      return null;
    }
    return new PeriodTotals(p.getDate(), DataHelper.round(p.getTotalBalanceMC(), precision),
        DataHelper.round(p.getCashBalanceMC(), precision), DataHelper.round(p.getSecuritiesAndMarginGainMC(), precision),
        DataHelper.round(p.getExternalCashTransferMC(), precision), DataHelper.round(p.getGainMC(), precision),
        DataHelper.round(p.getMarginCloseGainMC(), precision), DataHelper.round(p.getTotalGainMC(), precision),
        DataHelper.round(p.getDividendRealMC(), precision), DataHelper.round(p.getInterestCashaccountRealMC(), precision),
        DataHelper.round(p.getFeeRealMC(), precision), DataHelper.round(p.getAccumulateReduceMC(), precision));
  }

  // ---------------------------------------------------------------------------------------------------------------
  // dividends
  // ---------------------------------------------------------------------------------------------------------------

  public Dividends dividends(McpCaller caller, Integer yearFrom, Integer yearTo, List<Integer> idsSecurityaccount,
      List<Integer> idsCashaccount) {
    // Omitted means "all", which the report expects as a list holding -1. An empty list would select no cash account
    // at all in the report but every security account, so an explicitly empty list is refused.
    List<Integer> securityaccounts = requireIdsOrAll(caller, "idsSecurityaccount", idsSecurityaccount, true);
    List<Integer> cashaccounts = requireIdsOrAll(caller, "idsCashaccount", idsCashaccount, false);
    if (yearFrom != null && yearTo != null && yearTo < yearFrom) {
      throw McpToolException.invalidArgument("yearTo", "must not be before yearFrom");
    }
    SecurityDividendsChart chart = securityDividendsReport.getSecurityDividendsChartByTenant(caller.idTenant(),
        securityaccounts, cashaccounts);
    List<DividendYear> years = chart.getIncomeYears().stream()
        .filter(y -> (yearFrom == null || y.year >= yearFrom) && (yearTo == null || y.year <= yearTo))
        .map(McpReadToolService::toDividendYear).toList();
    Map<Integer, Assetclass> assetclasses = new HashMap<>();
    chart.getAssetclasses().forEach(a -> assetclasses.put(a.getIdAssetClass(), a));
    List<DividendAssetclass> byAssetclass = chart.getIncomeAssetclasses().stream()
        .filter(a -> (yearFrom == null || a.year >= yearFrom) && (yearTo == null || a.year <= yearTo)).map(a -> {
          Assetclass assetclass = assetclasses.get(a.idAssetClass);
          String subCategory = assetclass == null || assetclass.getSubCategoryNLS() == null ? null
              : cap(assetclass.getSubCategoryNLS().getText(caller.language().getKey()));
          return new DividendAssetclass(a.year, a.idAssetClass,
              assetclass == null ? null : String.valueOf(assetclass.getCategoryType()), subCategory, a.getNetMC(),
              a.getTaxMC());
        }).toList();
    int precision = globalparametersService.getPrecisionForCurrency(chart.mainCurrency);
    DividendTotals totals = new DividendTotals(sum(years, DividendYear::dividendNetMC, precision),
        sum(years, DividendYear::dividendTaxMC, precision), sum(years, DividendYear::interestNetMC, precision),
        sum(years, DividendYear::cashInterestMC, precision), sum(years, DividendYear::feeMC, precision),
        sum(years, DividendYear::netIncomeMC, precision));
    return new Dividends(chart.mainCurrency, yearFrom, yearTo, idsSecurityaccount, idsCashaccount, years,
        byAssetclass, totals);
  }

  private List<Integer> requireIdsOrAll(McpCaller caller, String parameter, List<Integer> ids, boolean security) {
    if (ids == null) {
      return new ArrayList<>(List.of(-1));
    }
    if (ids.isEmpty()) {
      throw McpToolException.invalidArgument(parameter, "omit the parameter for all accounts, an empty list is invalid");
    }
    if (security) {
      access.requireOwnSecurityaccounts(caller, ids, parameter);
    } else {
      access.requireOwnCashaccounts(caller, ids, parameter);
    }
    return new ArrayList<>(ids);
  }

  private static DividendYear toDividendYear(IncomeYear y) {
    return new DividendYear(y.year, y.getDividendNetMC(), y.getDividendTaxMC(), y.getInterestNetMC(),
        y.getInterestTaxMC(), y.getCashInterestMC(), y.getFinanceCostCfdMC(), y.getFinanceCostForexMC(), y.getFeeMC(),
        y.getNetIncomeMC(), y.getNetIncomeMonthMC(), y.getTaxMonthMC());
  }

  private static double sum(List<DividendYear> years, ToDoubleFunction<DividendYear> field,
      int precision) {
    return DataHelper.round(years.stream().mapToDouble(field).sum(), precision);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // security_transactions
  // ---------------------------------------------------------------------------------------------------------------

  public SecurityTransactions securityTransactions(McpCaller caller, Integer idSecuritycurrency, String untilDateStr,
      String orderBy, Integer limit, Integer offset) {
    Security security = access.requireVisibleSecurity(caller, idSecuritycurrency, "idSecuritycurrency");
    LocalDate untilDate = parseDateOrToday(caller, "untilDate", untilDateStr);
    OrderBy order = parseOrderBy(orderBy, "transactionTime:asc", TRANSACTION_ORDER_BY);
    PageRequest pageRequest = pageRequest(limit, offset);
    SecurityTransactionSummary summary = secruityTransactionsReport
        .getTransactionsByIdTenantAndIdSecurityAndClearSecurity(caller.idTenant(), security.getIdSecuritycurrency(),
            untilDate, new HashSet<SecruityTransactionsReportOptions>());
    List<SecurityTransactionRow> rows = new ArrayList<>(
        summary.transactionPositionList.stream().map(McpReadToolService::toSecurityTransactionRow).toList());
    Comparator<SecurityTransactionRow> comparator = Comparator.comparing(SecurityTransactionRow::transactionTime)
        .thenComparing(SecurityTransactionRow::idTransaction);
    rows.sort(order.descending() ? comparator.reversed() : comparator);
    SecurityPositionSummary p = summary.securityPositionSummary;
    SecurityPositionSummaryRow positionSummary = p == null ? null
        : new SecurityPositionSummaryRow(p.getUnits(), p.closePrice, p.closeDate, p.priceMissing,
            p.getAccountValueSecurity(), p.getAccountValueSecurityMC(), p.getGainLossSecurity(),
            p.getGainLossSecurityMC(), p.getGainLossCurrencyMC(), p.getTransactionCost(), p.getTaxCost());
    return new SecurityTransactions(untilDate, caller.zoneId().getId(),
        tenantJpaRepository.getReferenceById(caller.idTenant()).getCurrency(), toInstrumentItem(caller, security),
        positionSummary, pageRequest.slice(rows), pageRequest.page(rows.size(), order.text()));
  }

  private static SecurityTransactionRow toSecurityTransactionRow(SecurityTransactionPosition position) {
    Transaction t = position.transaction;
    Cashaccount cashaccount = t.getCashaccount();
    return new SecurityTransactionRow(t.getIdTransaction(), t.getTransactionTime(), t.getTransactionType().name(),
        t.getUnits(), t.getQuotation(), t.getCashaccountAmount(), cashaccount == null ? null : cashaccount.getCurrency(),
        t.getCurrencyExRate(), t.getTransactionCost(), t.getTaxCost(), t.getIdSecurityaccount(),
        cashaccount == null ? null : cashaccount.getIdSecuritycashAccount(), position.transactionGainLoss,
        position.getTransactionGainLossMC(), position.getTransactionGainLossCurrencyMC());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // cashaccount_transactions
  // ---------------------------------------------------------------------------------------------------------------

  public CashaccountTransactions cashaccountTransactions(McpCaller caller, Integer idCashaccount, Integer year,
      List<String> transactionTypes, String orderBy, Integer limit, Integer offset) {
    Cashaccount cashaccount = access.requireOwnCashaccount(caller, idCashaccount, "idCashaccount");
    if (year == null || year < 1900 || year > 2999) {
      throw McpToolException.invalidArgument("year", "a calendar year such as 2026 is required");
    }
    List<TransactionType> types = parseTransactionTypes(transactionTypes);
    OrderBy order = parseOrderBy(orderBy, "transactionTime:asc", TRANSACTION_ORDER_BY);
    PageRequest pageRequest = pageRequest(limit, offset);
    // The report orders the selection newest first, with the id as tie-break, and sums it backwards, so every row
    // carries the sum of the whole selection up to and including itself, independent of the page.
    CashaccountTransactionPosition[] positions = transactionJpaRepository.getTransactionsWithBalanceForCashaccount(
        cashaccount.getIdSecuritycashAccount(), year, types.stream().mapToInt(t -> t.getValue()).toArray());
    List<CashaccountTransactionRow> rows = new ArrayList<>(
        Arrays.stream(positions).map(McpReadToolService::toCashaccountTransactionRow).toList());
    double sumOfSelection = positions.length == 0 ? 0.0 : positions[0].balance;
    Comparator<CashaccountTransactionRow> comparator = Comparator
        .comparing(CashaccountTransactionRow::transactionTime).thenComparing(CashaccountTransactionRow::idTransaction);
    rows.sort(order.descending() ? comparator.reversed() : comparator);
    return new CashaccountTransactions(cashaccount.getIdSecuritycashAccount(), cap(cashaccount.getName()),
        cashaccount.getCurrency(), year, types.stream().map(Enum::name).toList(), sumOfSelection,
        pageRequest.slice(rows), pageRequest.page(rows.size(), order.text()));
  }

  private static List<TransactionType> parseTransactionTypes(List<String> transactionTypes) {
    List<TransactionType> bookable = Arrays.stream(TransactionType.values())
        .filter(t -> t != TransactionType.HYPOTHETICAL_BUY && t != TransactionType.HYPOTHETICAL_SELL).toList();
    if (transactionTypes == null || transactionTypes.isEmpty()) {
      return bookable;
    }
    return transactionTypes.stream().map(name -> bookable.stream().filter(t -> t.name().equals(name)).findFirst()
        .orElseThrow(() -> McpToolException.invalidArgument("transactionTypes", "allowed: " + bookable))).toList();
  }

  private static CashaccountTransactionRow toCashaccountTransactionRow(CashaccountTransactionPosition position) {
    Transaction t = position.transaction;
    Security security = t.getSecurity();
    return new CashaccountTransactionRow(t.getIdTransaction(), t.getTransactionTime(), t.getTransactionType().name(),
        t.getCashaccountAmount(), position.balance, security == null ? null : security.getIdSecuritycurrency(),
        security == null ? null : cap(security.getName()), t.getIdSecurityaccount(), t.getConnectedIdTransaction());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // find_instrument
  // ---------------------------------------------------------------------------------------------------------------

  public InstrumentSearch findInstrument(McpCaller caller, String text, String kind) {
    String searchText = text == null ? "" : text.trim();
    if (searchText.length() < 2) {
      throw McpToolException.invalidArgument("text", "at least 2 characters");
    }
    String instrumentKind = requireOneOf("kind", kind == null ? "security" : kind, INSTRUMENT_KINDS);
    if (instrumentKind.equals("currencypair")) {
      return findCurrencypair(searchText);
    }
    Map<Integer, Security> matches = new LinkedHashMap<>();
    boolean more = false;
    for (Function<String, SecuritycurrencySearch> criterion : List.<Function<String, SecuritycurrencySearch>>of(
        t -> searchCriteria(t, null, null), t -> searchCriteria(null, t.toUpperCase(Locale.ROOT), null),
        t -> searchCriteria(null, null, t))) {
      List<Security> found = securityJpaRepository.findBy(
          new SecuritySearchBuilder(null, null, criterion.apply(searchText), caller.idTenant(), null),
          q -> q.sortBy(Sort.by("name", "idSecuritycurrency")).limit(MAX_SEARCH_RESULTS + 1).all());
      more |= found.size() > MAX_SEARCH_RESULTS;
      found.forEach(s -> matches.putIfAbsent(s.getIdSecuritycurrency(), s));
    }
    more |= matches.size() > MAX_SEARCH_RESULTS;
    List<InstrumentItem> items = matches.values().stream().limit(MAX_SEARCH_RESULTS)
        .map(s -> toInstrumentItem(caller, s)).toList();
    return new InstrumentSearch("security", cap(searchText), items, List.of(), more,
        more ? "More instruments match. Narrow the text, for example with an ISIN or a ticker symbol." : null);
  }

  private static SecuritycurrencySearch searchCriteria(String name, String isin, String tickerSymbol) {
    SecuritycurrencySearch search = new SecuritycurrencySearch();
    search.setName(name);
    search.setIsin(isin);
    search.setTickerSymbol(tickerSymbol);
    search.setNameRegex(false);
    return search;
  }

  private InstrumentSearch findCurrencypair(String searchText) {
    String normalized = searchText.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
    List<CurrencypairItem> items;
    boolean more = false;
    if (normalized.length() == 6) {
      var pair = currencypairJpaRepository.findByFromCurrencyAndToCurrency(normalized.substring(0, 3),
          normalized.substring(3));
      items = pair == null ? List.of()
          : List.of(new CurrencypairItem(pair.getIdSecuritycurrency(), pair.getFromCurrency(), pair.getToCurrency()));
    } else {
      SecuritycurrencySearch search = searchCriteria(normalized, null, null);
      var found = currencypairJpaRepository.findBy(new CurrencyPairSearchBuilder(null, null, search),
          q -> q.sortBy(Sort.by("fromCurrency", "toCurrency", "idSecuritycurrency")).limit(MAX_SEARCH_RESULTS + 1)
              .all());
      more = found.size() > MAX_SEARCH_RESULTS;
      items = found.stream().limit(MAX_SEARCH_RESULTS)
          .map(c -> new CurrencypairItem(c.getIdSecuritycurrency(), c.getFromCurrency(), c.getToCurrency())).toList();
    }
    return new InstrumentSearch("currencypair", cap(searchText), List.of(), items, more,
        more ? "More currency pairs match. Pass both currencies, for example EUR/CHF." : null);
  }

  private static InstrumentItem toInstrumentItem(McpCaller caller, Security s) {
    return new InstrumentItem(s.getIdSecuritycurrency(), cap(s.getName()), s.getIsin(), cap(s.getTickerSymbol()),
        s.getCurrency(), s.getStockexchange() == null ? null : s.getStockexchange().getMic(),
        s.getAssetClass() == null ? null : String.valueOf(s.getAssetClass().getCategoryType()), s.getActiveToDate(),
        caller.idTenant().equals(s.getIdTenantPrivate()));
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Arguments and paging
  // ---------------------------------------------------------------------------------------------------------------

  /** Limits a text of the data to {@link #MAX_TEXT_LENGTH} characters. */
  static String cap(String text) {
    return text == null || text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH);
  }

  private static Double roundNullable(Double value, int precision) {
    return value == null ? null : DataHelper.round(value, precision);
  }

  static String requireOneOf(String parameter, String value, List<String> allowed) {
    if (!allowed.contains(value)) {
      throw McpToolException.invalidArgument(parameter, "allowed: " + String.join(", ", allowed));
    }
    return value;
  }

  static LocalDate parseDateOrToday(McpCaller caller, String parameter, String value) {
    return value == null || value.isBlank() ? caller.today() : parseRequiredDate(parameter, value);
  }

  static LocalDate parseRequiredDate(String parameter, String value) {
    if (value == null || value.isBlank()) {
      throw McpToolException.invalidArgument(parameter, "a date yyyy-MM-dd is required");
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException e) {
      throw McpToolException.invalidArgument(parameter, "a date yyyy-MM-dd is required");
    }
  }

  /** A requested order: one of the allowed fields, ascending or descending. */
  record OrderBy(String field, boolean descending) {
    String text() {
      return field + (descending ? ":desc" : ":asc");
    }
  }

  static OrderBy parseOrderBy(String value, String defaultValue, List<String> allowedFields) {
    String[] parts = (value == null || value.isBlank() ? defaultValue : value.trim()).split(":");
    boolean descending = parts.length > 1 && parts[1].equalsIgnoreCase("desc");
    if (!allowedFields.contains(parts[0]) || parts.length > 2
        || (parts.length == 2 && !descending && !parts[1].equalsIgnoreCase("asc"))) {
      throw McpToolException.invalidArgument("orderBy",
          "<field>[:asc|:desc] with field one of " + String.join(", ", allowedFields));
    }
    return new OrderBy(parts[0], descending);
  }

  static PageRequest pageRequest(Integer limit, Integer offset) {
    int l = limit == null ? DEFAULT_LIMIT : limit;
    int o = offset == null ? 0 : offset;
    if (l < 1 || l > MAX_LIMIT) {
      throw McpToolException.invalidArgument("limit", "between 1 and " + MAX_LIMIT);
    }
    if (o < 0) {
      throw McpToolException.invalidArgument("offset", "0 or greater");
    }
    return new PageRequest(l, o);
  }

  /** A page of an already sorted list. */
  record PageRequest(int limit, int offset) {
    <T> List<T> slice(List<T> rows) {
      return IntStream.range(Math.min(offset, rows.size()), Math.min(offset + limit, rows.size()))
          .mapToObj(rows::get).toList();
    }

    Page page(int totalCount, String orderBy) {
      return new Page(totalCount, offset, limit, offset + limit < totalCount ? offset + limit : null, orderBy);
    }
  }
}
