package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ezylang.evalex.Expression;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafiosch.common.LockedWhenUsed;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.RepositoryHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.StandingOrder;
import grafioschtrader.entities.StandingOrderCashaccount;
import grafioschtrader.entities.StandingOrderSecurity;
import grafioschtrader.dto.QuoteToleranceRange;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.service.StandingOrderExecutionService;
import grafioschtrader.types.PeriodDayPosition;
import grafioschtrader.types.RepeatUnit;
import grafioschtrader.types.TransactionType;

/**
 * Repository implementation for standing orders with validation logic for scheduling parameters,
 * transaction type constraints, tenant limits, and initial next-execution-date computation.
 */
public class StandingOrderJpaRepositoryImpl extends BaseRepositoryImpl<StandingOrder>
    implements StandingOrderJpaRepositoryCustom {

  /** Transaction types a cash standing order may produce; the security subclass covers ACCUMULATE and REDUCE. */
  private static final Set<TransactionType> CASH_TRANSACTION_TYPES = Set.of(TransactionType.WITHDRAWAL,
      TransactionType.DEPOSIT, TransactionType.INTEREST_CASHACCOUNT, TransactionType.FEE);

  @Autowired
  private StandingOrderJpaRepository standingOrderJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private CashaccountJpaRepository cashaccountJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Override
  public StandingOrder saveOnlyAttributes(StandingOrder standingOrder, StandingOrder existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

    // Tenant limit check on create
    if (existingEntity == null) {
      int maxAllowed = globalparametersJpaRepository
          .getMaxValueByKey(GlobalParamKeyDefault.GLOB_KEY_MAX_STANDING_ORDER);
      long current = standingOrderJpaRepository.countByIdTenant(user.getIdTenant());
      if (current >= maxAllowed) {
        throw new DataViolationException("standing.order", "standing.order.limit.exceeded",
            new Object[] {maxAllowed});
      }
    }
    if (existingEntity != null
        && transactionJpaRepository.countByIdStandingOrder(existingEntity.getIdStandingOrder()) > 0
        && !DataHelper.areAnnotatedFieldsEqual(standingOrder, existingEntity, LockedWhenUsed.class)) {
      throw new DataViolationException("standing.order", "standing.order.fields.locked", null);
    }
    normalizeBlankCashFields(standingOrder);
    // The client sends the cash account as an id-only stub, so its currency has to come from the database.
    String cashaccountCurrency = loadCashaccountCurrency(standingOrder, user.getIdTenant());
    validateStandingOrder(standingOrder, cashaccountCurrency);
    resolveCurrencypair(standingOrder, cashaccountCurrency);
    LocalDate today = LocalDate.now();
    if (standingOrder.getNextExecutionDate() == null && standingOrder.getLastExecutionDate() == null
        && !today.isBefore(standingOrder.getValidFrom())) {
      // Brand new standing order: use validFrom as first execution date
      standingOrder.setNextExecutionDate(computeInitialNextExecutionDate(standingOrder));
    } else if (standingOrder.getNextExecutionDate() == null && standingOrder.getLastExecutionDate() != null
        && standingOrder.getValidTo() != null && !standingOrder.getValidTo().isBefore(today)) {
      // Reactivation: validTo was extended, recalculate from lastExecutionDate
      LocalDate nextDate = StandingOrderExecutionService.computeNextExecutionDate(
          standingOrder, standingOrder.getLastExecutionDate());
      if (nextDate != null && !nextDate.isAfter(standingOrder.getValidTo())) {
        standingOrder.setNextExecutionDate(nextDate);
      }
    }
    return RepositoryHelper.saveOnlyAttributes(standingOrderJpaRepository, standingOrder, existingEntity,
        updatePropertyLevelClasses);
  }

  public int delEntityWithTenant(Integer idStandingOrder, Integer idTenant) {
    if (transactionJpaRepository.countByIdStandingOrder(idStandingOrder) > 0) {
      throw new DataViolationException("standing.order", "standing.order.has.transactions", null);
    }
    return standingOrderJpaRepository.deleteByIdStandingOrderAndIdTenant(idStandingOrder, idTenant);
  }

  @Override
  public Set<Class<? extends Annotation>> getUpdatePropertyLevels(final StandingOrder existingEntity) {
    if (existingEntity != null
        && transactionJpaRepository.countByIdStandingOrder(existingEntity.getIdStandingOrder()) > 0) {
      return Set.of(PropertySelectiveUpdatableOrWhenNull.class, PropertyAlwaysUpdatable.class);
    }
    return Set.of(PropertySelectiveUpdatableOrWhenNull.class, PropertyAlwaysUpdatable.class, LockedWhenUsed.class);
  }

  private void validateStandingOrder(StandingOrder so, String cashaccountCurrency) {
    // Validate valid_from < valid_to
    if (so.getValidFrom() != null && so.getValidTo() != null && !so.getValidFrom().isBefore(so.getValidTo())) {
      throw new DataViolationException("valid.from", "standing.order.validfrom.after.validto", null);
    }
    // Validate transaction type per subclass
    if (so instanceof StandingOrderCashaccount soc) {
      if (!CASH_TRANSACTION_TYPES.contains(so.getTransactionType())) {
        throw new DataViolationException("transaction.type", "standing.order.invalid.type", null);
      }
      validateCashAmountAndFormula(soc, cashaccountCurrency);
    } else if (so instanceof StandingOrderSecurity sos) {
      if (so.getTransactionType() != TransactionType.ACCUMULATE
          && so.getTransactionType() != TransactionType.REDUCE) {
        throw new DataViolationException("transaction.type", "standing.order.invalid.type", null);
      }
      // Exactly one of units or investAmount must be set
      boolean hasUnits = sos.getUnits() != null;
      boolean hasAmount = sos.getInvestAmount() != null;
      if (hasUnits == hasAmount) {
        throw new DataViolationException("units", "standing.order.units.xor.amount", null);
      }
    }
    // The entity caps the tolerance at the technical -3..3, an administrator may narrow it further.
    QuoteToleranceRange toleranceRange = globalparametersService.getStandingOrderQuoteToleranceRange();
    if (so.getQuoteToleranceDays() < toleranceRange.min() || so.getQuoteToleranceDays() > toleranceRange.max()) {
      throw new DataViolationException("quote.tolerance.days", "standing.order.tolerance.out.of.range",
          new Object[] { toleranceRange.min(), toleranceRange.max() });
    }
    // Validate day/month for MONTHS/YEARS repeat units
    RepeatUnit ru = so.getRepeatUnit();
    if ((ru == RepeatUnit.MONTHS || ru == RepeatUnit.YEARS)
        && so.getPeriodDayPosition() == PeriodDayPosition.SPECIFIC_DAY && so.getDayOfExecution() == null) {
      throw new DataViolationException("day.of.execution", "standing.order.day.required", null);
    }
    if (ru == RepeatUnit.YEARS && so.getMonthOfExecution() == null) {
      throw new DataViolationException("month.of.execution", "standing.order.month.required", null);
    }
  }

  /**
   * Turns the empty strings an unselected select and an untouched text input submit into null, so that "no amount
   * currency" and "no formula" are stored as null rather than as an empty value that later fails validation or is
   * mistaken for a configured conversion.
   */
  private static void normalizeBlankCashFields(StandingOrder so) {
    if (so instanceof StandingOrderCashaccount soc) {
      if (soc.getAmountCurrency() != null && soc.getAmountCurrency().isBlank()) {
        soc.setAmountCurrency(null);
      }
      if (soc.getCashaccountAmountFormula() != null && soc.getCashaccountAmountFormula().isBlank()) {
        soc.setCashaccountAmountFormula(null);
      }
    }
  }

  /**
   * Validates the amount, the optional foreign amount currency and the optional EvalEx amount formula of a cash
   * standing order.
   *
   * <p>
   * The formula is compiled and evaluated once with neutral values, so that a typo is reported to the user while the
   * dialog is open instead of silently producing a wrong booking every month. This is the guard the security subclass
   * lacks, where {@code evaluateCost} falls back to 0.0 on an error.
   * </p>
   */
  private void validateCashAmountAndFormula(StandingOrderCashaccount soc, String cashaccountCurrency) {
    // FEE is negated unconditionally when the transaction is saved, so a negative value here would credit the account.
    if (soc.getTransactionType() == TransactionType.FEE && soc.getCashaccountAmount() != null
        && soc.getCashaccountAmount() <= 0) {
      throw new DataViolationException("cashaccount.amount", "standing.order.fee.amount.positive", null);
    }
    String amountCurrency = soc.getAmountCurrency();
    if (amountCurrency != null) {
      if (!isSupportedCurrency(amountCurrency)) {
        throw new DataViolationException("amount.currency", "standing.order.amount.currency.invalid",
            new Object[] { amountCurrency });
      }
      if (amountCurrency.equals(cashaccountCurrency)) {
        throw new DataViolationException("amount.currency", "standing.order.amount.currency.same", null);
      }
    }
    String formula = soc.getCashaccountAmountFormula();
    if (formula != null && !formula.isBlank()) {
      try {
        Expression expression = new Expression(formula);
        expression.with("a", BigDecimal.ONE);
        expression.with("r", BigDecimal.ONE);
        expression.evaluate();
      } catch (Exception e) {
        throw new DataViolationException("cashaccount.amount.formula", "standing.order.formula.invalid",
            new Object[] { e.getMessage() });
      }
      // A formula that uses the exchange rate is meaningless without a currency pair to read it from.
      if (usesExchangeRate(formula) && amountCurrency == null) {
        throw new DataViolationException("cashaccount.amount.formula", "standing.order.formula.currency.required",
            null);
      }
    }
  }

  /**
   * Checks whether the formula references the exchange rate variable {@code r}. Matched as a standalone identifier so
   * that neither a function name such as {@code ROUND} nor a variable such as {@code rate} produces a false positive.
   */
  private static boolean usesExchangeRate(String formula) {
    return formula.matches(".*(^|[^A-Za-z0-9_])r([^A-Za-z0-9_]|$).*");
  }

  /**
   * Accepts every ISO currency plus the supported cryptocurrencies, matching the option list served by
   * {@code GlobalparametersService.getCurrencies()}.
   */
  private static boolean isSupportedCurrency(String currency) {
    if (GlobalConstants.CRYPTO_CURRENCY_SUPPORTED.contains(currency)) {
      return true;
    }
    try {
      return Currency.getAvailableCurrencies().contains(Currency.getInstance(currency));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /**
   * Derives {@code idCurrencypair} from the amount currency and the cash account currency. Resolving it here rather
   * than at execution time means the pair exists and its price history is being loaded long before the standing order
   * first fires.
   */
  private void resolveCurrencypair(StandingOrder so, String cashaccountCurrency) {
    if (so instanceof StandingOrderCashaccount soc) {
      soc.setIdCurrencypair(soc.getAmountCurrency() == null ? null
          : currencypairJpaRepository
              .findOrCreateCurrencypairByFromAndToCurrency(soc.getAmountCurrency(), cashaccountCurrency, true)
              .getIdSecuritycurrency());
    }
  }

  /**
   * Reads the currency of the referenced cash account from the database. The request body carries the cash account as
   * an id-only stub, so its currency is not populated by deserialization.
   *
   * @return the cash account currency, or null when the standing order is not a cash standing order
   */
  private String loadCashaccountCurrency(StandingOrder so, Integer idTenant) {
    if (!(so instanceof StandingOrderCashaccount) || so.getCashaccount() == null) {
      return null;
    }
    Cashaccount cashaccount = cashaccountJpaRepository
        .findByIdSecuritycashAccountAndIdTenant(so.getCashaccount().getIdSecuritycashAccount(), idTenant);
    if (cashaccount == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    return cashaccount.getCurrency();
  }

  private LocalDate computeInitialNextExecutionDate(StandingOrder so) {
    LocalDate start = so.getValidFrom();
    if (start == null) {
      start = LocalDate.now();
    }
    return start;
  }
}
