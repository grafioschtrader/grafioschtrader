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

import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
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
import grafioschtrader.entities.User;
import grafioschtrader.reportviews.FromToCurrency;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository.DateSecurityQuoteMissing;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository.IHoldSecuritySplitTransactionBySecurity;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository.ITransactionSecuritySplit;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository.TransactionSecuritySplit;
import grafioschtrader.repository.helper.HoldingsHelper;
import grafioschtrader.types.TransactionType;

/**
 * HoldSecurityaccountSecurity is affected:<b> When a tenant buys or sells a
 * security.<b> When a tenants global main currency or a portfolio currency
 * changes. For every tenant when security splits changes.<b>
 *
 * Margin transaction are handled differently, every single transaction is
 * processed, while for other transactions the database query delivers a
 * combined result of all transaction for a single day, in respect of security
 * and security account.
 *
 * Open and close a position in a day will not produce a record in holdings of
 * securities.
 *
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

  private CurrencypairSecuritySplit createCurrencypairSecuritySplit(Security security) {
    List<Securitysplit> securitysplits = securitysplitJpaRepository
        .findByIdSecuritycurrencyOrderBySplitDateAsc(security.getIdSecuritycurrency());
    Map<Integer, List<Securitysplit>> securitysplitMap = new HashMap<>();
    securitysplitMap.put(security.getIdSecuritycurrency(), securitysplits);
    List<Currencypair> currencypairs = currencypairJpaRepository.findByFromCurrency(security.getCurrency());

    return new CurrencypairSecuritySplit(HoldingsHelper.transformToCurrencypairMapWithFromCurrencyAsKey(currencypairs),
        securitysplitMap);

  }

  private void createSecurityHoldingsForTimeFrameByPortfolios(final List<Portfolio> allTenantPortfolios,
      final String tenantCurrency, CurrencypairSecuritySplit loadCurrencypairSecuritySplit) {
    allTenantPortfolios.forEach(portfolio -> {
      portfolio.getSecurityaccountList()
          .forEach(securityaccount -> loadForSecurityHoldingsBySecurityaccount(portfolio.getIdTenant(),
              portfolio.getIdPortfolio(), securityaccount.getIdSecuritycashAccount(), tenantCurrency,
              portfolio.getCurrency(), loadCurrencypairSecuritySplit));
    });
  }

  private CurrencypairSecuritySplit loadCurrencypairSecuritySplit(Integer idTenant) {
    return new CurrencypairSecuritySplit(
        HoldingsHelper.getUsedCurrencypiarsByIdTenant(idTenant, currencypairJpaRepository),
        securitysplitJpaRepository.getSecuritysplitMapByIdTenant(idTenant));
  }

  /**
   * Create holdings for a security account of a portfolio and tenant.
   *
   * @param idTenant
   * @param idPortfolio
   * @param idSecuritycashAccount
   * @param tenantCurrency
   * @param portfolioCurrency
   * @throws ExecutionException
   * @throws InterruptedException
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

  private Map<Integer, Transaction> getMarginTransactionByIdSecurityaccount(Integer idSecuritycashAccount) {
    return transactionJpaRepository.getMarginTransactionMapForSecurityaccount(idSecuritycashAccount).stream()
        .collect(Collectors.toMap(Transaction::getIdTransaction, Function.identity()));
  }

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

  private Map<Integer, Transaction> getMarginTransactionByIdSecurityaccountAndSecurity(Integer idSecuritycashAccount,
      Integer idSecurity) {
    return transactionJpaRepository
        .getMarginTransactionMapForSecurityaccountAndSecurity(idSecuritycashAccount, idSecurity).stream()
        .collect(Collectors.toMap(Transaction::getIdTransaction, Function.identity()));
  }

  /**
   * Must be created by each security account
   *
   */
  static class HoldPositionTimeFrameSecurity {
    final String tenantCurrency;
    final String portfolioCurrency;
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
      if (DataHelper.round(units) != 0d) {
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
            idSecuritycashAccount, tss.getIdSecuritycurrency(), tss.getTsDate().toLocalDate(), DataHelper.round(units),
            marginRealHoldings != 0 ? marginRealHoldings : null, marginAveragePrice, splitPriceFactor,
            idCurrencypairTenant, idCurrencypairPortfolio);
        toSaveHoldForSecurityList.add(holdSecurityaccountSecurity);
      }
    }

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

    public void prepareNextSecurity() {
      toSaveHoldForSecurityaccountList.addAll(toSaveHoldForSecurityList);
      toSaveHoldForSecurityList = new ArrayList<>();
      units = 0;
      openMarginSum = 0.0;
      marginRealHoldings = 0;
      marginAveragePrice = null;
      countMaringSameDay = 0;
    }

    public void prepareNextSecurityaccountAndSaveAll(
        HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository) {
      this.prepareNextSecurity();
      holdSecurityaccountSecurityRepository.saveAll(this.toSaveHoldForSecurityaccountList);
    }

    private void setToHoldDateOnLastTransSplit(LocalDate toHoldDate) {
      if (units != 0) {
        HoldSecurityaccountSecurity lastHoldSecurity = this.getLastTransSplit();
        if (lastHoldSecurity != null) {
          lastHoldSecurity.setToHoldDate(toHoldDate);
        }
      }
    }

    private void divideMulitplieUnitsBySplit(ITransactionSecuritySplit tss) {
      if (units != 0) {
        units *= tss.getFactorUnits();
        if (marginAveragePrice != null) {
          marginAveragePrice = openMarginSum / units;
        }
        marginRealHoldings *= tss.getFactorUnits();
      }
    }

    private HoldSecurityaccountSecurity getLastTransSplit() {
      if (toSaveHoldForSecurityList.isEmpty()) {
        return null;
      } else {
        return this.toSaveHoldForSecurityList.get(toSaveHoldForSecurityList.size() - 1);
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

  static class CurrencypairSecuritySplit {
    public Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap;
    public Map<Integer, List<Securitysplit>> securitysplitMap;

    public CurrencypairSecuritySplit(Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap,
        Map<Integer, List<Securitysplit>> securitysplitMap) {
      this.currencypairFromToCurrencyMap = currencypairFromToCurrencyMap;
      this.securitysplitMap = securitysplitMap;
    }

  }

}
