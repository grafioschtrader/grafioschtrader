package grafioschtrader.reports;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.common.DateHelper;
import grafiosch.entities.User;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.repository.CashaccountJpaRepository;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.service.GlobalparametersService;

/**
 * Abstract base class for generating comprehensive security position summaries and reports across different
 * organizational levels (tenant, portfolio, account).
 * 
 * <h3>Key Capabilities:</h3>
 * <ul>
 * <li>Multi-level position reporting (tenant, portfolio, account)</li>
 * <li>Historical transaction processing with stock split adjustments</li>
 * <li>Dividend and tax handling with configurable exclusions</li>
 * <li>Asynchronous data loading for optimal performance</li>
 * <li>Support for both open and closed positions</li>
 * </ul>
 * 
 * <h3>Thread Safety:</h3>
 * <p>
 * <strong>Important:</strong> This class is designed to be stateless and thread-safe. There may be only one instance of
 * this class per application context to avoid any class member state conflicts. All operations rely on method
 * parameters and local variables to maintain thread safety in multi-user environments.
 * </p>
 */
public abstract class SecurityPositionSummaryReport {

  @Autowired
  protected GlobalparametersService globalparametersService;

  @Autowired
  protected SecurityCalcService securityCalcService;

  @Autowired
  protected TenantJpaRepository tenantJpaRepository;

  @Autowired
  protected SecurityaccountJpaRepository securityaccountJpaRepository;

  @Autowired
  protected SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  protected CashaccountJpaRepository cashaccountJpaRepository;

  @Autowired
  SecruityTransactionsReport secruityTransactionsReport;

  @Autowired
  protected CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  protected SecurityJpaRepository securityJpaRepository;

  @Autowired
  protected TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  /**
   * Template method for creating position groups and calculating grand totals. Subclasses must implement this method to
   * define specific grouping strategies and calculation logic for their particular reporting requirements.
   * 
   * <p>
   * This method enables different reporting formats and grouping strategies while leveraging the common position
   * calculation infrastructure provided by the base class. Implementation should handle position aggregation, group
   * creation, and grand total calculations according to the specific reporting needs.
   * </p>
   * 
   * @param tenant                      the tenant context for which calculations are performed
   * @param securityPositionSummaryList list of individual security positions to group and summarize
   * @param dateCurrencyMap             currency conversion context for multi-currency calculations
   * @return comprehensive position summary with groups and grand totals
   * @throws Exception if calculation or grouping operations fail
   */
  protected abstract SecurityPositionGrandSummary createGroupsAndCalcGrandTotal(final Tenant tenant,
      final List<SecurityPositionSummary> securityPositionSummaryList,
      final DateTransactionCurrencypairMap dateCurrencyMap) throws Exception;

  /**
   * Generates a comprehensive security position summary for all portfolios within a tenant as of a specific date.
   * Aggregates positions across all portfolios owned by the tenant, providing a complete view of the tenant's
   * investment holdings with real-time valuations and multi-currency normalization.
   * 
   * @param includeClosedPosition if true, includes positions that have been fully closed (units = 0)
   * @param untilDate             the calculation date for position valuation and historical data cutoff
   * @return comprehensive position summary across all tenant portfolios with groupings and totals
   * @throws Exception if data access, calculation, or currency conversion operations fail
   */
  @Transactional
  @Modifying
  public SecurityPositionGrandSummary getSecurityPositionGrandSummaryIdTenant(final boolean includeClosedPosition,
      Date untilDate) throws Exception {
    final Integer idTenant = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getIdTenant();

    final Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);

    final CompletableFuture<List<Object[]>> dateTransactionCurrencyFuture = CompletableFuture
        .supplyAsync(() -> historyquoteJpaRepository.getHistoryquotesForAllForeignTransactionsByIdTenant(idTenant));
    final CompletableFuture<List<Currencypair>> currencypairsFuture = CompletableFuture
        .supplyAsync(() -> currencypairJpaRepository.getAllCurrencypairsByTenantInPortfolioAndAccounts(idTenant));

    final DateTransactionCurrencypairMap dateCurrencyMap = new DateTransactionCurrencypairMap(tenant.getCurrency(),
        untilDate, dateTransactionCurrencyFuture.join(), currencypairsFuture.join(),
        false);

    final List<Securityaccount> securityaccountList = tenant.getPortfolioList().stream()
        .map(portfolio -> portfolio.getSecurityaccountList()).flatMap(Collection::stream).collect(Collectors.toList());
    return getSecurityPositionGrandSummary(tenant, securityaccountList, includeClosedPosition, tenant.isExcludeDivTax(),
        dateCurrencyMap);
  }

  /**
   * Generates a comprehensive security position summary for a specific portfolio as of a given date. Provides detailed
   * position analysis for all securities within the specified portfolio, including real-time valuations, performance
   * metrics, and multi-currency calculations normalized to the portfolio's base currency.
   * 
   * <p>
   * The method includes security validation to ensure the user has appropriate access to the requested portfolio data,
   * maintaining proper tenant isolation and security boundaries.
   * </p>
   * 
   * @param idPortfolio           the unique identifier of the portfolio to analyze
   * @param includeClosedPosition if true, includes positions that have been fully closed for historical analysis
   * @param untilDate             the calculation date for position valuation and transaction cutoff
   * @return comprehensive position summary for the specified portfolio, or empty summary if portfolio not found
   */
  @Transactional
  @Modifying
  public SecurityPositionGrandSummary getSecurityPositionGrandSummaryIdPortfolio(final Integer idPortfolio,
      final boolean includeClosedPosition, Date untilDate) throws Exception {
    final Integer idTenant = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getIdTenant();
    final List<Securityaccount> securityaccountList = securityaccountJpaRepository
        .findByPortfolio_IdPortfolioAndIdTenant(idPortfolio, idTenant);

    if (securityaccountList.isEmpty()) {
      return new SecurityPositionGrandSummary(null, null);
    } else {
      Tenant tenant = tenantJpaRepository.getReferenceById(securityaccountList.get(0).getPortfolio().getIdTenant());

      final CompletableFuture<List<Object[]>> dateTransactionCurrencyFuture = CompletableFuture.supplyAsync(
          () -> historyquoteJpaRepository.getHistoryquotesForAllForeignTransactionsByIdPortfolio(idPortfolio));
      final CompletableFuture<List<Currencypair>> currencypairsFuture = CompletableFuture.supplyAsync(
          () -> currencypairJpaRepository.getAllCurrencypairsByTenantInPortfolioAndAccounts(tenant.getIdTenant()));

      final DateTransactionCurrencypairMap dateCurrencyMap = new DateTransactionCurrencypairMap(
          securityaccountList.get(0).getPortfolio().getCurrency(), untilDate, dateTransactionCurrencyFuture.join(),
          currencypairsFuture.join(),
          tradingDaysPlusJpaRepository.hasTradingDayBetweenUntilYesterday(DateHelper.getLocalDate(untilDate)));
      return getSecurityPositionGrandSummary(tenant, securityaccountList, includeClosedPosition,
          tenant.isExcludeDivTax(), dateCurrencyMap);
    }
  }

  /**
   * Generates a detailed security position summary for a specific security account as of a given date. Provides
   * account-level granularity for detailed position analysis, including all transactions, adjustments, and valuations
   * for the securities held within the specified account.
   * 
   * <p>
   * Security validation ensures that users can only access accounts within their tenant scope, maintaining proper data
   * isolation and access control in multi-tenant environments.
   * </p>
   * 
   * @param idSecurityaccount     the unique identifier of the security account to analyze
   * @param includeClosedPosition if true, includes positions that have been fully closed for complete historical view
   * @param untilDate             the calculation date for position valuation and transaction processing cutoff
   * @return comprehensive position summary for the specified account
   * @throws SecurityException if the user lacks access to the specified account
   * @throws Exception         if data access or calculation operations fail
   */
  @Transactional
  @Modifying
  public SecurityPositionGrandSummary getSecurityPositionGrandSummaryIdSecurityaccount(final Integer idSecurityaccount,
      final boolean includeClosedPosition, Date untilDate) throws Exception {
    final Integer idTenant = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getIdTenant();
    final Securityaccount securityaccount = this.securityaccountJpaRepository
        .findByIdSecuritycashAccountAndIdTenant(idSecurityaccount, idTenant);

    if (securityaccount != null) {
      final List<Securityaccount> securityList = new ArrayList<>();
      securityList.add(securityaccount);
      Tenant tenant = tenantJpaRepository.getReferenceById(securityaccount.getPortfolio().getIdTenant());

      final CompletableFuture<List<Object[]>> dateTransactionCurrencyFuture = CompletableFuture
          .supplyAsync(() -> historyquoteJpaRepository
              .getHistoryquotesForAllForeignTransactionsByIdSecuritycashAccount(idSecurityaccount));
      final CompletableFuture<List<Currencypair>> currencypairsFuture = CompletableFuture.supplyAsync(
          () -> currencypairJpaRepository.getAllCurrencypairsByTenantInPortfolioAndAccounts(tenant.getIdTenant()));

      final DateTransactionCurrencypairMap dateCurrencyMap = new DateTransactionCurrencypairMap(
          securityaccount.getPortfolio().getCurrency(), untilDate, dateTransactionCurrencyFuture.join(),
          currencypairsFuture.join(),
          tradingDaysPlusJpaRepository.hasTradingDayBetweenUntilYesterday(DateHelper.getLocalDate(untilDate)));

      return getSecurityPositionGrandSummary(tenant, securityList, includeClosedPosition, tenant.isExcludeDivTax(),
          dateCurrencyMap);
    } else {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
  }

  /**
   * Core position calculation engine that processes security accounts to generate comprehensive position summaries with
   * real-time valuations. This method orchestrates the workflow of transaction processing, position aggregation,
   * currency conversion, and market price integration to produce accurate investment position analysis.
   * 
   * @param tenant                the tenant context providing calculation preferences and base currency
   * @param securityaccountList   list of security accounts to process for position calculation
   * @param includeClosedPosition whether to include fully closed positions in the final summary
   * @param excludeDivTaxcost     whether to exclude dividend tax costs from calculations
   * @param dateCurrencyMap       currency conversion context with historical exchange rates
   * @return comprehensive position summary with calculations, groupings, and totals
   */
  private SecurityPositionGrandSummary getSecurityPositionGrandSummary(final Tenant tenant,
      final List<Securityaccount> securityaccountList, final boolean includeClosedPosition,
      final boolean excludeDivTaxcost, final DateTransactionCurrencypairMap dateCurrencyMap) throws Exception {

    Date untilDatePlus = DateHelper.setTimeToZeroAndAddDay(dateCurrencyMap.getUntilDate(), 1);

    final Map<Security, SecurityPositionSummary> securityPositionSummaryMap = new HashMap<>();
    securityaccountList.stream().forEach((securityaccount) -> createPositionSummaryForSecurityaccount(securityaccount,
        securityPositionSummaryMap, excludeDivTaxcost, untilDatePlus, dateCurrencyMap));

    final List<SecurityPositionSummary> securityPositionSummaryList = securityJpaRepository
        .processOpenPositionsWithActualPrice(dateCurrencyMap.getUntilDate(), securityPositionSummaryMap);

    if (includeClosedPosition) {
      // Include closed positions for output
      final List<SecurityPositionSummary> closedSecurityPositionList = securityPositionSummaryMap.entrySet().stream()
          .filter(map -> map.getValue().units == 0).map(map -> map.getValue()).collect(Collectors.toList());
      securityPositionSummaryList.addAll(closedSecurityPositionList);
    }

    SecurityPositionGrandSummary securityPositionGrandSummary = createGroupsAndCalcGrandTotal(tenant,
        securityPositionSummaryList, dateCurrencyMap);
    sortSecurityPositionSummaryInGroupsByName(securityPositionGrandSummary);

    return securityPositionGrandSummary;

  }

  /**
   * Creates a SecurityPositionSummary for a certain security account. The same security in different security accounts
   * will be merged to a single result.
   *
   * @param securityaccount    Security account which transactions are processed
   * @param summarySecurityMap Contains the calculations for each individual security.
   * @param excludeDivTaxcost  If true, tax withholdings are not taken into account.
   * @param dateCurrencyMap    Contains the currency information
   */
  private void createPositionSummaryForSecurityaccount(final Securityaccount securityaccount,
      final Map<Security, SecurityPositionSummary> summarySecurityMap, final boolean excludeDivTaxcost,
      final Date untilDatePlus, final DateTransactionCurrencypairMap dateCurrencyMap) {

    final Map<Integer, List<Securitysplit>> securitysplitMap = securitysplitJpaRepository
        .getSecuritysplitMapByIdSecuritycashaccount(securityaccount.getIdSecuritycashAccount());

    for (final Transaction transaction : securityaccount.getSecurityTransactionList()) {
      if (transaction.getTransactionTime().after(untilDatePlus)) {
        break;
      }

      if (transaction.getSecurity() != null) {
        // We don't want transaction without a Security, for example FEE
        securityCalcService.calcSingleSecurityTransaction(transaction, summarySecurityMap, securitysplitMap,
            excludeDivTaxcost, dateCurrencyMap);
      }
    }
  }

  /**
   * Sorts the security position summaries within each group alphabetically by security name for consistent and
   * user-friendly report presentation. This method provides standardized ordering across all position reports,
   * improving readability and enabling users to quickly locate specific securities within their portfolios.
   * 
   * <p>
   * The sorting is case-insensitive to ensure consistent ordering regardless of how security names are formatted in the
   * underlying data. This method operates in-place on the provided summary structure for optimal performance.
   * </p>
   * 
   * @param securityPositionGrandSummary the comprehensive position summary containing grouped positions to sort
   */
  private void sortSecurityPositionSummaryInGroupsByName(SecurityPositionGrandSummary securityPositionGrandSummary) {
    securityPositionGrandSummary.securityPositionGroupSummaryList.forEach(securtiyPositionSummaryList -> {
      securtiyPositionSummaryList.securityPositionSummaryList
          .sort((SecurityPositionSummary sps1, SecurityPositionSummary sps2) -> sps1.securitycurrency.getName()
              .compareToIgnoreCase((sps2.securitycurrency.getName())));
    });

  }
}
