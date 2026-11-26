package grafioschtrader.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.DataViolationException;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.SpinOffDecisionDto;
import grafioschtrader.dto.SpinOffPreviewDto;
import grafioschtrader.dto.SpinOffPreviewDto.Account;
import grafioschtrader.dto.SpinOffPreviewDto.Candidate;
import grafioschtrader.dto.SpinOffPreviewDto.Decision;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityAction;
import grafioschtrader.entities.SecurityActionApplication;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.CashaccountJpaRepository;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityActionApplicationJpaRepository;
import grafioschtrader.repository.SecurityActionJpaRepository;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.service.SpinOffLegs.Rate;
import grafioschtrader.types.SecurityActionRole;
import grafioschtrader.types.SecurityActionType;
import grafioschtrader.types.TaskTypeExtended;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManager;

/** Previews, books and reverses the cash-neutral legs for one tenant. */
@Service
public class SpinOffApplyService {
  private final SecurityActionJpaRepository actions;
  private final SecurityActionApplicationJpaRepository applications;
  private final TransactionJpaRepository transactions;
  private final SecurityaccountJpaRepository accounts;
  private final CashaccountJpaRepository cashAccounts;
  private final HistoryquoteJpaRepository quotes;
  private final SecuritysplitJpaRepository splits;
  private final CurrencypairJpaRepository pairs;
  private final GlobalparametersService parameters;
  private final TaskDataChangeJpaRepository tasks;
  private final TenantJpaRepository tenants;
  private final EntityManager entityManager;
  private final SpinOffParentRefreshService parentRefresh;

  public SpinOffApplyService(SecurityActionJpaRepository actions, SecurityActionApplicationJpaRepository applications,
      TransactionJpaRepository transactions, SecurityaccountJpaRepository accounts,
      CashaccountJpaRepository cashAccounts, HistoryquoteJpaRepository quotes, SecuritysplitJpaRepository splits,
      CurrencypairJpaRepository pairs, GlobalparametersService parameters, TaskDataChangeJpaRepository tasks,
      TenantJpaRepository tenants, EntityManager entityManager, SpinOffParentRefreshService parentRefresh) {
    this.actions = actions;
    this.applications = applications;
    this.transactions = transactions;
    this.accounts = accounts;
    this.cashAccounts = cashAccounts;
    this.quotes = quotes;
    this.splits = splits;
    this.pairs = pairs;
    this.parameters = parameters;
    this.tasks = tasks;
    this.tenants = tenants;
    this.entityManager = entityManager;
    this.parentRefresh = parentRefresh;
  }

  @Transactional(readOnly = true)
  public SpinOffPreviewDto preview(Integer id, Integer tenant) {
    SecurityAction action = actions.findById(id).filter(a -> a.getActionType() == SecurityActionType.SPIN_OFF)
        .orElseThrow(() -> invalid("gt.security.action.not.found"));
    requireUnapplied(id, tenant);
    return buildPlan(action, tenant, false).preview();
  }

  @Transactional
  public SecurityActionApplication apply(Integer id, Integer tenant, List<SpinOffDecisionDto> decisions) {
    tenants.lockMonitoringTenant(tenant);
    SecurityAction action = actions.findByIdForUpdate(id).orElseThrow(() -> invalid("gt.security.action.not.found"));
    entityManager.refresh(action);
    Optional<SecurityActionApplication> existing = requireUnapplied(id, tenant);
    if (decisions == null) {
      throw stale();
    }
    if (decisions.isEmpty()) {
      return result(new SecurityActionApplication(), action, 0, 0);
    }
    Plan plan = buildPlan(action, tenant, true);
    Map<Integer, SpinOffDecisionDto> accepted = validateDecisions(decisions, plan);
    int booked = (int) decisions.stream().filter(d -> d.decision() == Decision.BOOK).count();
    transactions.throwWhenTransactionLimitReached(tenant, 3 * booked);

    SecurityActionApplication application = existing.orElseGet(SecurityActionApplication::new);
    application.setSecurityAction(action);
    application.setIdTenant(tenant);
    application.setAppliedTime(LocalDateTime.now());
    application.setReversed(false);
    application.setCostAllocation(action.getCostAllocation());
    applications.save(application);
    SpinOffPreviewDto preview = plan.preview();
    for (AccountPlan row : plan.accounts()) {
      if (accepted.get(row.preview().idSecurityaccount()).decision() == Decision.BOOK) {
        List<Transaction> legs = SpinOffLegs.book(action, tenant, application.getId(), row.cash(), row.preview(),
            preview.parentClose(), preview.childClose(), preview.fxChildToParent(), row.parentRate(), row.childRate(),
            row.precision());
        transactions.saveAll(legs);
        if (application.getIdTransactionSell() == null) {
          application.setIdTransactionSell(legs.getFirst().getId());
          application.setIdTransactionBuy(legs.getLast().getId());
        }
      }
    }
    actions.incrementAppliedCount(id, 1);
    entityManager.refresh(action);
    scheduleRebuild(tenant);
    return result(application, action, booked, decisions.size() - booked);
  }

  @Transactional
  public void reverse(Integer id, Integer tenant) {
    tenants.lockMonitoringTenant(tenant);
    SecurityAction action = actions.findByIdForUpdate(id).orElseThrow(() -> invalid("gt.security.action.not.found"));
    var application = applications.findBySecurityAction_IdSecurityActionAndIdTenant(id, tenant)
        .orElseThrow(() -> invalid("gt.security.action.not.applied"));
    if (application.isReversed()) {
      throw invalid("gt.security.action.already.reversed");
    }
    List<Transaction> legs = transactions.findByIdSecurityActionApp(application.getId()).stream()
        .filter(t -> t.getSecurityActionRole() != null).toList();
    // The legs of a simulation environment are a copy of the source tenant's opening state, never its own application
    if (legs.stream().anyMatch(Transaction::isSimulationOpening)) {
      throw new GeneralNotTranslatedWithArgumentsException("gt.simulation.opening.protected", null);
    }
    requireNoDependentBooking(action, application, tenant, legs);

    application.setIdTransactionSell(null);
    application.setIdTransactionBuy(null);
    application.setReversed(true);
    applications.flush();
    transactions.deleteAll(legs);
    actions.incrementAppliedCount(id, -1);
    scheduleRebuild(tenant);
  }

  /**
   * Refuses the reversal while something depends on the child purchase: a later booking of the child in the same
   * security account that does not belong to this application, such as the cash settlement of a fraction, or another
   * active application of a security action dated on or after the spin-off that involves the child. A security action
   * dated before the spin-off, for example an ISIN change that created the child, does not depend on it. The message
   * names the date, the child and, for a booking, the security account.
   */
  private void requireNoDependentBooking(SecurityAction action, SecurityActionApplication application,
      Integer tenant, List<Transaction> legs) {
    Security child = action.getSecurityNew();
    Integer childId = child.getId();
    var childTransactions = transactions.findByIdTenantAndIdSecurity(tenant, childId);
    Optional<Transaction> blockingTrade = legs.stream()
        .filter(leg -> leg.getSecurityActionRole() == SecurityActionRole.SPIN_OFF_CHILD)
        .flatMap(leg -> childTransactions.stream()
            .filter(t -> Objects.equals(leg.getIdSecurityaccount(), t.getIdSecurityaccount())
                && !Objects.equals(t.getIdSecurityActionApp(), application.getId())
                && Transaction.CALC_ORDER.compare(t, leg) > 0))
        .min(Comparator.comparing(Transaction::getTransactionDate));
    if (blockingTrade.isPresent()) {
      Transaction trade = blockingTrade.get();
      var account = accounts.findByIdSecuritycashAccountAndIdTenant(trade.getIdSecurityaccount(), tenant);
      throw new DataViolationException("action.date", "gt.security.action.spinoff.reverse.blocked",
          new Object[] { trade.getTransactionDate(), child.getName(), account == null ? "" : account.getName() });
    }
    applications.findByIdTenant(tenant).stream()
        .filter(a -> !a.isReversed() && !a.getId().equals(application.getId()))
        .map(SecurityActionApplication::getSecurityAction)
        .filter(other -> !other.getActionDate().isBefore(action.getActionDate()))
        .filter(other -> childId.equals(other.getSecurityOld().getId())
            || (other.getSecurityNew() != null && childId.equals(other.getSecurityNew().getId())))
        .map(SecurityAction::getActionDate).min(Comparator.naturalOrder()).ifPresent(date -> {
          throw new DataViolationException("action.date", "gt.security.action.spinoff.reverse.blocked.action",
              new Object[] { date, child.getName() });
        });
  }

  private Optional<SecurityActionApplication> requireUnapplied(Integer id, Integer tenant) {
    var existing = applications.findBySecurityAction_IdSecurityActionAndIdTenant(id, tenant);
    if (existing.isPresent() && !existing.get().isReversed()) {
      throw invalid("gt.security.action.already.applied");
    }
    return existing;
  }

  private record AccountPlan(Account preview, Cashaccount cash, Rate parentRate, Rate childRate, int precision) {
  }

  private record Plan(SpinOffPreviewDto preview, List<AccountPlan> accounts) {
  }

  private Plan buildPlan(SecurityAction action, Integer tenant, boolean applyingPreview) {
    LocalDate date = action.getActionDate();
    Integer parentId = action.getSecurityOld().getId();
    Integer childId = action.getSecurityNew().getId();
    parentRefresh.requireNotPending(parentId);
    SpinOffActionService.requireLaterHistoryDetermined(actions, parentId, childId, date);
    List<Transaction> parentTrades = transactions.findByIdTenantAndIdSecurity(tenant, parentId);
    List<Transaction> childTrades = transactions.findByIdTenantAndIdSecurity(tenant, childId);
    if (java.util.stream.Stream.concat(parentTrades.stream(), childTrades.stream())
        .anyMatch(t -> t.isSimulationOpening() && t.getTransactionDate().isAfter(date))) {
      throw new GeneralNotTranslatedWithArgumentsException("gt.simulation.opening.protected", null);
    }
    List<Securitysplit> parentSplits = splits.findByIdSecuritycurrencyOrderBySplitDateAsc(parentId);
    List<Securitysplit> childSplits = splits.findByIdSecuritycurrencyOrderBySplitDateAsc(childId);
    Map<Integer, Double> entitlements = entitlement(parentTrades, parentSplits, date);
    if (entitlements.isEmpty()) {
      throw applyingPreview ? stale() : invalid("gt.security.action.no.holdings");
    }
    double parentClose = quotes.findByIdSecuritycurrencyAndDate(parentId, date).filter(SpinOffApplyService::validClose)
        .map(q -> q.getClose() * Securitysplit.calcSplitFatorForFromDate(parentSplits, date))
        .orElseThrow(() -> invalid("gt.security.action.no.price.on.date"));
    Historyquote childPrice = quotes.findByIdSecuritycurrencyAndDateBetweenOrderByDate(childId, date, date.plusDays(10))
        .stream().filter(SpinOffApplyService::validClose).findFirst()
        .orElseThrow(() -> invalid("gt.security.action.spinoff.no.child.price"));
    double childClose = childPrice.getClose() * Securitysplit.calcSplitFatorForFromDate(childSplits, date);
    String parentCurrency = action.getSecurityOld().getCurrency();
    String childCurrency = action.getSecurityNew().getCurrency();
    double childToParent = exchangeRate(childCurrency, parentCurrency, date).multiplier();
    double ratio = (double) action.getToFactor() / action.getFromFactor();
    double salePrice = SpinOffLegs.theoreticalParentClose(parentClose, childClose, ratio, childToParent);
    List<AccountPlan> plans = new ArrayList<>();
    for (var entry : entitlements.entrySet()) {
      Integer accountId = entry.getKey();
      var account = accounts.findByIdSecuritycashAccountAndIdTenant(accountId, tenant);
      if (account == null) {
        throw invalid("gt.security.action.no.holdings");
      }
      Cashaccount cash = preferredCashaccount(account.getPortfolio().getId(), parentCurrency);
      String currency = cash.getCurrency();
      Rate parentRate = exchangeRate(parentCurrency, currency, date);
      Rate childRate = exchangeRate(childCurrency, currency, date);
      int precision = parameters.getPrecisionForCurrency(currency);
      double n = entry.getValue();
      double m = n * ratio;
      var amounts = SpinOffLegs.amounts(n, parentClose, salePrice, parentRate.multiplier(), precision);
      List<Transaction> parentWindow = candidates(parentTrades, accountId, date);
      List<Transaction> childWindow = candidates(childTrades, accountId, date);
      boolean recorded = manuallyRecorded(parentWindow, childWindow, n, m, parentSplits, childSplits, date);
      List<Candidate> candidates = new ArrayList<>();
      parentWindow.forEach(t -> candidates.add(candidate(t, false)));
      childWindow.forEach(t -> candidates.add(candidate(t, true)));
      candidates.sort(Comparator.comparing(Candidate::transactionTime).thenComparing(Candidate::idTransaction));
      plans.add(new AccountPlan(new Account(accountId, account.getName(), currency, n, m, m - Math.floor(m),
          parentRate.multiplier(), childRate.multiplier(), amounts.sale(), amounts.repurchase(), amounts.child(),
          candidates, recorded ? Decision.SKIP : Decision.BOOK), cash, parentRate, childRate, precision));
    }
    return new Plan(new SpinOffPreviewDto(date, childPrice.getDate(), parentClose, parentCurrency, childClose,
        childCurrency, childToParent, plans.stream().map(AccountPlan::preview).toList()), plans);
  }

  static Map<Integer, Double> entitlement(List<Transaction> parentTrades, List<Securitysplit> splits, LocalDate date) {
    Map<Integer, Double> units = new TreeMap<>();
    for (Transaction trade : parentTrades) {
      int sign = switch (trade.getTransactionType()) {
      case ACCUMULATE -> 1;
      case REDUCE -> -1;
      default -> 0;
      };
      if (sign != 0 && trade.getIdSecurityaccount() != null && trade.getTransactionDate().isBefore(date)) {
        units.merge(trade.getIdSecurityaccount(), sign * unitsOnDate(trade, splits, date), Double::sum);
      }
    }
    units.values().removeIf(n -> DataBusinessHelper.round(n) <= 0);
    return units;
  }

  private static double unitsOnDate(Transaction tx, List<Securitysplit> splits, LocalDate date) {
    return tx.getUnits() * Securitysplit.calcSplitFatorForFromDate(splits, tx.getTransactionDate())
        / Securitysplit.calcSplitFatorForFromDate(splits, date);
  }

  private List<Transaction> candidates(List<Transaction> trades, Integer account, LocalDate date) {
    return trades.stream()
        .filter(t -> account.equals(t.getIdSecurityaccount()) && t.getIdSecurityActionApp() == null
            && !t.isSimulationOpening() && !t.getTransactionDate().isBefore(date.minusDays(10))
            && !t.getTransactionDate().isAfter(date.plusDays(10)))
        .sorted(Transaction.CALC_ORDER).toList();
  }

  /**
   * Tells whether the tenant appears to have recorded the spin-off of an account by own bookings, so that the preview
   * proposes to skip it. Either sign is enough: a purchase of the child over the allotted units (within one unit for a
   * settled fraction), as an import of the bank's delivery statement creates it, or a sale of the parent units followed
   * by their repurchase. The candidates come from the window around the action date and exclude system bookings.
   */
  static boolean manuallyRecorded(List<Transaction> parent, List<Transaction> child, double n, double m,
      List<Securitysplit> parentSplits, List<Securitysplit> childSplits, LocalDate date) {
    boolean childRecorded = child.stream()
        .anyMatch(t -> t.getIdSecurityTransfer() == null && t.getUnits() != null
            && t.getTransactionType() == TransactionType.ACCUMULATE
            && Math.abs(unitsOnDate(t, childSplits, date) - m) <= 1);
    if (childRecorded) {
      return true;
    }
    List<Transaction> matchingParent = parent.stream().filter(trade -> trade.getIdSecurityTransfer() == null
        && trade.getUnits() != null && DataBusinessHelper.round(unitsOnDate(trade, parentSplits, date) - n) == 0)
        .toList();
    Optional<LocalDateTime> sale = matchingParent.stream().filter(t -> t.getTransactionType() == TransactionType.REDUCE)
        .map(Transaction::getTransactionTime).min(Comparator.naturalOrder());
    return sale.isPresent() && matchingParent.stream().anyMatch(
        t -> t.getTransactionType() == TransactionType.ACCUMULATE && !t.getTransactionTime().isBefore(sale.get()));
  }

  private Candidate candidate(Transaction trade, boolean child) {
    return new Candidate(trade.getId(), trade.getTransactionTime(), child, trade.getTransactionType(), trade.getUnits(),
        trade.getQuotation());
  }

  private Cashaccount preferredCashaccount(Integer portfolio, String preferredCurrency) {
    List<Cashaccount> available = cashAccounts.findByPortfolio_IdPortfolio(portfolio);
    return available.stream().filter(cash -> preferredCurrency.equals(cash.getCurrency())).findFirst()
        .or(() -> available.stream().findFirst()).orElseThrow(() -> invalid("gt.security.transfer.no.cashaccount"));
  }

  private Rate exchangeRate(String from, String to, LocalDate date) {
    if (from.equals(to)) {
      return new Rate(null, 1);
    }
    var rates = pairs.findByFromCurrencyAndToCurrencyOrToCurrencyAndFromCurrency(from, to);
    for (var pair : rates) {
      Double close = pairs.getHistoricalClosePriceForDate(pair, date);
      if (close != null && close > 0 && Double.isFinite(close)) {
        return new Rate(pair.getId(),
            DataBusinessHelper.divideMultiplyExchangeRate(1, close, from, to, !from.equals(pair.getFromCurrency())));
      }
    }
    throw new DataViolationException("action.date", "gt.security.action.spinoff.no.fx.rate",
        new Object[] { from, to, date });
  }

  private static boolean validClose(Historyquote quote) {
    return quote.getClose() > 0 && Double.isFinite(quote.getClose());
  }

  private Map<Integer, SpinOffDecisionDto> validateDecisions(List<SpinOffDecisionDto> decisions, Plan plan) {
    Map<Integer, SpinOffDecisionDto> byAccount = new HashMap<>();
    for (SpinOffDecisionDto decision : decisions) {
      if (decision == null || decision.decision() == null || decision.parentUnits() == null
          || !Double.isFinite(decision.parentUnits())
          || byAccount.putIfAbsent(decision.idSecurityaccount(), decision) != null) {
        throw stale();
      }
    }
    if (byAccount.size() != plan.accounts().size()) {
      throw stale();
    }
    for (AccountPlan row : plan.accounts()) {
      var decision = byAccount.get(row.preview().idSecurityaccount());
      if (decision == null || Math.abs(decision.parentUnits() - row.preview().parentUnits()) > 1e-8) {
        throw stale();
      }
    }
    return byAccount;
  }

  private SecurityActionApplication result(SecurityActionApplication application, SecurityAction action, int booked,
      int skipped) {
    application.setSecurityAction(action);
    application.setBookedAccounts(booked);
    application.setSkippedAccounts(skipped);
    application.setAlreadyPerformedCount(skipped);
    return application;
  }

  private void scheduleRebuild(Integer tenant) {
    tasks.save(new TaskDataChange(TaskTypeExtended.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT,
        TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusMinutes(1), tenant, Tenant.class.getSimpleName()));
  }

  private static DataViolationException stale() {
    return invalid("gt.security.action.spinoff.preview.stale");
  }

  private static DataViolationException invalid(String key) {
    return new DataViolationException("action.date", key, null);
  }
}
