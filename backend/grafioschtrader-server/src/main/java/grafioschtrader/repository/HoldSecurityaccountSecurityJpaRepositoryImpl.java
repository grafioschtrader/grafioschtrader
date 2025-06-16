package grafioschtrader.repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.common.DateHelper;
import grafiosch.entities.User;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.MissingQuotesWithSecurities;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.HoldSecurityaccountSecurity;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Securitysplit.SplitFactorAfterBefore;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.FromToCurrency;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository.DateSecurityQuoteMissing;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository.IHoldSecuritySplitTransactionBySecurity;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository.ITransactionSecuritySplit;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository.TransactionSecuritySplit;
import grafioschtrader.repository.helper.HoldingsHelper;
import grafioschtrader.types.TransactionType;

/**
 * Implementation of custom repository methods for managing security holdings and position calculations.
 * 
 * <p>
 * <strong>Security Holdings Management:</strong>
 * </p>
 * <p>
 * This class handles the creation and maintenance of time-based security holding records that track position changes
 * from buy/sell transactions, corporate actions, and stock splits. Holdings are automatically updated when securities
 * transactions occur.
 * </p>
 * 
 * <p>
 * <strong>Holdings Impact Scenarios:</strong>
 * </p>
 * <ul>
 * <li>When a tenant buys or sells a security</li>
 * <li>When a tenant's global main currency or portfolio currency changes</li>
 * <li>When security splits occur (affects all tenants holding the security)</li>
 * </ul>
 * 
 * <p>
 * <strong>Margin vs. Regular Transactions:</strong>
 * </p>
 * <p>
 * Margin transactions are handled differently - every single transaction is processed individually, while regular
 * transactions use combined daily results from database queries grouped by security and security account.
 * </p>
 * 
 * <p>
 * <strong>Position Management:</strong>
 * </p>
 * <p>
 * Opening and closing a position within the same day will not produce a holding record, as the net position change is
 * zero. Holdings are only created when there is a non-zero position at the end of processing.
 * </p>
 */
public class HoldSecurityaccountSecurityJpaRepositoryImpl implements HoldSecurityaccountSecurityJpaRepositoryCustom {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Override
  @Transactional
  @Modifying
  public void createSecurityHoldingsEntireForAllTenant() {
    List<Tenant> tenants = tenantJpaRepository.findAll();
    tenants.stream().forEach(this::createSecurityHoldingsEntireByTenant);
  }

  @Transactional
  @Modifying
  @Override
  public void createSecurityHoldingsEntireByTenant(Integer idTenant) {
    long startTime = System.currentTimeMillis();
    Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);
    createSecurityHoldingsEntireByTenant(tenant);
    log.debug("End - HoldSecurityaccountSecurity: {}", System.currentTimeMillis() - startTime);
  }

  /**
   * Creates complete security holdings for a specific tenant entity.
   * 
   * <p>
   * This method delegates to portfolio-level processing, loading currency conversion data and security split
   * information needed for accurate position calculations.
   * </p>
   * 
   * @param tenant the tenant entity for which to rebuild holdings
   */
  private void createSecurityHoldingsEntireByTenant(Tenant tenant) {
    createSecurityHoldingsForTimeFrameByPortfolios(tenant.getPortfolioList(), tenant.getCurrency(),
        loadCurrencypairSecuritySplit(tenant.getIdTenant()));
  }

  @Override
  public void adjustSecurityHoldingForSecurityaccountAndSecurity(Securityaccount securityaccount,
      Transaction transaction, boolean isAdded) {
    long startTime = System.currentTimeMillis();
    Tenant tenant = tenantJpaRepository.getReferenceById(securityaccount.getIdTenant());
    loadForSecurityHoldingsBySecurityaccountAndSecurity(securityaccount, transaction, tenant.getCurrency(),
        securityaccount.getPortfolio().getCurrency(), loadCurrencypairSecuritySplit(securityaccount.getIdTenant()),
        isAdded);
    log.debug("End - HoldSecurityaccountSecurity: {}", System.currentTimeMillis() - startTime);
  }

  @Override
  @Transactional
  public void rebuildHoldingsForSecurity(Integer idSecuritycurrency) {
    Optional<Security> securityOpt = securityJpaRepository.findById(idSecuritycurrency);

    if (securityOpt.isPresent()) {
      Security security = securityOpt.get();
      if (security.isMarginInstrument()) {
        Map<Integer, Transaction> marginTransactionMap = transactionJpaRepository
            .findBySecurity_idSecuritycurrency(security.getIdSecuritycurrency()).stream()
            .collect(Collectors.toMap(Transaction::getIdTransaction, Function.identity()));
        List<IHoldSecuritySplitTransactionBySecurity> hstbsList = holdSecurityaccountSecurityRepository
            .getHoldSecuritySplitMarginTransactionBySecurity(security.getIdSecuritycurrency());
        rebuildHoldingsForSecurity(security, hstbsList, marginTransactionMap);

      } else {
        List<IHoldSecuritySplitTransactionBySecurity> hstbsList = holdSecurityaccountSecurityRepository
            .getHoldSecuritySplitTransactionBySecurity(security.getIdSecuritycurrency());
        rebuildHoldingsForSecurity(security, hstbsList, null);
      }
    }
  }

  /**
   * Rebuilds holdings for a specific security across all accounts and tenants.
   * 
   * <p>
   * This method processes holdings grouped by tenant and security account, handling both margin and regular securities.
   * It removes existing holdings before recreating them with updated data.
   * </p>
   * 
   * <p>
   * <strong>Processing Strategy:</strong>
   * </p>
   * <ul>
   * <li>Groups transactions by tenant and security account</li>
   * <li>Creates currency and split context for the security</li>
   * <li>Processes transactions chronologically with margin handling</li>
   * <li>Maintains time-frame continuity across account changes</li>
   * </ul>
   * 
   * @param security             the security for which to rebuild holdings
   * @param hstbsList            list of holdings, splits, and transactions for the security
   * @param marginTransactionMap map of margin transactions (null for regular securities)
   */
  private void rebuildHoldingsForSecurity(Security security, List<IHoldSecuritySplitTransactionBySecurity> hstbsList,
      Map<Integer, Transaction> marginTransactionMap) {
    Integer idTenant = null;
    Integer idSecurityaccount = null;
    Integer idPortfolio = null;

    HoldPositionTimeFrameSecurity holdPositionTimeFrameSecuriy = null;
    CurrencypairSecuritySplit css = createCurrencypairSecuritySplit(security);

    for (int i = 0; i < hstbsList.size(); i++) {
      IHoldSecuritySplitTransactionBySecurity hstbs = hstbsList.get(i);
      if (idTenant == null || !hstbs.getIdTenant().equals(idTenant) || idSecurityaccount == null
          || !hstbs.getIdSecurityaccount().equals(idSecurityaccount)) {

        if (idSecurityaccount != null) {
          holdSecurityaccountSecurityRepository.deleteByHsskIdSecuritycashAccountAndHsskIdSecuritycurrency(
              idSecurityaccount, security.getIdSecuritycurrency());
          holdPositionTimeFrameSecuriy.prepareNextSecurityaccountAndSaveAll(this.holdSecurityaccountSecurityRepository);
        }
        holdPositionTimeFrameSecuriy = new HoldPositionTimeFrameSecurity(currencypairJpaRepository,
            hstbs.getTenantCurrency(), hstbs.getPorfolioCurrency(), css, marginTransactionMap);
        idTenant = hstbs.getIdTenant();
        idSecurityaccount = hstbs.getIdSecurityaccount();
        idPortfolio = hstbs.getIdPortfolio();
      }
      TransactionSecuritySplit transactionSecuritySplit = new TransactionSecuritySplit(null,
          security.getIdSecuritycurrency(), hstbs.getTsDate(), hstbs.getFactorUnits(), hstbs.getIdTransactionMargin(),
          hstbs.getIdPortfolio() == null ? null : security.getCurrency());

      boolean isNextMarginSameDate = security.isMarginInstrument() && (i + 1) < hstbsList.size()
          ? isNextMarginSameDate(hstbs, hstbsList.get(i + 1), marginTransactionMap)
          : false;
      Transaction marginTransaction = security.isMarginInstrument()
          ? marginTransactionMap.get(hstbs.getIdTransactionMargin())
          : null;

      holdPositionTimeFrameSecuriy.addTransactionOrSplit(idTenant, idPortfolio, idSecurityaccount,
          transactionSecuritySplit, marginTransaction, isNextMarginSameDate);
    }
    if (idSecurityaccount != null) {
      holdSecurityaccountSecurityRepository.deleteByHsskIdSecuritycashAccountAndHsskIdSecuritycurrency(
          idSecurityaccount, security.getIdSecuritycurrency());
      holdPositionTimeFrameSecuriy.prepareNextSecurityaccountAndSaveAll(this.holdSecurityaccountSecurityRepository);
    }
  }

  /**
   * Determines if the next margin transaction occurs on the same date as the current one.
   * 
   * <p>
   * This method helps optimize margin transaction processing by identifying when multiple margin transactions for the
   * same security account occur on the same trading day.
   * </p>
   * 
   * @param hstbs                current holding/split/transaction record
   * @param hstbsNext            next holding/split/transaction record
   * @param marginTransactionMap map of margin transactions for lookup
   * @return true if both transactions are on the same date for the same account
   */
  private boolean isNextMarginSameDate(IHoldSecuritySplitTransactionBySecurity hstbs,
      IHoldSecuritySplitTransactionBySecurity hstbsNext, Map<Integer, Transaction> marginTransactionMap) {
    if (hstbs.getIdPortfolio() != null && hstbsNext.getIdPortfolio() != null
        && hstbs.getIdSecurityaccount().equals(hstbsNext.getIdSecurityaccount())) {
      Transaction marginTransaction = marginTransactionMap.get(hstbs.getIdTransactionMargin());
      Transaction marginTransactionNext = marginTransactionMap.get(hstbsNext.getIdTransactionMargin());
      return DateHelper.isSameDay(marginTransaction.getTransactionTime(), marginTransactionNext.getTransactionTime());
    }
    return false;
  }

  /**
   * Creates currency conversion and security split context for a specific security.
   * 
   * <p>
   * This method loads the currency pairs and stock splits needed for accurate position calculations and currency
   * conversions for the security.
   * </p>
   * 
   * @param security the security for which to create the context
   * @return context object containing currency pairs and split information
   */
  private CurrencypairSecuritySplit createCurrencypairSecuritySplit(Security security) {
    List<Securitysplit> securitysplits = securitysplitJpaRepository
        .findByIdSecuritycurrencyOrderBySplitDateAsc(security.getIdSecuritycurrency());
    Map<Integer, List<Securitysplit>> securitysplitMap = new HashMap<>();
    securitysplitMap.put(security.getIdSecuritycurrency(), securitysplits);
    List<Currencypair> currencypairs = currencypairJpaRepository.findByFromCurrency(security.getCurrency());
    return new CurrencypairSecuritySplit(HoldingsHelper.transformToCurrencypairMapWithFromCurrencyAsKey(currencypairs),
        securitysplitMap);
  }

  /**
   * Creates security holdings for all portfolios within a tenant.
   * 
   * <p>
   * This method processes each portfolio and its security accounts to create complete holdings. It delegates to
   * security account level processing with the necessary currency and split context.
   * </p>
   * 
   * @param allTenantPortfolios           list of portfolios for the tenant
   * @param tenantCurrency                the tenant's base currency
   * @param loadCurrencypairSecuritySplit currency and split context for conversions
   */
  private void createSecurityHoldingsForTimeFrameByPortfolios(final List<Portfolio> allTenantPortfolios,
      final String tenantCurrency, CurrencypairSecuritySplit loadCurrencypairSecuritySplit) {
    allTenantPortfolios.forEach(portfolio -> {
      portfolio.getSecurityaccountList()
          .forEach(securityaccount -> loadForSecurityHoldingsBySecurityaccount(portfolio.getIdTenant(),
              portfolio.getIdPortfolio(), securityaccount.getIdSecuritycashAccount(), tenantCurrency,
              portfolio.getCurrency(), loadCurrencypairSecuritySplit));
    });
  }

  /**
   * Loads currency conversion and security split data for a tenant.
   * 
   * @param idTenant the tenant identifier
   * @return context object with currency pairs and security splits for the tenant
   */
  private CurrencypairSecuritySplit loadCurrencypairSecuritySplit(Integer idTenant) {
    return new CurrencypairSecuritySplit(
        HoldingsHelper.getUsedCurrencypiarsByIdTenant(idTenant, currencypairJpaRepository),
        securitysplitJpaRepository.getSecuritysplitMapByIdTenant(idTenant));
  }

  /**
   * Create holdings for a security account of a portfolio and tenant.
   * 
   * <p>
   * This method uses concurrent processing to load transaction and margin data simultaneously, then delegates to
   * holdings creation with the loaded data.
   * </p>
   * 
   * @param idTenant              the tenant identifier
   * @param idPortfolio           the portfolio identifier
   * @param idSecuritycashAccount the security account identifier
   * @param tenantCurrency        the tenant's base currency
   * @param portfolioCurrency     the portfolio's base currency
   * @param css                   currency and split context for calculations
   */
  private void loadForSecurityHoldingsBySecurityaccount(Integer idTenant, Integer idPortfolio,
      Integer idSecuritycashAccount, String tenantCurrency, String portfolioCurrency, CurrencypairSecuritySplit css) {

    holdSecurityaccountSecurityRepository.removeAllByIdSecuritycashAccount(idSecuritycashAccount);

    final CompletableFuture<List<ITransactionSecuritySplit>> transSplitCF = CompletableFuture
        .supplyAsync(() -> holdSecurityaccountSecurityRepository
            .getBuySellTransWithSecuritySplitByIdSecurityaccount(idSecuritycashAccount));

    final CompletableFuture<Map<Integer, Transaction>> marginTransactionCF = CompletableFuture
        .supplyAsync(() -> getMarginTransactionByIdSecurityaccount(idSecuritycashAccount));

    try {
      this.createSecurityHoldingsBySecurityaccount(idTenant, idPortfolio, idSecuritycashAccount, tenantCurrency,
          portfolioCurrency, css, transSplitCF.get(), marginTransactionCF.get());
    } catch (InterruptedException | ExecutionException ex) {
      ex.printStackTrace();
      throw new RuntimeException(ex);
    }

  }

  /**
   * Creates security holdings for a single security account using loaded transaction data.
   * 
   * <p>
   * This method processes transactions grouped by security, maintaining position continuity and handling both regular
   * and margin transactions with proper time-frame management.
   * </p>
   * 
   * <p>
   * <strong>Processing Flow:</strong>
   * </p>
   * <ul>
   * <li>Processes transactions chronologically within each security</li>
   * <li>Handles security changes by preparing the previous security</li>
   * <li>Manages margin transaction grouping for same-day trades</li>
   * <li>Creates holdings only for non-zero positions</li>
   * </ul>
   * 
   * @param idTenant                     the tenant identifier
   * @param idPortfolio                  the portfolio identifier
   * @param idSecuritycashAccount        the security account identifier
   * @param tenantCurrency               the tenant's base currency
   * @param portfolioCurrency            the portfolio's base currency
   * @param css                          currency and split context for calculations
   * @param transactionSecuritySplitList list of transactions and splits for the account
   * @param marginTransactionMap         map of margin transactions for reference
   */
  private void createSecurityHoldingsBySecurityaccount(Integer idTenant, Integer idPortfolio,
      Integer idSecuritycashAccount, String tenantCurrency, String portfolioCurrency, CurrencypairSecuritySplit css,
      List<ITransactionSecuritySplit> transactionSecuritySplitList, Map<Integer, Transaction> marginTransactionMap) {
    Integer lastIdSecuritycurrency = null;
    HoldPositionTimeFrameSecurity holdPositionTimeFrameSecurity = new HoldPositionTimeFrameSecurity(
        this.currencypairJpaRepository, tenantCurrency, portfolioCurrency, css, marginTransactionMap);

    for (int i = 0; i < transactionSecuritySplitList.size(); i++) {
      ITransactionSecuritySplit tss = transactionSecuritySplitList.get(i);
      if (lastIdSecuritycurrency == null) {
        lastIdSecuritycurrency = tss.getIdSecuritycurrency();

      } else if (!lastIdSecuritycurrency.equals(tss.getIdSecuritycurrency())) {
        // Security has changed

        holdPositionTimeFrameSecurity.prepareNextSecurity();
        lastIdSecuritycurrency = tss.getIdSecuritycurrency();
      }
      Transaction marginTransaction = tss.getIdTransactionMargin() == null ? null
          : marginTransactionMap.get(tss.getIdTransactionMargin());

      boolean isNextMarginSameDate = marginTransaction != null
          ? isNextMarginAndSameDate(marginTransaction, tss, transactionSecuritySplitList, i, marginTransactionMap)
          : false;

      holdPositionTimeFrameSecurity.addTransactionOrSplit(idTenant, idPortfolio, idSecuritycashAccount, tss,
          marginTransaction, isNextMarginSameDate);
    }
    holdPositionTimeFrameSecurity.prepareNextSecurityaccountAndSaveAll(holdSecurityaccountSecurityRepository);

  }

  /**
   * Checks if the next margin transaction in the list occurs on the same date as the current one.
   * 
   * @param marginTransaction            current margin transaction
   * @param tss                          current transaction/split record
   * @param transactionSecuritySplitList full list of transactions for comparison
   * @param actElement                   current index in the list
   * @param marginTransactionMap         map for looking up margin transactions
   * @return true if the next transaction is for the same security on the same date
   */
  private boolean isNextMarginAndSameDate(Transaction marginTransaction, ITransactionSecuritySplit tss,
      List<ITransactionSecuritySplit> transactionSecuritySplitList, int actElement,
      Map<Integer, Transaction> marginTransactionMap) {
    if (actElement + 1 < transactionSecuritySplitList.size()) {
      ITransactionSecuritySplit tssNext = transactionSecuritySplitList.get(actElement + 1);
      if (tssNext.getIdSecuritycurrency().equals(tss.getIdSecuritycurrency())) {
        // Must be a margin
        Transaction marginTransactionNext = marginTransactionMap.get(tssNext.getIdTransactionMargin());
        return marginTransactionNext != null
            && DateHelper.isSameDay(marginTransaction.getTransactionTime(), marginTransactionNext.getTransactionTime());
      }
    }
    return false;
  }

  /**
   * Retrieves margin transactions for a specific security account.
   * 
   * @param idSecuritycashAccount the security account identifier
   * @return map of transaction IDs to margin transaction entities
   */
  private Map<Integer, Transaction> getMarginTransactionByIdSecurityaccount(Integer idSecuritycashAccount) {
    return transactionJpaRepository.getMarginTransactionMapForSecurityaccount(idSecuritycashAccount).stream()
        .collect(Collectors.toMap(Transaction::getIdTransaction, Function.identity()));
  }

  /**
   * Handles incremental holdings adjustment for a specific security account and security.
   * 
   * <p>
   * This method provides efficient incremental updates by removing existing holdings for the security and recreating
   * them with updated transaction data. It uses concurrent processing for data loading optimization.
   * </p>
   * 
   * @param securityaccount   the security account containing the affected security
   * @param transaction       the transaction that triggered the adjustment
   * @param tenantCurrency    the tenant's base currency
   * @param portfolioCurrency the portfolio's base currency
   * @param css               currency and split context for calculations
   * @param isAdded           true if the transaction was added, false if removed
   */
  private void loadForSecurityHoldingsBySecurityaccountAndSecurity(Securityaccount securityaccount,
      Transaction transaction, String tenantCurrency, String portfolioCurrency, CurrencypairSecuritySplit css,
      boolean isAdded) {
    CompletableFuture<Map<Integer, Transaction>> marginTransactionCF = null;
    holdSecurityaccountSecurityRepository.deleteByHsskIdSecuritycashAccountAndHsskIdSecuritycurrency(
        securityaccount.getIdSecuritycashAccount(), transaction.getSecurity().getIdSecuritycurrency());
    final CompletableFuture<List<ITransactionSecuritySplit>> transSplitCF = getMarginNormalTransactions(securityaccount,
        transaction);
    if (transaction.isMarginInstrument()) {
      marginTransactionCF = CompletableFuture.supplyAsync(() -> getMarginTransactionByIdSecurityaccountAndSecurity(
          securityaccount.getIdSecuritycashAccount(), transaction.getSecurity().getIdSecuritycurrency()));
    }
    try {
      createSecurityHoldingsForSecurityaccountAndSecurity(securityaccount, transaction, tenantCurrency,
          portfolioCurrency, css, transSplitCF.get(), (marginTransactionCF == null) ? null : marginTransactionCF.get(),
          isAdded);
    } catch (InterruptedException | ExecutionException ex) {
      ex.printStackTrace();
      throw new RuntimeException(ex);
    }
  }

  /**
   * Loads transaction data concurrently based on whether the security is a margin instrument.
   * 
   * @param securityaccount the security account
   * @param transaction     the reference transaction
   * @return CompletableFuture with transaction and split data
   */
  private CompletableFuture<List<ITransactionSecuritySplit>> getMarginNormalTransactions(
      Securityaccount securityaccount, Transaction transaction) {
    if (transaction.isMarginInstrument()) {
      return CompletableFuture.supplyAsync(() -> holdSecurityaccountSecurityRepository
          .getBuySellTransWithSecuritySplitByIdSecurityaccountAndSecurityMargin(
              securityaccount.getIdSecuritycashAccount(), transaction.getSecurity().getIdSecuritycurrency()));
    } else {
      return CompletableFuture.supplyAsync(
          () -> holdSecurityaccountSecurityRepository.getBuySellTransWithSecuritySplitByIdSecurityaccountAndSecurity(
              securityaccount.getIdSecuritycashAccount(), transaction.getSecurity().getIdSecuritycurrency()));
    }
  }

  /**
   * Creates security holdings for a specific security account and security combination.
   * 
   * <p>
   * This method handles both new transaction additions and modifications by:
   * </p>
   * <ul>
   * <li>Merging existing transactions with the new/modified transaction</li>
   * <li>Sorting transactions chronologically</li>
   * <li>Processing margin transactions with proper grouping</li>
   * <li>Creating holdings with accurate position calculations</li>
   * </ul>
   * 
   * @param securityaccount               the security account
   * @param transaction                   the transaction being added or modified
   * @param tenantCurrency                the tenant's base currency
   * @param portfolioCurrency             the portfolio's base currency
   * @param css                           currency and split context
   * @param iTransactionSecuritySplitList existing transactions and splits
   * @param marginTransactionMap          margin transaction lookup map
   * @param isAdded                       true if transaction is being added, false if removed
   */
  private void createSecurityHoldingsForSecurityaccountAndSecurity(Securityaccount securityaccount,
      Transaction transaction, String tenantCurrency, String portfolioCurrency, CurrencypairSecuritySplit css,
      List<ITransactionSecuritySplit> iTransactionSecuritySplitList, Map<Integer, Transaction> marginTransactionMap,
      boolean isAdded) {

    HoldPositionTimeFrameSecurity holdPositionTimeFrameSecurity = new HoldPositionTimeFrameSecurity(
        currencypairJpaRepository, tenantCurrency, portfolioCurrency, css, marginTransactionMap);

    Comparator<ITransactionSecuritySplit> transactionTimeComparator = (ITransactionSecuritySplit tss1,
        ITransactionSecuritySplit tss2) -> tss1.getTsDate().compareTo(tss2.getTsDate());

    List<ITransactionSecuritySplit> transactionSecuritySplitList = new ArrayList<>();

    // Add existing transactions
    iTransactionSecuritySplitList.forEach(itss -> transactionSecuritySplitList
        .add(new TransactionSecuritySplit(itss.getIdTransaction(), itss.getIdSecuritycurrency(), itss.getTsDate(),
            itss.getFactorUnits(), itss.getIdTransactionMargin(), itss.getCurrency())));

    TransactionSecuritySplit tssNew = new TransactionSecuritySplit(transaction.getIdTransaction(),
        transaction.getSecurity().getIdSecuritycurrency(),
        transaction.getTransactionTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
        (transaction.getTransactionType() == TransactionType.ACCUMULATE ? 1 : -1) * transaction.getUnits(), null,
        transaction.getSecurity().getCurrency());

    transactionSecuritySplitList.removeIf(t -> transaction.getIdTransaction().equals(t.getIdTransaction()));
    // Insert new or modified transaction
    if (isAdded) {
      transactionSecuritySplitList.add(tssNew);
    }
    Collections.sort(transactionSecuritySplitList, transactionTimeComparator);
    if (transaction.isMarginInstrument()) {
      marginTransactionMap.put(transaction.getIdTransaction(), transaction);
    }

    for (int i = 0; i < transactionSecuritySplitList.size(); i++) {
      ITransactionSecuritySplit tss = transactionSecuritySplitList.get(i);
      Transaction marginTransaction = tss.getIdTransactionMargin() == null ? null
          : marginTransactionMap.get(tss.getIdTransactionMargin());
      if (tssNew == tss && transaction.isMarginInstrument()) {
        marginTransaction = transaction;
      }

      boolean isNextMarginSameDate = marginTransaction != null
          ? isNextMarginAndSameDate(marginTransaction, tss, transactionSecuritySplitList, i, marginTransactionMap)
          : false;
      holdPositionTimeFrameSecurity.addTransactionOrSplit(securityaccount.getIdTenant(),
          securityaccount.getPortfolio().getIdPortfolio(), securityaccount.getIdSecuritycashAccount(), tss,
          marginTransaction, isNextMarginSameDate);
    }
    holdPositionTimeFrameSecurity.prepareNextSecurityaccountAndSaveAll(holdSecurityaccountSecurityRepository);
  }

  /**
   * Retrieves margin transactions for a specific security account and security combination.
   * 
   * @param idSecuritycashAccount the security account identifier
   * @param idSecurity            the security identifier
   * @return map of transaction IDs to margin transaction entities
   */
  private Map<Integer, Transaction> getMarginTransactionByIdSecurityaccountAndSecurity(Integer idSecuritycashAccount,
      Integer idSecurity) {
    return transactionJpaRepository
        .getMarginTransactionMapForSecurityaccountAndSecurity(idSecuritycashAccount, idSecurity).stream()
        .collect(Collectors.toMap(Transaction::getIdTransaction, Function.identity()));
  }

  /**
   * Position calculator and holdings manager for security time-frame processing.
   * 
   * <p>
   * This class manages the calculations required for security position tracking, including unit calculations, margin
   * averaging, stock split adjustments, and currency conversions. It maintains running totals and creates holding
   * records as positions change.
   * </p>
   * 
   * <p>
   * <strong>Core Responsibilities:</strong>
   * </p>
   * <ul>
   * <li>Position unit tracking with split adjustments</li>
   * <li>Margin position averaging and realized holdings calculation</li>
   * <li>Currency conversion setup for multi-currency analysis</li>
   * <li>Time-frame management with proper start/end dates</li>
   * <li>Batch processing optimization for same-day margin transactions</li>
   * </ul>
   * 
   * <p>
   * <strong>Margin Transaction Handling:</strong>
   * </p>
   * <p>
   * The class handles margin position calculations including average price calculations, position opening/closing, and
   * partial position closures with proper split factor adjustments over time.
   * </p>
   */
  static class HoldPositionTimeFrameSecurity {
    /** Tenant's base currency for conversion reference. */
    final String tenantCurrency;
    /** Portfolio's base currency for conversion reference. */
    final String portfolioCurrency;
    /** Currency conversion and security split context. */
    final CurrencypairSecuritySplit css;
    private Double marginAveragePrice = null;
    private double units = 0;
    private double marginRealHoldings = 0.0;
    private double openMarginSum = 0.0;
    private List<HoldSecurityaccountSecurity> toSaveHoldForSecurityList = new ArrayList<>();
    private List<HoldSecurityaccountSecurity> toSaveHoldForSecurityaccountList = new ArrayList<>();

    /**
     * Id of transaction as key
     */
    private Map<Integer, Transaction> marginTransactionMap;
    private int countMaringSameDay = 0;
    private CurrencypairJpaRepository currencypairJpaRepository;
    private Integer idCurrencypairTenant = null;
    private Integer idCurrencypairPortfolio = null;

    public HoldPositionTimeFrameSecurity(CurrencypairJpaRepository currencypairJpaRepository, String tenantCurrency,
        String portfolioCurrency, CurrencypairSecuritySplit css, Map<Integer, Transaction> marginTransactionMap) {
      this.currencypairJpaRepository = currencypairJpaRepository;
      this.tenantCurrency = tenantCurrency;
      this.portfolioCurrency = portfolioCurrency;
      this.css = css;
      this.marginTransactionMap = marginTransactionMap;
    }

    public void addTransactionOrSplit(Integer idTenant, Integer idPortfolio, Integer idSecuritycashAccount,
        ITransactionSecuritySplit tss, Transaction marginTransaction, boolean isNextMarginTransactionAndSameDate) {

      if (isNextMarginTransactionAndSameDate) {
        if (countMaringSameDay == 0) {
          setToHoldDateOnLastTransSplit(tss.getTsDate().toLocalDate().minusDays(1));
        }
        countMaringSameDay++;
        setMarginValues(marginTransaction, tss);
        units += tss.getFactorUnits();
      } else {
        addTransactionOrSplit(idTenant, idPortfolio, idSecuritycashAccount, tss, marginTransaction);
      }
    }

    /**
     * Processes a single transaction or split and creates a holding record if position is non-zero.
     * 
     * <p>
     * This method handles the core logic for transaction processing including:
     * </p>
     * <ul>
     * <li>Stock split adjustments for position units and margin values</li>
     * <li>Currency conversion setup for cross-currency analysis</li>
     * <li>Margin position calculations and average price tracking</li>
     * <li>Time-frame management with proper end date setting</li>
     * </ul>
     * 
     * @param idTenant              the tenant identifier
     * @param idPortfolio           the portfolio identifier
     * @param idSecuritycashAccount the security account identifier
     * @param tss                   the transaction or split to process
     * @param marginTransaction     the associated margin transaction (null for regular transactions)
     */
    private void addTransactionOrSplit(Integer idTenant, Integer idPortfolio, Integer idSecuritycashAccount,
        ITransactionSecuritySplit tss, Transaction marginTransaction) {
      countMaringSameDay = 0;
      setToHoldDateOnLastTransSplit(tss.getTsDate().toLocalDate().minusDays(1));
      if (tss.getCurrency() == null) {
        // Split -> no action when unit = 0
        divideMulitplieUnitsBySplit(tss);
      } else {
        // Transaction
        idCurrencypairTenant = null;
        idCurrencypairPortfolio = null;
        if (tss.getFactorUnits() != 0) {
          setMarginValues(marginTransaction, tss);
        }
        units += tss.getFactorUnits();
      }
      if (DataBusinessHelper.round(units) != 0d) {
        // Per transaction when units <> 0 -> create new HoldSecurityaccountSecurity

        if (tss.getCurrency() != null) {
          if (!tss.getCurrency().equals(tenantCurrency)) {
            idCurrencypairTenant = css.currencypairFromToCurrencyMap
                .get(new FromToCurrency(tss.getCurrency(), tenantCurrency)).getIdSecuritycurrency();
          }
          if (!tss.getCurrency().equals(portfolioCurrency)) {
            idCurrencypairPortfolio = HoldingsHelper.getCurrency(currencypairJpaRepository,
                css.currencypairFromToCurrencyMap, tss.getCurrency(), portfolioCurrency).getIdSecuritycurrency();
          }
        }
        double splitPriceFactor = Securitysplit.calcSplitFatorForFromDate(tss.getIdSecuritycurrency(),
            tss.getTsDate().toLocalDate(), css.securitysplitMap);

        HoldSecurityaccountSecurity holdSecurityaccountSecurity = new HoldSecurityaccountSecurity(idTenant, idPortfolio,
            idSecuritycashAccount, tss.getIdSecuritycurrency(), tss.getTsDate().toLocalDate(),
            DataBusinessHelper.round(units), marginRealHoldings != 0 ? marginRealHoldings : null, marginAveragePrice,
            splitPriceFactor, idCurrencypairTenant, idCurrencypairPortfolio);
        toSaveHoldForSecurityList.add(holdSecurityaccountSecurity);
      }
    }

    /**
     * Calculates and updates margin position values including average price and position tracking.
     * 
     * <p>
     * This method handles margin calculations including:
     * </p>
     * <ul>
     * <li>Position opening with average price calculation</li>
     * <li>Position closing with split-adjusted pricing</li>
     * <li>Partial position closures</li>
     * <li>Running cost basis maintenance</li>
     * </ul>
     * 
     * @param marginTransaction the margin transaction being processed
     * @param tss               the transaction/split context
     */
    private void setMarginValues(Transaction marginTransaction, ITransactionSecuritySplit tss) {

      if (marginTransaction != null) {
        double buySellFactor = marginTransaction.getTransactionType() == TransactionType.ACCUMULATE ? 1 : -1;
        if (marginTransaction.getConnectedIdTransaction() == null) {
          // Open margin position
          openMarginSum += marginTransaction.getSeucritiesNetPrice() * buySellFactor;
          marginAveragePrice = openMarginSum
              / (units + marginTransaction.getUnitsMultiplyValuePerPoint() * buySellFactor);
        } else {
          // Close full or partly margin position
          Transaction openTransaction = marginTransactionMap.get(marginTransaction.getConnectedIdTransaction());
          SplitFactorAfterBefore splitFactorAfterBefore = Securitysplit.calcSplitFatorForFromDateAndToDate(
              tss.getIdSecuritycurrency(), openTransaction.getTransactionTime(), marginTransaction.getTransactionTime(),
              css.securitysplitMap);
          if (units - openTransaction.getUnitsMultiplyValuePerPoint() * buySellFactor != 0d) {
            openMarginSum += openTransaction.getSeucritiesNetPrice() / openTransaction.getUnitsMultiplyValuePerPoint()
                / splitFactorAfterBefore.fromToDateFactor * marginTransaction.getUnitsMultiplyValuePerPoint()
                * buySellFactor;
            marginAveragePrice = openMarginSum
                / (units + marginTransaction.getUnitsMultiplyValuePerPoint() * buySellFactor);
            if (marginAveragePrice.isNaN()) {
              marginAveragePrice = null;
            }
          } else {
            marginAveragePrice = null;
          }
        }
      }
    }

    /**
     * Prepares for the next security by consolidating current security holdings.
     * 
     * <p>
     * This method is called when switching to a different security within the same security account, resetting position
     * calculations while preserving completed holdings.
     * </p>
     */
    public void prepareNextSecurity() {
      toSaveHoldForSecurityaccountList.addAll(toSaveHoldForSecurityList);
      toSaveHoldForSecurityList = new ArrayList<>();
      units = 0;
      openMarginSum = 0.0;
      marginRealHoldings = 0;
      marginAveragePrice = null;
      countMaringSameDay = 0;
    }

    /**
     * Prepares for the next security account and saves all accumulated holdings.
     * 
     * <p>
     * This method finalizes processing for the current security account by saving all holdings to the repository and
     * resetting for the next account.
     * </p>
     * 
     * @param holdSecurityaccountSecurityRepository repository for saving holdings
     */
    public void prepareNextSecurityaccountAndSaveAll(
        HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository) {
      this.prepareNextSecurity();
      holdSecurityaccountSecurityRepository.saveAll(this.toSaveHoldForSecurityaccountList);
    }

    /**
     * Sets the end date on the most recent holding record.
     * 
     * <p>
     * This method maintains time-frame continuity by setting end dates on previous holdings when new transactions
     * create position changes.
     * </p>
     * 
     * @param toHoldDate the end date to set on the last holding
     */
    private void setToHoldDateOnLastTransSplit(LocalDate toHoldDate) {
      if (units != 0) {
        HoldSecurityaccountSecurity lastHoldSecurity = this.getLastTransSplit();
        if (lastHoldSecurity != null && lastHoldSecurity.getToHoldDate() == null) {
          lastHoldSecurity.setToHoldDate(toHoldDate);
        }
      }
    }

    /**
     * Applies stock split adjustments to position units and margin calculations.
     * 
     * <p>
     * This method adjusts position quantities and related margin values when stock splits occur, maintaining accurate
     * position tracking across corporate actions.
     * </p>
     * 
     * @param tss the split transaction containing the split factor
     */
    private void divideMulitplieUnitsBySplit(ITransactionSecuritySplit tss) {
      if (units != 0) {
        units *= tss.getFactorUnits();
        if (marginAveragePrice != null) {
          marginAveragePrice = openMarginSum / units;
        }
        marginRealHoldings *= tss.getFactorUnits();
      }
    }

    /**
     * Returns the most recent holding record for the current security.
     * 
     * @return the last holding record, or null if no holdings exist
     */
    private HoldSecurityaccountSecurity getLastTransSplit() {
      if (toSaveHoldForSecurityList.isEmpty()) {
        return null;
      } else {
        return this.toSaveHoldForSecurityList.getLast();
      }
    }

  }

  @Override
  public MissingQuotesWithSecurities getMissingQuotesWithSecurities(Integer year)
      throws InterruptedException, ExecutionException {
    final Integer idTenant = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getIdTenant();
    final CompletableFuture<LocalDate> firstEverTradingDayCF = CompletableFuture
        .supplyAsync(() -> holdSecurityaccountSecurityRepository.findByIdTenantMinFromHoldDate(idTenant));
    LocalDate lastDayOfYear = LocalDate.of(year, 12, 31);
    LocalDate yesterday = LocalDate.now().minusDays(1);
    final CompletableFuture<List<DateSecurityQuoteMissing>> dateSecurityQuoteMissingListCF = CompletableFuture
        .supplyAsync(() -> holdSecurityaccountSecurityRepository.getMissingQuotesForSecurityByTenantAndPeriod(idTenant,
            LocalDate.of(year, 1, 1), yesterday.isBefore(lastDayOfYear) ? yesterday : lastDayOfYear));

    MissingQuotesWithSecurities missingQuotesWithSecurities = new MissingQuotesWithSecurities(year,
        firstEverTradingDayCF.get());

    List<DateSecurityQuoteMissing> dateSecurityQuoteMissingList = dateSecurityQuoteMissingListCF.get();
    if (!dateSecurityQuoteMissingList.isEmpty()) {
      Set<Integer> idsSecuritycurrency = new HashSet<>();
      dateSecurityQuoteMissingList.forEach(dateSecurityQuoteMissing -> {
        idsSecuritycurrency.add(dateSecurityQuoteMissing.getIdSecuritycurrency());
        missingQuotesWithSecurities.addDateSecurity(dateSecurityQuoteMissing.getTradingDate(),
            dateSecurityQuoteMissing.getIdSecuritycurrency());
        missingQuotesWithSecurities.addMissingIdSecurity(dateSecurityQuoteMissing.getIdSecuritycurrency());
      });
      missingQuotesWithSecurities
          .setSecurties(this.securityJpaRepository.findByIdSecuritycurrencyInOrderByName(idsSecuritycurrency));
    }

    return missingQuotesWithSecurities;
  }

  /**
   * Context class containing currency conversion mappings and security split information.
   * 
   * <p>
   * This class provides the necessary data for accurate position calculations across different currencies and time
   * periods, including historical stock split adjustments.
   * </p>
   * 
   * <p>
   * <strong>Currency Support:</strong>
   * </p>
   * <p>
   * Contains mappings for currency pair conversions needed when securities, portfolios, and tenants use different
   * currencies.
   * </p>
   * 
   * <p>
   * <strong>Split Adjustments:</strong>
   * </p>
   * <p>
   * Contains historical stock split data for accurate position and price calculations across corporate actions.
   * </p>
   */
  static class CurrencypairSecuritySplit {
    /** Map of currency pairs for conversion between different currencies. */
    public Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap;
    /** Map of security splits by security ID for position adjustments. */
    public Map<Integer, List<Securitysplit>> securitysplitMap;

    public CurrencypairSecuritySplit(Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap,
        Map<Integer, List<Securitysplit>> securitysplitMap) {
      this.currencypairFromToCurrencyMap = currencypairFromToCurrencyMap;
      this.securitysplitMap = securitysplitMap;
    }

  }

}
