package grafioschtrader.service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.data.EvaluationValue;

import grafiosch.common.DataHelper;
import grafiosch.exceptions.DataViolation;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.common.DateBusinessHelper;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.StandingOrder;
import grafioschtrader.entities.StandingOrderCashaccount;
import grafioschtrader.entities.StandingOrderFailure;
import grafioschtrader.entities.StandingOrderSecurity;
import grafioschtrader.entities.TradingDaysMinus.TradingDaysMinusKey;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.StandingOrderFailureJpaRepository;
import grafioschtrader.repository.StandingOrderJpaRepository;
import grafioschtrader.repository.TradingDaysMinusJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.PeriodDayPosition;
import grafioschtrader.types.RepeatUnit;
import grafioschtrader.types.TransactionType;
import grafioschtrader.types.WeekendAdjustType;

/**
 * Service that processes due standing orders and creates {@link Transaction} entities via the existing transaction
 * pipeline. Designed so that both the daily scheduled task and the simulation environment can call
 * {@link #executeAllDueStandingOrders(LocalDate)} with the appropriate date.
 */
@Service
public class StandingOrderExecutionService {

  private static final Logger log = LoggerFactory.getLogger(StandingOrderExecutionService.class);

  private static final int MAX_TRADING_DAY_ADJUSTMENT_ITERATIONS = 10;

  @Autowired
  private StandingOrderJpaRepository standingOrderJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private StandingOrderFailureJpaRepository standingOrderFailureJpaRepository;

  @Autowired
  private TradingDaysMinusJpaRepository tradingDaysMinusJpaRepository;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Autowired
  private MessageSource messageSource;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  @Lazy
  private StandingOrderExecutionService self;

  /**
   * Queries all active standing orders whose {@code nextExecutionDate <= processingDate} and creates transactions
   * for each due execution. Each standing order is processed independently; a failure in one does not affect others.
   *
   * @param processingDate the date up to which standing orders are processed (typically today)
   */
  public void executeAllDueStandingOrders(LocalDate processingDate) {
    List<StandingOrder> dueOrders = standingOrderJpaRepository
        .findByNextExecutionDateNotNullAndNextExecutionDateLessThanEqual(processingDate);
    log.info("Standing order execution: {} due orders found for date {}", dueOrders.size(), processingDate);

    for (StandingOrder so : dueOrders) {
      List<StandingOrderFailure> failures;
      try {
        failures = self.processSingleStandingOrder(so, processingDate);
      } catch (Exception e) {
        log.error("Standing order {} failed unexpectedly: {}", so.getIdStandingOrder(), e.getMessage(), e);
        failures = List.of(new StandingOrderFailure(so.getIdStandingOrder(), processingDate, null,
            getStackTrace(e)));
      }
      if (!failures.isEmpty()) {
        try {
          standingOrderFailureJpaRepository.saveAll(failures);
        } catch (Exception e) {
          log.error("Failed to persist {} failure(s) for standing order {}: {}", failures.size(),
              so.getIdStandingOrder(), e.getMessage());
        }
      }
    }
  }

  /**
   * Processes a single standing order, iterating through all due execution dates (catch-up loop). For each scheduled
   * date, builds and saves a transaction, then advances the standing order to the next execution date. Failures are
   * collected and returned (not saved) to avoid issues with rollback-only Hibernate sessions.
   *
   * @param so             the standing order to process
   * @param processingDate the upper bound date for execution
   * @return list of failures encountered during execution (empty if all succeeded)
   */
  @Transactional
  public List<StandingOrderFailure> processSingleStandingOrder(StandingOrder so, LocalDate processingDate) {
    List<StandingOrderFailure> failures = new ArrayList<>();
    LocalDate scheduledDate = so.getNextExecutionDate();

    // Enforce the total (lifetime) per-tenant transaction limit, also for automatic standing-order executions.
    int maxTransaction = globalparametersJpaRepository.getMaxValueByKey(GlobalParamKeyDefault.GLOB_KEY_MAX_TRANSACTION);
    int tenantTransactionCount = transactionJpaRepository.countByIdTenant(so.getIdTenant());

    while (scheduledDate != null && !scheduledDate.isAfter(processingDate) && so.getNextExecutionDate() != null) {
      if (tenantTransactionCount >= maxTransaction) {
        // Transaction limit reached: stop without advancing nextExecutionDate so this standing order resumes
        // automatically once the tenant deletes transactions. Already-created catch-up transactions are kept.
        String msg = messageSource.getMessage("gt.transaction.limit.exceeded", new Object[] { maxTransaction },
            "gt.transaction.limit.exceeded", getTenantLocale(so.getIdTenant()));
        failures.add(new StandingOrderFailure(so.getIdStandingOrder(), scheduledDate, msg, null));
        log.warn("Standing order {} skipped on {}: transaction limit {} reached", so.getIdStandingOrder(),
            scheduledDate, maxTransaction);
        break;
      }
      LocalDate effectiveDate = adjustForWeekend(scheduledDate, so.getWeekendAdjust());

      try {
        Transaction transaction = null;
        if (so instanceof StandingOrderCashaccount soc) {
          transaction = buildCashTransaction(soc, effectiveDate);
        } else if (so instanceof StandingOrderSecurity sos) {
          // Inside the try, so that "no trading day found" is recorded as a business error rather than a stack trace.
          effectiveDate = adjustForTradingDay(effectiveDate, sos, processingDate);
          transaction = buildSecurityTransaction(sos, effectiveDate);
        }

        if (transaction != null) {
          transactionJpaRepository.saveOnlyAttributesFormImport(transaction, null);
          tenantTransactionCount++;
          log.debug("Standing order {} created transaction on {}", so.getIdStandingOrder(), effectiveDate);
        }
      } catch (StandingOrderBusinessException e) {
        log.warn("Standing order {} execution skipped for date {}: {}", so.getIdStandingOrder(), effectiveDate,
            e.getMessage());
        failures.add(new StandingOrderFailure(so.getIdStandingOrder(), effectiveDate, e.getMessage(), null));
      } catch (DataViolationException dvex) {
        String translatedMsg = translateDataViolation(dvex, so.getIdTenant());
        log.warn("Standing order {} validation failed for date {}: {}", so.getIdStandingOrder(), effectiveDate,
            translatedMsg);
        failures.add(new StandingOrderFailure(so.getIdStandingOrder(), effectiveDate, translatedMsg, null));
      } catch (Exception e) {
        log.warn("Standing order {} execution failed for date {}: {}", so.getIdStandingOrder(), effectiveDate,
            e.getMessage());
        failures.add(new StandingOrderFailure(so.getIdStandingOrder(), effectiveDate, null, getStackTrace(e)));
      }

      // Always advance to prevent infinite retry of a failed date
      so.setLastExecutionDate(effectiveDate);
      LocalDate nextDate = computeNextExecutionDate(so, scheduledDate);
      if (nextDate != null && nextDate.isAfter(so.getValidTo())) {
        so.setNextExecutionDate(null);
      } else {
        so.setNextExecutionDate(nextDate);
      }
      standingOrderJpaRepository.save(so);

      scheduledDate = so.getNextExecutionDate();
    }
    return failures;
  }

  /**
   * Builds a cash transaction (WITHDRAWAL, DEPOSIT, INTEREST_CASHACCOUNT or FEE) from a standing order.
   *
   * <p>
   * When the standing order carries a foreign amount currency, the exchange rate of the effective date is applied,
   * either through the EvalEx formula or, without one, as a plain conversion. Note that the transaction is
   * deliberately left without a currency pair and exchange rate: those describe a conversion between two accounts,
   * whereas here the foreign currency is only the basis on which the bank calculates the amount. For FEE and
   * INTEREST_CASHACCOUNT {@code Transaction.clearAccountTransaction()} would drop the pair anyway.
   * </p>
   *
   * @param soc           the cash-account standing order
   * @param effectiveDate the adjusted execution date
   * @return a new Transaction ready for saving
   */
  private Transaction buildCashTransaction(StandingOrderCashaccount soc, LocalDate effectiveDate) {
    Transaction tx = new Transaction();
    tx.setCashaccount(soc.getCashaccount());
    tx.setCashaccountAmount(computeCashaccountAmount(soc, effectiveDate));
    tx.setTransactionType(soc.getTransactionType());
    tx.setTransactionTime(effectiveDate.atStartOfDay());
    tx.setIdTenant(soc.getIdTenant());
    tx.setNote(soc.getNote());
    tx.setIdStandingOrder(soc.getIdStandingOrder());
    // Kept for WITHDRAWAL/DEPOSIT; clearAccountTransaction() discards it again for FEE and INTEREST_CASHACCOUNT.
    tx.setTransactionCost(soc.getTransactionCost());
    return tx;
  }

  /**
   * Computes the amount to book for a cash standing order. Without an amount currency the stored amount is used
   * unchanged, which is the behaviour of standing orders that do not involve a foreign currency.
   *
   * @param soc           the cash-account standing order
   * @param effectiveDate the adjusted execution date, used to look up the exchange rate
   * @return the amount in the currency of the cash account, rounded to that currency's precision
   * @throws StandingOrderBusinessException if no exchange rate is available or the formula cannot be evaluated
   */
  private double computeCashaccountAmount(StandingOrderCashaccount soc, LocalDate effectiveDate) {
    double a = soc.getCashaccountAmount();
    String formula = soc.getCashaccountAmountFormula();
    Double r = null;

    if (soc.getIdCurrencypair() != null) {
      // Booking an unconverted amount would silently charge the wrong sum, so skip and report the failure instead.
      r = findCloseWithinTolerance(soc.getIdCurrencypair(), effectiveDate, soc.getQuoteToleranceDays())
          .orElseThrow(() -> new StandingOrderBusinessException(
              messageSource.getMessage("standing.order.exec.no.exchange.rate",
                  new Object[] { soc.getAmountCurrency(), effectiveDate }, "standing.order.exec.no.exchange.rate",
                  getTenantLocale(soc.getIdTenant()))));
    }

    double amount;
    if (formula != null && !formula.isBlank()) {
      amount = evaluateCashAmount(formula, a, r, soc);
    } else if (r != null) {
      amount = DataBusinessHelper.divideMultiplyExchangeRate(a, r, soc.getAmountCurrency(),
          soc.getCashaccount().getCurrency());
    } else {
      amount = a;
    }
    return DataHelper.round(amount,
        globalparametersService.getPrecisionForCurrency(soc.getCashaccount().getCurrency()));
  }

  /**
   * Evaluates the amount formula and turns a failure into a translated business error. Unlike {@link #evaluateCost}
   * this does not fall back to 0.0: a broken amount formula would silently book a zero-value transaction every period,
   * so the execution is aborted and recorded as a failure instead.
   *
   * @param formula the EvalEx amount formula
   * @param a       the base amount as entered on the standing order
   * @param r       the exchange rate, or null when the standing order has no amount currency
   * @param soc     the standing order, used to resolve the tenant's locale for the error message
   * @return the evaluated amount
   * @throws StandingOrderBusinessException if the formula cannot be parsed or evaluated
   */
  private double evaluateCashAmount(String formula, double a, Double r, StandingOrderCashaccount soc) {
    try {
      return evaluateCashAmountFormula(formula, a, r);
    } catch (Exception e) {
      throw new StandingOrderBusinessException(messageSource.getMessage("standing.order.exec.amount.formula.error",
          new Object[] { formula, e.getMessage() }, "standing.order.exec.amount.formula.error",
          getTenantLocale(soc.getIdTenant())));
    }
  }

  /**
   * Evaluates an EvalEx amount formula with the variables {@code a} (base amount) and {@code r} (exchange rate).
   * Package-visible and free of Spring dependencies so it can be unit tested.
   *
   * @param formula the EvalEx amount formula
   * @param a       the base amount
   * @param r       the exchange rate, or null when the standing order has no amount currency; the variable is then
   *                left unbound, so a formula using {@code r} fails instead of silently assuming a rate
   * @return the evaluated amount
   * @throws Exception if the formula cannot be parsed or evaluated
   */
  static double evaluateCashAmountFormula(String formula, double a, Double r) throws Exception {
    Expression expression = new Expression(formula);
    expression.with("a", BigDecimal.valueOf(a));
    if (r != null) {
      expression.with("r", BigDecimal.valueOf(r));
    }
    return expression.evaluate().getNumberValue().doubleValue();
  }

  /** Resolves the locale of the tenant's user, falling back to English when no user is found. */
  private Locale getTenantLocale(Integer idTenant) {
    return userJpaRepository.findByIdTenant(idTenant).map(user -> user.createAndGetJavaLocale()).orElse(Locale.ENGLISH);
  }

  /**
   * Builds a security transaction (ACCUMULATE or REDUCE) from a standing order. Looks up the close price for the
   * effective date, evaluates cost formulas, calculates units (for amount-based orders), and computes the cash
   * account amount.
   *
   * @param sos           the security standing order
   * @param effectiveDate the adjusted execution date
   * @return a new Transaction ready for saving, or null if no price is available or units would be <= 0
   */
  private Transaction buildSecurityTransaction(StandingOrderSecurity sos, LocalDate effectiveDate) {
    Integer idSecurity = sos.getSecurity().getIdSecuritycurrency();

    // 1. Price lookup
    double quotation = findCloseWithinTolerance(idSecurity, effectiveDate, sos.getQuoteToleranceDays())
        .orElseThrow(() -> new StandingOrderBusinessException(
            messageSource.getMessage("standing.order.exec.no.price",
                new Object[] { idSecurity, effectiveDate, sos.getIdStandingOrder() },
                "standing.order.exec.no.price", getTenantLocale(sos.getIdTenant()))));

    // 2. Determine units and costs
    double units;
    double taxCost;
    double transactionCost;
    TransactionType txType = sos.getTransactionType();

    if (sos.getUnits() != null) {
      // Unit-based mode
      units = sos.getUnits();
      taxCost = evaluateCost(sos.getTaxCost(), sos.getTaxCostFormula(), units, quotation, units * quotation);
      transactionCost = evaluateCost(sos.getTransactionCost(), sos.getTransactionCostFormula(), units, quotation,
          units * quotation);
    } else {
      // Amount-based mode
      double investAmount = sos.getInvestAmount();

      // First pass: estimate units without costs, then refine
      if (sos.isAmountIncludesCosts()) {
        // Gross: costs come out of investAmount
        double estimatedTax = evaluateCost(sos.getTaxCost(), sos.getTaxCostFormula(), investAmount / quotation,
            quotation, investAmount);
        double estimatedTxCost = evaluateCost(sos.getTransactionCost(), sos.getTransactionCostFormula(),
            investAmount / quotation, quotation, investAmount);
        units = (investAmount - estimatedTax - estimatedTxCost) / quotation;
      } else {
        // Net: investAmount is the pure investment, costs added on top
        units = investAmount / quotation;
      }

      if (!sos.isFractionalUnits()) {
        units = Math.floor(units);
      }

      if (units <= 0) {
        throw new StandingOrderBusinessException(messageSource.getMessage("standing.order.exec.zero.units",
            new Object[] { sos.getIdStandingOrder() }, "standing.order.exec.zero.units",
            getTenantLocale(sos.getIdTenant())));
      }

      // Re-evaluate costs with actual units
      double amount = units * quotation;
      taxCost = evaluateCost(sos.getTaxCost(), sos.getTaxCostFormula(), units, quotation, amount);
      transactionCost = evaluateCost(sos.getTransactionCost(), sos.getTransactionCostFormula(), units, quotation,
          amount);
    }

    // 3. Exchange rate lookup. A missing rate must not pass silently: the security and the cash account are then in
    // different currencies and an unconverted amount would be booked.
    Double currencyExRate = null;
    if (sos.getIdCurrencypair() != null) {
      currencyExRate = findCloseWithinTolerance(sos.getIdCurrencypair(), effectiveDate, sos.getQuoteToleranceDays())
          .orElseThrow(() -> new StandingOrderBusinessException(
              messageSource.getMessage("standing.order.exec.no.exchange.rate",
                  new Object[] { sos.getSecurity().getCurrency(), effectiveDate },
                  "standing.order.exec.no.exchange.rate", getTenantLocale(sos.getIdTenant()))));
    }

    // 4. Compute cashaccountAmount: ACCUMULATE = -(u*q + tax + txCost), REDUCE = +(u*q - tax - txCost)
    double grossAmount = units * quotation;
    double cashaccountAmount;
    if (txType == TransactionType.ACCUMULATE) {
      cashaccountAmount = -(grossAmount + taxCost + transactionCost);
    } else {
      cashaccountAmount = grossAmount - taxCost - transactionCost;
    }

    // Apply exchange rate conversion if applicable
    if (currencyExRate != null) {
      cashaccountAmount = DataBusinessHelper.divideMultiplyExchangeRate(cashaccountAmount, currencyExRate,
          sos.getSecurity().getCurrency(), sos.getCashaccount().getCurrency());
    }

    // 5. Build transaction
    Transaction tx = new Transaction(sos.getIdSecurityaccount(), sos.getCashaccount(), sos.getSecurity(),
        cashaccountAmount, units, quotation, txType, taxCost > 0 ? taxCost : null,
        transactionCost > 0 ? transactionCost : null, null, effectiveDate.atStartOfDay(), currencyExRate, sos.getIdCurrencypair(), null,
        null);
    tx.setIdTenant(sos.getIdTenant());
    tx.setNote(sos.getNote());
    tx.setIdStandingOrder(sos.getIdStandingOrder());
    return tx;
  }

  /**
   * Looks up the close of an instrument for the execution date, widened by the standing order's tolerance. The whole
   * window is read in a single query and then probed in the priority order of {@link #toleranceCandidates}, so a
   * tolerance of 3 costs one round trip rather than seven.
   *
   * <p>
   * The transaction keeps the execution date in every case; only the quote may originate from a neighbouring day. This
   * replaces an unbounded "certain or older day" search that could reach back arbitrarily far for an exchange rate.
   * </p>
   *
   * @param idSecuritycurrency the security or currency pair whose close is wanted
   * @param effectiveDate      the execution date
   * @param tolerance          the standing order's tolerance in days; 0 demands the exact date
   * @return the close of the first matching candidate, or empty when the window holds no quote at all
   */
  private Optional<Double> findCloseWithinTolerance(Integer idSecuritycurrency, LocalDate effectiveDate,
      byte tolerance) {
    int window = Math.abs(tolerance);
    List<Historyquote> quotes = historyquoteJpaRepository.findByIdSecuritycurrencyAndDateBetweenOrderByDate(
        idSecuritycurrency, effectiveDate.minusDays(window), effectiveDate.plusDays(window));
    Map<LocalDate, Double> closeByDate = new HashMap<>();
    quotes.forEach(hq -> closeByDate.put(hq.getDate(), hq.getClose()));

    return toleranceCandidates(effectiveDate, tolerance).stream().map(closeByDate::get).filter(close -> close != null)
        .findFirst();
  }

  // ---- Static helpers (package-visible for unit testing) ----

  /**
   * Builds the candidate quote dates for a tolerance, in the order in which they should be tried. The magnitude of the
   * tolerance is the window in days on both sides of the execution date, its sign decides which side is offered first:
   * a negative tolerance prefers the past, a positive one the future. A tolerance of 0 yields the execution date alone.
   *
   * @param date      the execution date, always the first candidate
   * @param tolerance the tolerance in days, between -3 and 3
   * @return the candidate dates, closest first and within each distance the preferred direction first
   */
  static List<LocalDate> toleranceCandidates(LocalDate date, byte tolerance) {
    List<LocalDate> candidates = new ArrayList<>();
    candidates.add(date);
    int preferred = tolerance < 0 ? -1 : 1;
    for (int distance = 1; distance <= Math.abs(tolerance); distance++) {
      candidates.add(date.plusDays((long) preferred * distance));
      candidates.add(date.minusDays((long) preferred * distance));
    }
    return candidates;
  }

  /**
   * Adjusts a date that falls on Saturday or Sunday according to the specified weekend adjustment policy.
   *
   * @param date           the scheduled execution date
   * @param weekendAdjust  BEFORE shifts to previous Friday, AFTER shifts to next Monday
   * @return the adjusted date (unchanged if it falls on a weekday)
   */
  static LocalDate adjustForWeekend(LocalDate date, WeekendAdjustType weekendAdjust) {
    DayOfWeek dow = date.getDayOfWeek();
    if (dow == DayOfWeek.SATURDAY) {
      return weekendAdjust == WeekendAdjustType.BEFORE ? date.minusDays(1) : date.plusDays(2);
    } else if (dow == DayOfWeek.SUNDAY) {
      return weekendAdjust == WeekendAdjustType.BEFORE ? date.minusDays(2) : date.plusDays(1);
    }
    return date;
  }

  /**
   * Shifts the effective date onto the nearest trading day of the security's stock exchange. Each iteration moves the
   * date by one day in the direction indicated by {@link WeekendAdjustType} and re-applies weekend adjustment. The
   * adjustment is skipped outside the period the trading calendar covers, because there the answer would be wrong
   * rather than merely unknown.
   *
   * @param effectiveDate  the weekend-adjusted execution date
   * @param sos            the security standing order (provides access to the stock exchange)
   * @param processingDate the current processing date — dates after this are not adjusted
   * @return the nearest trading day, or the original date if it is already a trading day or outside the calendar
   * @throws StandingOrderBusinessException if no trading day is found within
   *                                        {@link #MAX_TRADING_DAY_ADJUSTMENT_ITERATIONS} steps
   */
  private LocalDate adjustForTradingDay(LocalDate effectiveDate, StandingOrderSecurity sos, LocalDate processingDate) {
    if (effectiveDate.isAfter(processingDate) || effectiveDate.isBefore(DateBusinessHelper
        .getOldestTradingDayAsLocalDate())) {
      return effectiveDate;
    }
    Integer idStockexchange = sos.getSecurity().getStockexchange().getIdStockexchange();
    WeekendAdjustType weekendAdjust = sos.getWeekendAdjust();

    for (int i = 0; i < MAX_TRADING_DAY_ADJUSTMENT_ITERATIONS; i++) {
      if (isTradingDay(idStockexchange, effectiveDate)) {
        return effectiveDate;
      }
      effectiveDate = (weekendAdjust == WeekendAdjustType.BEFORE)
          ? effectiveDate.minusDays(1)
          : effectiveDate.plusDays(1);
      effectiveDate = adjustForWeekend(effectiveDate, weekendAdjust);
    }
    throw new StandingOrderBusinessException(messageSource.getMessage("standing.order.exec.no.trading.day",
        new Object[] { MAX_TRADING_DAY_ADJUSTMENT_ITERATIONS, sos.getSecurity().getIdSecuritycurrency(),
            idStockexchange, sos.getIdStandingOrder() },
        "standing.order.exec.no.trading.day", getTenantLocale(sos.getIdTenant())));
  }

  /**
   * Answers whether the exchange trades on the given date. A date qualifies only when the global calendar
   * {@code trading_days_plus} contains it — weekends and worldwide holidays such as 1 January are missing there — and
   * the exchange has no additional closure for it in {@code trading_days_minus}. Checking only the latter would treat
   * every global holiday as a trading day, because a foreign key forbids such a date from appearing in the delta table.
   *
   * @param idStockexchange the stock exchange whose closures apply
   * @param date            the date to test
   * @return true when the exchange trades on that date
   */
  private boolean isTradingDay(Integer idStockexchange, LocalDate date) {
    return tradingDaysPlusJpaRepository.existsById(date)
        && !tradingDaysMinusJpaRepository.existsById(new TradingDaysMinusKey(idStockexchange, date));
  }

  /**
   * Computes the next execution date based on the standing order's repeat configuration.
   *
   * @param so                the standing order
   * @param lastScheduledDate the last scheduled date (before weekend adjustment) from which to advance
   * @return the next scheduled date
   */
  public static LocalDate computeNextExecutionDate(StandingOrder so, LocalDate lastScheduledDate) {
    RepeatUnit repeatUnit = so.getRepeatUnit();
    int interval = so.getRepeatInterval();
    PeriodDayPosition dayPosition = so.getPeriodDayPosition();

    switch (repeatUnit) {
    case DAYS:
      return lastScheduledDate.plusDays(interval);
    case MONTHS:
      LocalDate nextMonth = lastScheduledDate.plusMonths(interval);
      return resolveDayInMonth(nextMonth, dayPosition, so.getDayOfExecution());
    case YEARS:
      LocalDate nextYear = lastScheduledDate.plusYears(interval);
      if (so.getMonthOfExecution() != null) {
        nextYear = nextYear.withMonth(so.getMonthOfExecution());
      }
      return resolveDayInMonth(nextYear, dayPosition, so.getDayOfExecution());
    default:
      return lastScheduledDate.plusDays(interval);
    }
  }

  /**
   * Resolves the day within a month according to the period day position setting.
   *
   * @param date          the date with the correct year and month
   * @param dayPosition   SPECIFIC_DAY, FIRST_DAY, or LAST_DAY
   * @param dayOfExecution the specific day (1-28) for SPECIFIC_DAY position, may be null
   * @return the date with the resolved day-of-month
   */
  public static LocalDate resolveDayInMonth(LocalDate date, PeriodDayPosition dayPosition, Byte dayOfExecution) {
    switch (dayPosition) {
    case FIRST_DAY:
      return date.withDayOfMonth(1);
    case LAST_DAY:
      return date.withDayOfMonth(date.lengthOfMonth());
    case SPECIFIC_DAY:
    default:
      int day = dayOfExecution != null ? dayOfExecution : 1;
      return date.withDayOfMonth(Math.min(day, date.lengthOfMonth()));
    }
  }

  /**
   * Evaluates a cost value: returns the fixed cost if non-null, otherwise evaluates the EvalEx formula with variables
   * {@code u} (units), {@code q} (quotation), {@code a} (amount = u*q). Returns 0.0 on error or if both are null.
   *
   * @param fixedCost the fixed cost value (takes priority if non-null)
   * @param formula   the EvalEx cost formula (used when fixedCost is null)
   * @param u         the number of units
   * @param q         the quotation (price per unit)
   * @param a         the total amount (u * q)
   * @return the evaluated cost, or 0.0 if not applicable or on error
   */
  static double evaluateCost(Double fixedCost, String formula, double u, double q, double a) {
    if (fixedCost != null) {
      return fixedCost;
    }
    if (formula != null && !formula.isBlank()) {
      try {
        Expression expression = new Expression(formula);
        expression.with("u", BigDecimal.valueOf(u));
        expression.with("q", BigDecimal.valueOf(q));
        expression.with("a", BigDecimal.valueOf(a));
        EvaluationValue result = expression.evaluate();
        return result.getNumberValue().doubleValue();
      } catch (Exception e) {
        log.warn("standing.order.exec.formula.error: formula='{}', error={}", formula, e.getMessage());
        return 0.0;
      }
    }
    return 0.0;
  }

  /**
   * Translates the {@link DataViolation} entries of a {@link DataViolationException} into a human-readable string
   * using the tenant user's locale. Follows the same translation logic as
   * {@link grafiosch.rest.helper.RestHelper#createValidationError} but produces a plain String for storage in
   * {@link StandingOrderFailure#businessError}.
   *
   * @param dvex     the exception containing one or more data violations
   * @param idTenant the tenant ID used to look up the user's locale
   * @return translated violation messages joined with "; "
   */
  private String translateDataViolation(DataViolationException dvex, Integer idTenant) {
    Locale locale = getTenantLocale(idTenant);

    return dvex.getDataViolation().stream().map(dv -> {
      String field = dv.isTranslateFieldName()
          ? messageSource.getMessage(dv.getField(), null, dv.getField(), locale)
          : dv.getField();
      String message = messageSource.getMessage(dv.getMessageKey(), dv.getData(), dv.getMessageKey(), locale);
      return field + ": " + message;
    }).collect(Collectors.joining("; "));
  }

  /**
   * Converts an exception's full stack trace to a string, truncated to the maximum size allowed by
   * {@link StandingOrderFailure#MAX_SIZE_UNEXPECTED_ERROR}.
   */
  private static String getStackTrace(Exception e) {
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    String trace = sw.toString();
    return trace.substring(0, Math.min(trace.length(), StandingOrderFailure.MAX_SIZE_UNEXPECTED_ERROR));
  }
}
