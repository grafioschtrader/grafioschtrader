package grafioschtrader.reports;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

import grafiosch.BaseConstants;
import grafiosch.common.DateHelper;
import grafiosch.entities.User;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityOpenPositionPerSecurityaccount;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.securityaccount.SecurityaccountOpenPositionUnits;
import grafioschtrader.reportviews.transaction.SecurityTransactionPosition;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.IPositionCloseOnLatestPrice;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TransactionType;

/**
 * Creates a Report for a single security with all its transactions.
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
  private GlobalparametersService globalparametersService;

  /**
   * Configuration options for transaction report generation.
   */
  public enum SecruityTransactionsReportOptions {
    /**
     * Set security of Transaction to null. It is for the reduction the json footprint
     */
    CLEAR_TRANSACTION_SECURITY,
    /**
     * Some charts of historical security data includes transaction (sell, buy, dividend) data. Normally all history
     * quote data are adjusted to splits. In such case the transaction quotation must be corrected as well.
     */
    QUTATION_SPLIT_CORRECTION
  }

  /**
   * Gets transaction summary for a security across all portfolios in a tenant.
   * 
   * @param idTenant the tenant identifier
   * @param idSecuritycurrency the security identifier  
   * @param untilDate the cutoff date for transactions (inclusive)
   * @param secruityTransactionsReportOptions processing options for the report
   * @return transaction summary with security objects cleared from transactions
   */
  public SecurityTransactionSummary getTransactionsByIdTenantAndIdSecurityAndClearSecurity(final Integer idTenant,
      final Integer idSecuritycurrency, final Date untilDate,
      final Set<SecruityTransactionsReportOptions> secruityTransactionsReportOptions) {
    secruityTransactionsReportOptions.add(SecruityTransactionsReportOptions.CLEAR_TRANSACTION_SECURITY);
    return getTransactionsByIdTenantAndIdSecurity(idTenant, null, idSecuritycurrency, untilDate,
        secruityTransactionsReportOptions, null);
  }

  /**
   * Gets transaction summary for a security within a specific portfolio.
   * 
   * @param idPortfolio the portfolio identifier
   * @param idSecuritycurrency the security identifier
   * @param untilDate the cutoff date for transactions (inclusive)
   * @param secruityTransactionsReportOptions processing options for the report
   * @return transaction summary with security objects cleared from transactions
   */
  public SecurityTransactionSummary getTransactionsByIdPortfolioAndIdSecurityAndClearSecurity(final Integer idPortfolio,
      final Integer idSecuritycurrency, final Date untilDate,
      final Set<SecruityTransactionsReportOptions> secruityTransactionsReportOptions) {
    secruityTransactionsReportOptions.add(SecruityTransactionsReportOptions.CLEAR_TRANSACTION_SECURITY);
    return getTransactionsByIdPortfolioAndIdSecurity(idPortfolio, idSecuritycurrency, untilDate,
        secruityTransactionsReportOptions);
  }

  /**
   * Gets transaction summary for a security across specified security accounts.
   * 
   * @param idsSecurityaccount list of security account identifiers
   * @param idSecuritycurrency the security identifier
   * @param untilDate the cutoff date for transactions (inclusive)
   * @param secruityTransactionsReportOptions processing options for the report
   * @return transaction summary with security objects cleared from transactions
   */
  public SecurityTransactionSummary getTransactionsByIdSecurityaccountsAndIdSecurityAndClearSecurity(
      final List<Integer> idsSecurityaccount, final Integer idSecuritycurrency, final Date untilDate,
      final Set<SecruityTransactionsReportOptions> secruityTransactionsReportOptions) {
    secruityTransactionsReportOptions.add(SecruityTransactionsReportOptions.CLEAR_TRANSACTION_SECURITY);
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (idsSecurityaccount.size() == 1) {
      return getTransactionsByIdSecurityaccountAndIdSecurity(user.getIdTenant(), idsSecurityaccount.getFirst(),
          idSecuritycurrency, untilDate, secruityTransactionsReportOptions);
    } else {
      return getTransactionsByIdTenantAndIdSecurity(user.getIdTenant(), idsSecurityaccount, idSecuritycurrency,
          untilDate, secruityTransactionsReportOptions, null);
    }
  }

  /**
   * Returns security accounts with open positions for a specific security at a given point in time.
   * 
   * <p>Calculates which security accounts hold open positions for the specified security,
   * excluding weekends and considering timezone offsets. Not suitable for margin products.</p>
   * 
   * @param idTenant the tenant identifier
   * @param idSecuritycurrency the security identifier
   * @param dateString date in yyyyMMddHHmm format
   * @param before if true, calculates positions just before the specified time
   * @param excludeIdTransaction transaction ID to exclude from calculations
   * @param idOpenMarginTransaction margin transaction ID for position tracking
   * @return open positions grouped by security account
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

  /**
   * Loads and processes transactions for a security at tenant level with optional filtering.
   * 
   * <p>Uses concurrent loading of transactions, currency data, and exchange rates for performance.
   * Supports filtering by security accounts or margin transaction relationships.</p>
   * 
   * @param idTenant the tenant identifier
   * @param idsSecurityaccount optional list of security accounts to filter by
   * @param idSecuritycurrency the security identifier
   * @param untilDate the cutoff date for transactions
   * @param secruityTransactionsReportOptions processing options
   * @param idOpenMarginTransaction optional margin transaction for related position tracking
   * @return comprehensive transaction summary with position and performance data
   */
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

  /**
   * Loads and processes transactions for a security within a specific portfolio.
   * 
   * @param idPortfolio the portfolio identifier
   * @param idSecuritycurrency the security identifier
   * @param untilDate the cutoff date for transactions
   * @param secruityTransactionsReportOptions processing options
   * @return transaction summary for the portfolio scope
   * @throws SecurityException if user lacks access to the portfolio
   */
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
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
  }

  /**
   * Loads and processes transactions for a security within a specific security account.
   * 
   * @param idTenant the tenant identifier
   * @param idSecuritycashAccount the security account identifier
   * @param idSecuritycurrency the security identifier
   * @param untilDate the cutoff date for transactions
   * @param secruityTransactionsReportOptions processing options
   * @return transaction summary for the security account scope
   */
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

  /**
   * Groups transactions by security account for separate position analysis.
   * 
   * @param securityTransactionSummary the transaction summary to process
   * @param excludeIdTransaction transaction ID to exclude from grouping
   * @return map of security account ID to transaction list
   */
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

  /**
   * Calculates comprehensive transaction summary with position analysis and performance metrics.
   * 
   * <p>Processes all transactions for a security, applying stock splits, currency conversions,
   * and calculating current positions. Optionally generates hypothetical sell scenarios and
   * applies split corrections for charting purposes.</p>
   * 
   * @param transactions the list of transactions to process
   * @param security the security master data
   * @param untilDate the cutoff date for calculations
   * @param secruityTransactionsReportOptions processing options
   * @param dateCurrencyMap currency conversion rates and trading day information
   * @return comprehensive transaction summary with positions and performance data
   */
  private SecurityTransactionSummary calcSummaryForTransactions(final List<Transaction> transactions,
      final Security security, final Date untilDate,
      final Set<SecruityTransactionsReportOptions> secruityTransactionsReportOptions,
      final DateTransactionCurrencypairMap dateCurrencyMap) {

    final SecurityTransactionSummary securityTransactionSummary = new SecurityTransactionSummary(
        securityJpaRepository.findById(security.getIdSecuritycurrency()).get(),
        (dateCurrencyMap != null) ? dateCurrencyMap.getMainCurrency() : null,
        globalparametersService.getCurrencyPrecision());
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
    if ((securityTransactionSummary.securityPositionSummary.units != 0.0
        || !securityTransactionSummary.transactionPositionList.isEmpty())
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
      if (security.isMarginInstrument()) {
        Collections.sort(securityTransactionSummary.transactionPositionList,
            Comparator.comparing(tp -> tp.transaction.getTransactionTime()));
      }
    }
    return securityTransactionSummary;
  }

  /**
   * Adjusts transaction quotations to account for historical stock splits.
   * 
   * <p>When historical price data is split-adjusted, transaction quotations must be corrected
   * to maintain consistency for charting and analysis. This method applies the cumulative split
   * factor to each transaction's quotation based on splits that occurred after the transaction date.</p>
   * 
   * @param idSecuritycurrency the security identifier
   * @param securityTransactionSummary the transaction summary to modify
   * @param securitySplitMap map of security splits by security ID
   */
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
