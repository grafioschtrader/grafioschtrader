package grafioschtrader.reports;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.User;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityOpenPositionPerSecurityaccount;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.securityaccount.SecurityaccountOpenPositionUnits;
import grafioschtrader.reportviews.transaction.SecurityTransactionPosition;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.IPositionCloseOnLatestPrice;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.TransactionType;

/**
 * Creates a Report for a single security with all its transactions.
 *
 *
 * @author Hugo Graf
 *
 */
@Component
public class SecruityTransactionsReport {

  @Autowired
  protected SecurityCalcService securityCalcService;

  @Autowired
  protected TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private PortfolioJpaRepository portfolioJpaRepository;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  public enum SecruityTransactionsReportOptions {
    /**
     * Set security of Transaction to null. It is for the reduction the json
     * footprint
     */
    CLEAR_TRANSACTION_SECURITY,
    /**
     * Some charts of historical security data includes transaction (sell, buy,
     * dividend) data. Normally all history quote data are adjusted to splits. In
     * such case the transaction quotation must be corrected as well.
     */
    QUTATION_SPLIT_CORRECTION
  }

  /**
   * Get transactions for a security over all portfolios until a certain date.
   *
   * @param idTenant
   * @param idSecuritycurrency
   * @param untilDate
   * @param secruityTransactionsReportOptions
   * @return
   */
  public SecurityTransactionSummary getTransactionsByIdTenantAndIdSecurityAndClearSecurity(final Integer idTenant,
      final Integer idSecuritycurrency, final Date untilDate,
      final Set<SecruityTransactionsReportOptions> secruityTransactionsReportOptions) {
    secruityTransactionsReportOptions.add(SecruityTransactionsReportOptions.CLEAR_TRANSACTION_SECURITY);
    return getTransactionsByIdTenantAndIdSecurity(idTenant, null, idSecuritycurrency, untilDate,
        secruityTransactionsReportOptions, null);
  }

  /**
   * Get transactions for a security over a single portfolio until a certain date.
   *
   * @param idPortfolio
   * @param idSecuritycurrency
   * @param untilDate
   * @param secruityTransactionsReportOptions
   * @return
   */
  public SecurityTransactionSummary getTransactionsByIdPortfolioAndIdSecurityAndClearSecurity(final Integer idPortfolio,
      final Integer idSecuritycurrency, final Date untilDate,
      final Set<SecruityTransactionsReportOptions> secruityTransactionsReportOptions) {
    secruityTransactionsReportOptions.add(SecruityTransactionsReportOptions.CLEAR_TRANSACTION_SECURITY);
    return getTransactionsByIdPortfolioAndIdSecurity(idPortfolio, idSecuritycurrency, untilDate,
        secruityTransactionsReportOptions);
  }

  /**
   * Get transactions for a security over single security account until a certain
   * date.
   *
   * @param data.idSecuritycashAccount
   * @param idSecuritycurrency
   * @param untilDate
   * @param secruityTransactionsReportOptions
   * @return
   */
  public SecurityTransactionSummary getTransactionsByIdSecurityaccountsAndIdSecurityAndClearSecurity(
      final List<Integer> idsSecurityaccount, final Integer idSecuritycurrency, final Date untilDate,
      final Set<SecruityTransactionsReportOptions> secruityTransactionsReportOptions) {
    secruityTransactionsReportOptions.add(SecruityTransactionsReportOptions.CLEAR_TRANSACTION_SECURITY);
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (idsSecurityaccount.size() == 1) {
      return getTransactionsByIdSecurityaccountAndIdSecurity(user.getIdTenant(), idsSecurityaccount.get(0),
          idSecuritycurrency, untilDate, secruityTransactionsReportOptions);
    } else {
      return getTransactionsByIdTenantAndIdSecurity(user.getIdTenant(), idsSecurityaccount, idSecuritycurrency,
          untilDate, secruityTransactionsReportOptions, null);
    }
  }

  /**
   * Returns security accounts with open positions for the required security. Not
   * usable for margin products!
   *
   * @param idTenant
   * @param idSecuritycurrency
   * @param dateString
   * @param before
   * @param excludeIdTransaction
   * @return
   * @throws ParseException
   */
  public SecurityOpenPositionPerSecurityaccount getOpenPositionByIdTenantAndIdSecuritycurrency(final Integer idTenant,
      final Integer idSecuritycurrency, final String dateString, final boolean before,
      final Integer excludeIdTransaction, Integer idOpenMarginTransaction) throws ParseException {

    // We need to convert the date to UTC time zone;
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

    Date untilDate = new SimpleDateFormat("yyyyMMddHHmm").parse(dateString);
    if (before) {
      untilDate = new Date(untilDate.getTime() - 1 + user.getTimezoneOffset() * 60 * 1000);
    }

    final SecurityOpenPositionPerSecurityaccount securityaccountOpenPositionSecurity = new SecurityOpenPositionPerSecurityaccount();

    SecurityTransactionSummary securityTransactionSummary = getTransactionsByIdTenantAndIdSecurity(idTenant, null,
        idSecuritycurrency, untilDate, Collections.<SecruityTransactionsReportOptions>emptySet(),
        idOpenMarginTransaction);

    securityaccountOpenPositionSecurity.securityPositionSummary = securityTransactionSummary.securityPositionSummary;

    final Map<Integer, List<Transaction>> securityTransactionMap = this
        .separateTransactionForSecurityBySecurityaccount(securityTransactionSummary, excludeIdTransaction);

    for (final Map.Entry<Integer, List<Transaction>> entry : securityTransactionMap.entrySet()) {
      securityTransactionSummary = calcSummaryForTransactions(entry.getValue(),
          securityJpaRepository.getReferenceById(idSecuritycurrency), untilDate,
          Collections.<SecruityTransactionsReportOptions>emptySet(), null);
      if (securityTransactionSummary.securityPositionSummary.units != 0.0) {
        securityaccountOpenPositionSecurity.securityaccountOpenPositionUnitsList
            .add(new SecurityaccountOpenPositionUnits(entry.getKey(),
                securityTransactionSummary.securityPositionSummary.getUnits()));
      }
    }
    return securityaccountOpenPositionSecurity;
  }

  private SecurityTransactionSummary getTransactionsByIdTenantAndIdSecurity(final Integer idTenant,
      final List<Integer> idsSecurityaccount, final Integer idSecuritycurrency, final Date untilDate,
      final Set<SecruityTransactionsReportOptions> secruityTransactionsReportOptions, Integer idOpenMarginTransaction) {

    final Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);

    final CompletableFuture<List<Transaction>> transactionsFuture = CompletableFuture
        .supplyAsync(() -> idOpenMarginTransaction != null
            ? transactionJpaRepository.findByIdTenantAndIdTransactionOrConnectedIdTransaction(idTenant,
                idOpenMarginTransaction, idOpenMarginTransaction)
            : idsSecurityaccount == null
                ? transactionJpaRepository.findByIdTenantAndIdSecurity(idTenant, idSecuritycurrency)
                : transactionJpaRepository.findByIdTenantAndSecurityAccountsIdSecurity(idTenant, idsSecurityaccount,
                    idSecuritycurrency));

    final CompletableFuture<List<Object[]>> dateTransactionCurrencyFuture = CompletableFuture.supplyAsync(
        () -> historyquoteJpaRepository.findByIdTenantAndIdSecurityFoCuHistoryquotes(idTenant, idSecuritycurrency));

    final CompletableFuture<List<Currencypair>> currencypairsFuture = CompletableFuture
        .supplyAsync(() -> currencypairJpaRepository.getAllCurrencypairsByTenantInPortfolioAndAccounts(idTenant));

    final CompletableFuture<DateTransactionCurrencypairMap> dateCurrencyMapFuture = CompletableFuture
        .supplyAsync(() -> new DateTransactionCurrencypairMap(tenant.getCurrency(), untilDate,
            dateTransactionCurrencyFuture.join(), currencypairsFuture.join(),
            this.tradingDaysPlusJpaRepository.hasTradingDayBetweenUntilYesterday(DateHelper.getLocalDate(untilDate))));

    return calcSummaryForTransactions(transactionsFuture.join(),
        securityJpaRepository.getReferenceById(idSecuritycurrency), untilDate, secruityTransactionsReportOptions,
        dateCurrencyMapFuture.join());
  }

  private SecurityTransactionSummary getTransactionsByIdPortfolioAndIdSecurity(final Integer idPortfolio,
      final Integer idSecuritycurrency, final Date untilDate,
      final Set<SecruityTransactionsReportOptions> secruityTransactionsReportOptions) {
    final Integer idTenant = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getIdTenant();

    final Portfolio portfolio = portfolioJpaRepository.findByIdTenantAndIdPortfolio(idTenant, idPortfolio);
    if (portfolio != null) {
      final CompletableFuture<List<Transaction>> transactionsFuture = CompletableFuture.supplyAsync(
          () -> transactionJpaRepository.findByIdPortfolioAndIdSecurity(portfolio.getSecurityaccountList().stream()
              .map(Securityaccount::getIdSecuritycashAccount).collect(Collectors.toList()), idSecuritycurrency));

      final CompletableFuture<List<Object[]>> dateTransactionCurrencyFuture = CompletableFuture
          .supplyAsync(() -> historyquoteJpaRepository.findByIdPortfolioAndIdSecurityFoCuHistoryquotes(idPortfolio,
              idSecuritycurrency));
      final CompletableFuture<List<Currencypair>> currencypairsFuture = CompletableFuture.supplyAsync(
          () -> currencypairJpaRepository.getAllCurrencypairsByTenantInPortfolioAndAccounts(portfolio.getIdTenant()));

      final CompletableFuture<DateTransactionCurrencypairMap> dateCurrencyMapFuture = CompletableFuture
          .supplyAsync(() -> new DateTransactionCurrencypairMap(portfolio.getCurrency(), untilDate,
              dateTransactionCurrencyFuture.join(), currencypairsFuture.join(), this.tradingDaysPlusJpaRepository
                  .hasTradingDayBetweenUntilYesterday(DateHelper.getLocalDate(untilDate))));

      return calcSummaryForTransactions(transactionsFuture.join(),
          securityJpaRepository.getReferenceById(idSecuritycurrency), untilDate, secruityTransactionsReportOptions,
          dateCurrencyMapFuture.join());
    } else {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
  }

  private SecurityTransactionSummary getTransactionsByIdSecurityaccountAndIdSecurity(final Integer idTenant,
      final Integer idSecuritycashAccount, final Integer idSecuritycurrency, final Date untilDate,
      final Set<SecruityTransactionsReportOptions> secruityTransactionsReportOptions) {

    final Portfolio portfolio = portfolioJpaRepository
        .findBySecuritycashaccountList_idSecuritycashAccountAndIdTenant(idSecuritycashAccount, idTenant);

    final CompletableFuture<List<Transaction>> transactionsFuture = CompletableFuture.supplyAsync(
        () -> transactionJpaRepository.findByIdSecurityaccountAndIdSecurity(idSecuritycashAccount, idSecuritycurrency));

    final CompletableFuture<List<Object[]>> dateTransactionCurrencyFuture = CompletableFuture
        .supplyAsync(() -> historyquoteJpaRepository
            .findByIdSecurityaccountAndIdSecurityFoCuHistoryquotes(idSecuritycashAccount, idSecuritycurrency));
    final CompletableFuture<List<Currencypair>> currencypairsFuture = CompletableFuture.supplyAsync(
        () -> currencypairJpaRepository.getAllCurrencypairsByTenantInPortfolioAndAccounts(portfolio.getIdTenant()));

    final CompletableFuture<DateTransactionCurrencypairMap> dateCurrencyMapFuture = CompletableFuture
        .supplyAsync(() -> new DateTransactionCurrencypairMap(portfolio.getCurrency(), untilDate,
            dateTransactionCurrencyFuture.join(), currencypairsFuture.join(),
            this.tradingDaysPlusJpaRepository.hasTradingDayBetweenUntilYesterday(DateHelper.getLocalDate(untilDate))));

    return calcSummaryForTransactions(transactionsFuture.join(),
        securityJpaRepository.getReferenceById(idSecuritycurrency), untilDate, secruityTransactionsReportOptions,
        dateCurrencyMapFuture.join());
  }

  private Map<Integer, List<Transaction>> separateTransactionForSecurityBySecurityaccount(
      final SecurityTransactionSummary securityTransactionSummary, final Integer excludeIdTransaction) {
    final Map<Integer, List<Transaction>> securityTransactionMap = new HashMap<>();
    securityTransactionSummary.transactionPositionList.forEach(securityTransactionPosition -> {

      if (securityTransactionPosition.transaction.getIdTransaction() != null
          && !securityTransactionPosition.transaction.getIdTransaction().equals(excludeIdTransaction)) {
        final Integer idSecurityaccount = securityTransactionPosition.transaction.getIdSecurityaccount();

        List<Transaction> transactions = securityTransactionMap.computeIfAbsent(idSecurityaccount,
            key -> new ArrayList<>());
        transactions.add(securityTransactionPosition.transaction);
      }
    });
    return securityTransactionMap;
  }

  private SecurityTransactionSummary calcSummaryForTransactions(final List<Transaction> transactions,
      final Security security, final Date untilDate,
      final Set<SecruityTransactionsReportOptions> secruityTransactionsReportOptions,
      final DateTransactionCurrencypairMap dateCurrencyMap) {

    final SecurityTransactionSummary securityTransactionSummary = new SecurityTransactionSummary(
        securityJpaRepository.findById(security.getIdSecuritycurrency()).get(),
        (dateCurrencyMap != null) ? dateCurrencyMap.getMainCurrency() : null,
        globalparametersJpaRepository.getCurrencyPrecision());
    final boolean excludeDivTaxcost = tenantJpaRepository.isExcludeDividendTaxcost();

    final Map<Integer, List<Securitysplit>> securitySplitMap = this.securitysplitJpaRepository
        .getSecuritysplitMapByIdSecuritycurrency(security.getIdSecuritycurrency());

    List<Securitysplit> securitysplits = securitySplitMap.get(security.getIdSecuritycurrency());
    if (securitysplits != null) {
      Securitysplit[] securitysplitsArray = new Securitysplit[securitysplits.size()];
      securitysplits.toArray(securitysplitsArray);
      securityTransactionSummary.securityPositionSummary.securitycurrency.setSplitPropose(securitysplitsArray);
    }

    securityCalcService.calcTransactions(security, excludeDivTaxcost, securityTransactionSummary, securitySplitMap,
        transactions, untilDate, dateCurrencyMap);

    if (securityTransactionSummary.securityPositionSummary.units != 0.0
        && secruityTransactionsReportOptions.contains(SecruityTransactionsReportOptions.CLEAR_TRANSACTION_SECURITY)) {

      securityJpaRepository.calcGainLossBasedOnDateOrNewestPrice(securityTransactionSummary.securityPositionSummary,
          new IPositionCloseOnLatestPrice<Security, SecurityPositionSummary>() {

            @Override
            public void calculatePositionClose(final SecurityPositionSummary securityPositionSummary,
                final Double lastPrice) {
              securityCalcService.createHypotheticalSellTransaction(securityPositionSummary, lastPrice,
                  securitySplitMap, dateCurrencyMap, securityTransactionSummary);
            }
          }, untilDate);

    }
    if (secruityTransactionsReportOptions.contains(SecruityTransactionsReportOptions.CLEAR_TRANSACTION_SECURITY)) {
      securityTransactionSummary.setSecurityInTransactionToNull();
    }

    if (secruityTransactionsReportOptions.contains(SecruityTransactionsReportOptions.QUTATION_SPLIT_CORRECTION)) {
      quotationSplitCorrection(security.getIdSecuritycurrency(), securityTransactionSummary, securitySplitMap);
    }

    return securityTransactionSummary;
  }

  private void quotationSplitCorrection(final Integer idSecuritycurrency,
      final SecurityTransactionSummary securityTransactionSummary,
      final Map<Integer, List<Securitysplit>> securitySplitMap) {

    final List<Securitysplit> securitySplits = securitySplitMap.get(idSecuritycurrency);
    if (securitySplits != null) {
      final ListIterator<SecurityTransactionPosition> li = securityTransactionSummary.transactionPositionList
          .listIterator(securityTransactionSummary.transactionPositionList.size());

      while (li.hasPrevious()) {
        final SecurityTransactionPosition securityTransactionPosition = li.previous();
        final Transaction transaction = securityTransactionPosition.transaction;
        if (transaction.getTransactionType() == TransactionType.ACCUMULATE
            || transaction.getTransactionType() == TransactionType.REDUCE) {

          int splitIndex = securitySplits.size() - 1;
          double factor = 1.0;
          while (splitIndex >= 0
              && transaction.getTransactionTime().getTime() < securitySplits.get(splitIndex).getSplitDate().getTime()) {
            final Securitysplit securitysplit = securitySplits.get(splitIndex);
            factor /= (double) securitysplit.getToFactor() / securitysplit.getFromFactor();
            splitIndex--;
          }
          securityTransactionPosition.quotationSplitCorrection = transaction.getQuotation() * factor;
        }
      }
    }
  }

}
