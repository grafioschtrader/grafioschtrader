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

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.User;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.repository.CashaccountJpaRepository;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;

/**
 * There may be only one instance of this class, to not use any class members.
 *
 * @author Hugo Graf
 *
 */
public abstract class SecurityPositionSummaryReport {

  @Autowired
  protected GlobalparametersJpaRepository globalparametersJpaRepository;

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

  protected abstract SecurityPositionGrandSummary createGroupsAndCalcGrandTotal(final Tenant tenant,
      final List<SecurityPositionSummary> securityPositionSummaryList,
      final DateTransactionCurrencypairMap dateCurrencyMap) throws Exception;

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
        tradingDaysPlusJpaRepository.hasTradingDayBetweenUntilYesterday(DateHelper.getLocalDate(untilDate)));

    final List<Securityaccount> securityaccountList = tenant.getPortfolioList().stream()
        .map(portfolio -> portfolio.getSecurityaccountList()).flatMap(Collection::stream).collect(Collectors.toList());
    return getSecurityPositionGrandSummary(tenant, securityaccountList, includeClosedPosition, tenant.isExcludeDivTax(),
        dateCurrencyMap);
  }

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
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
  }

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
   * Creates a SecurityPositionSummary for a certain security account. The same
   * security in different security accounts will be merged to a single result.
   *
   * @param securityaccount    Security account which transactions are processed
   * @param summarySecurityMap
   * @param excludeDivTaxcost
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

  private void sortSecurityPositionSummaryInGroupsByName(SecurityPositionGrandSummary securityPositionGrandSummary) {
    securityPositionGrandSummary.securityPositionGroupSummaryList.forEach(securtiyPositionSummaryList -> {
      securtiyPositionSummaryList.securityPositionSummaryList
          .sort((SecurityPositionSummary sps1, SecurityPositionSummary sps2) -> sps1.securitycurrency.getName()
              .compareToIgnoreCase((sps2.securitycurrency.getName())));
    });

  }
}
