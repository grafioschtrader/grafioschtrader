package grafioschtrader.instrument;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import grafioschtrader.entities.SecurityActionApplication;
import grafioschtrader.entities.Transaction;
import grafioschtrader.instrument.SpinOffAllocator.Allocation;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.CostLot;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.types.CostBasisMethod;
import grafioschtrader.types.SecurityActionRole;

/**
 * One calculation scope for the spin-off applications and lot allocations used by a report. Parent history is loaded at
 * most once per instrument and replayed only up to the ex-date.
 */
public final class SpinOffContext {
  private final SecurityCalcService calculator;
  private final Integer tenant;
  private final Set<Integer> accounts;
  private Map<Integer, SecurityActionApplication> applications;
  private final Map<Integer, List<Transaction>> history = new HashMap<>();
  private final Map<Integer, List<Transaction>> legs = new HashMap<>();
  private final Map<FxKey, Double> rates = new HashMap<>();
  private final Map<Integer, Map<Integer, Allocation>> allocations = new HashMap<>();

  SpinOffContext(SecurityCalcService calculator, Integer tenant, Set<Integer> accounts) {
    this.calculator = calculator;
    this.tenant = tenant;
    this.accounts = accounts;
  }

  Allocation allocation(Transaction leg, SecurityPositionSummary inMemoryParent, boolean excludeDivTax,
      DateTransactionCurrencypairMap currencyMap, CostBasisMethods methods, double units) {
    Integer applicationId = leg.getIdSecurityActionApp();
    Integer account = leg.getIdSecurityaccount();
    if (applicationId == null || tenant == null) {
      return missing(leg, methods, units);
    }
    if (applications == null) {
      applications = calculator.spinOffApplications(tenant);
    }
    SecurityActionApplication application = applications.get(applicationId);
    if (application == null || application.getCostAllocation() == null) {
      return missing(leg, methods, units);
    }
    // Explicit get/put allows an earlier event to fill a different entry during private replay.
    Map<Integer, Allocation> byAccount = allocations.get(applicationId);
    if (byAccount == null) {
      byAccount = resolve(application, inMemoryParent, excludeDivTax, currencyMap, methods);
      allocations.put(applicationId, byAccount);
    }
    Allocation result = byAccount.get(account);
    return result == null ? missing(leg, methods, units) : result;
  }

  private Map<Integer, Allocation> resolve(SecurityActionApplication application,
      SecurityPositionSummary inMemoryParent, boolean excludeDivTax, DateTransactionCurrencypairMap currencyMap,
      CostBasisMethods methods) {
    var action = application.getSecurityAction();
    var parent = action.getSecurityOld();
    var child = action.getSecurityNew();
    LocalDate date = action.getActionDate();
    List<Transaction> actionLegs = legs.computeIfAbsent(application.getId(), calculator::spinOffLegs);
    String main = currencyMap == null ? null : currencyMap.getMainCurrency();
    double fx = rate(parent.getCurrency(), child.getCurrency(), date);
    SecurityPositionSummary state = inMemoryParent;
    if (state == null) {
      List<Transaction> before = history.computeIfAbsent(parent.getId(), id -> calculator.spinOffHistory(tenant, id))
          .stream().filter(t -> inScope(t) && t.getTransactionDate().isBefore(date)).toList();
      state = calculator.spinOffParentState(parent, before, excludeDivTax, currencyMap, methods, this);
    }
    Map<Integer, Allocation> result = new HashMap<>();
    int parentPrecision = calculator.spinOffPrecision(parent.getCurrency());
    int childPrecision = calculator.spinOffPrecision(child.getCurrency());
    Double parentRate = main == null ? null : rate(parent.getCurrency(), main, date);
    double parentFactor = calculator.spinOffSplitFactor(parent.getId(), date, currencyMap);
    double childFactor = calculator.spinOffSplitFactor(child.getId(), date, currencyMap);
    double ratio = (double) action.getToFactor() / action.getFromFactor();
    for (Transaction childLeg : actionLegs) {
      if (childLeg.getSecurityActionRole() == SecurityActionRole.SPIN_OFF_CHILD && inScope(childLeg)) {
        double childUnits = childLeg.getUnits();
        var method = methods.forAccount(childLeg.getIdSecurityaccount());
        result.put(childLeg.getIdSecurityaccount(),
            fx <= 0 ? missing(childLeg, methods, childUnits * childFactor)
                : SpinOffAllocator.allocate(state, childLeg, method, childUnits / ratio * parentFactor,
                    childUnits * childFactor, application.getCostAllocation(), fx, parentRate, parentPrecision,
                    childPrecision));
      }
    }
    return result;
  }

  private boolean inScope(Transaction t) {
    return accounts == null || accounts.contains(t.getIdSecurityaccount());
  }

  record FxKey(String from, String to, LocalDate date) {
  }

  /**
   * Closing rate of a currency pair on a day, 1 for the same currency and 0 when the pair has no close that day.
   * Rates are cached for the lifetime of the context.
   */
  public double rate(String from, String to, LocalDate date) {
    return from.equals(to) ? 1 : rates.computeIfAbsent(new FxKey(from, to, date), calculator::spinOffRate);
  }

  private Allocation missing(Transaction leg, CostBasisMethods methods, double units) {
    List<CostLot> inherited = leg.getSecurityActionRole() == SecurityActionRole.SPIN_OFF_CHILD
        && methods.forAccount(leg.getIdSecurityaccount()) != CostBasisMethod.AVERAGE_COST
            ? List.of(new CostLot(leg.getId(), null, leg.getTransactionTime(), null, units, 0, true))
            : List.of();
    return new Allocation(0, 0, List.of(), inherited, 0, true);
  }
}
