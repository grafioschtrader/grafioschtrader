package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDate;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.RepositoryHelper;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.entities.StandingOrder;
import grafioschtrader.entities.StandingOrderCashaccount;
import grafioschtrader.entities.StandingOrderSecurity;
import grafioschtrader.types.PeriodDayPosition;
import grafioschtrader.types.RepeatUnit;
import grafioschtrader.types.TransactionType;

/**
 * Repository implementation for standing orders with validation logic for scheduling parameters,
 * transaction type constraints, tenant limits, and initial next-execution-date computation.
 */
public class StandingOrderJpaRepositoryImpl extends BaseRepositoryImpl<StandingOrder>
    implements StandingOrderJpaRepositoryCustom {

  @Autowired
  private StandingOrderJpaRepository standingOrderJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

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
    validateStandingOrder(standingOrder);
    LocalDate today = LocalDate.now();
    if (standingOrder.getNextExecutionDate() == null
        && !today.isBefore(standingOrder.getValidFrom()) && !today.isAfter(standingOrder.getValidTo())) {
      standingOrder.setNextExecutionDate(computeInitialNextExecutionDate(standingOrder));
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

  private void validateStandingOrder(StandingOrder so) {
    // Validate valid_from < valid_to
    if (so.getValidFrom() != null && so.getValidTo() != null && !so.getValidFrom().isBefore(so.getValidTo())) {
      throw new DataViolationException("valid.from", "standing.order.validfrom.after.validto", null);
    }
    // Validate transaction type per subclass
    if (so instanceof StandingOrderCashaccount) {
      if (so.getTransactionType() != TransactionType.WITHDRAWAL && so.getTransactionType() != TransactionType.DEPOSIT) {
        throw new DataViolationException("transaction.type", "standing.order.invalid.type", null);
      }
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

  private LocalDate computeInitialNextExecutionDate(StandingOrder so) {
    LocalDate start = so.getValidFrom();
    if (start == null) {
      start = LocalDate.now();
    }
    return start;
  }
}
