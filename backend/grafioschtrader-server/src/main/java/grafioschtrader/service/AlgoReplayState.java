package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafioschtrader.entities.AlgoEventLog;
import grafioschtrader.entities.AlgoSimulationResult;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoEventLogJpaRepository;
import grafioschtrader.repository.AlgoSecurityJpaRepository;
import grafioschtrader.repository.SimulationSourceRepository;
import grafioschtrader.types.AlgoEventType;

/**
 * Everything one historical replay carries from day to day. A field of the orchestrator would be shared between
 * concurrent runs.
 */
class AlgoReplayState {

  final AlgoSimulationResult run;
  final Tenant tenant;
  final AlgoTop algoTop;
  final AlgoReplayMarketData market;
  /**
   * The language every detail resolved on the server is written in. A rationale is a key the client translates, but a
   * detail that carries arguments - a refused evaluation, a dividend audit, a bucket label - cannot be one, so it is
   * resolved in the language of the user who started the run.
   */
  final Locale locale;
  final AlgoReplayAccounts accounts;
  final AlgoReplayIncomeService.Session dividends;
  final AlgoReplayInputs.Snapshot inputs;
  final AlgoReplayTaxes taxes;
  final AlgoReplayCosts costs;
  grafioschtrader.dto.TaxEstimateResult pendingTax;
  final AlgoReplayRoundTrips roundTrips = new AlgoReplayRoundTrips();
  final List<AlgoReplayMetrics.EquityPoint> equity = new ArrayList<>();
  final List<LocalDate> valuationDates = new ArrayList<>();
  /** Sparse pre-order snapshots preserve the equity used by fees before same-day funding transfers. */
  final Map<LocalDate, AlgoHistoricalValuationService.Snapshot> decisionEquity = new LinkedHashMap<>();
  private Supplier<AlgoHistoricalValuationService.Snapshot> currentValuation;
  final Map<Integer, Security> securities = new LinkedHashMap<>();
  final AlgoReplayTerminalSchedule terminalSchedule = new AlgoReplayTerminalSchedule();
  /** Dated external flows are consumed in order when the final daily report is calculated. */
  final NavigableMap<LocalDate, Map<String, Double>> externalCashFlows = new TreeMap<>();
  final Integer eventLimit;
  /** Read once: the hierarchy cannot gain or lose its rebalancing strategy while the run is walking its days. */
  final boolean rebalancingConfigured;
  /**
   * The environment held no position on its opening date, so its instruments have to be bought before the rebalancing
   * has anything to compare. Read from the opening valuation rather than from the initialization mode, because a copy
   * of a portfolio that happened to hold only cash on that day needs the purchase just as much as a manually funded
   * environment does.
   */
  boolean initialPurchaseRequired;
  LocalDate lastRebalancedOn;
  boolean initialPurchaseSettled;
  int initialPurchaseAttempts;
  boolean eventBudgetExhausted;
  /** Whether the fill just booked had to be cut down to what its cash account could pay. */
  boolean lastFillWasReduced;
  /** Funding transfers written so far, capped by {@link AlgoReplayBooking#MAX_FUNDING_TRANSFERS}. */
  int fundingTransfers;
  int done;

  private final AlgoEventLogJpaRepository events;
  private final TransactionTemplate transactionTemplate;
  private final Map<String, Integer> currencyPrecision;

  AlgoReplayState(AlgoSimulationResult run, Tenant tenant, AlgoTop algoTop, AlgoReplayMarketData market,
      boolean initialPurchaseRequired, Locale locale, boolean rebalancingConfigured, Integer eventLimit,
      SimulationSourceRepository source, AlgoAssetclassJpaRepository algoBuckets, AlgoSecurityJpaRepository algoMembers,
      TransactionCostEvalExEstimator costEstimator, TaxEvalExEstimator taxEstimator,
      AlgoReplayIncomeService incomeService, AlgoEventLogJpaRepository events, TransactionTemplate transactionTemplate,
      Map<String, Integer> currencyPrecision) {
    this.run = run;
    this.tenant = tenant;
    this.algoTop = algoTop;
    this.market = market;
    this.initialPurchaseRequired = initialPurchaseRequired;
    this.locale = locale;
    this.rebalancingConfigured = rebalancingConfigured;
    this.eventLimit = eventLimit;
    this.events = events;
    this.transactionTemplate = transactionTemplate;
    this.currencyPrecision = currencyPrecision;
    List<Transaction> openingLedger = source.transactions(run.getIdTenant(), run.getOpeningDate().plusDays(1));
    List<Securityaccount> securityaccounts = source.securityaccounts(run.getIdTenant());
    this.accounts = new AlgoReplayAccounts(securityaccounts, source.cashaccounts(run.getIdTenant()), openingLedger,
        tenant.getCurrency());
    this.inputs = AlgoReplayInputs.read(run.getInputAssumptionsJson());
    market.setInputs(inputs);
    this.taxes = new AlgoReplayTaxes(inputs, taxEstimator, run.getOpeningDate(), currencyPrecision);
    this.costs = new AlgoReplayCosts(new AlgoReplayFees(securityaccounts, costEstimator), taxes, inputs);
    openingLedger.forEach(t -> {
      if (t.getSecurity() != null) {
        securities.put(t.getSecurity().getId(), t.getSecurity());
      }
    });
    // A strategic bucket may name an instrument the environment does not hold yet, so the hierarchy is the
    // authoritative source of what a rebalancing line can refer to.
    Integer owner = tenant.getIdParentTenant();
    algoBuckets.findByIdTenantAndIdAlgoAssetclassParent(owner, algoTop.getId())
        .forEach(bucket -> algoMembers.findByIdAlgoSecurityParentAndIdTenant(bucket.getId(), owner).forEach(m -> {
          if (m.getSecurity() != null) {
            securities.put(m.getSecurity().getId(), m.getSecurity());
          }
        }));
    securities.keySet().forEach(this::registerExpiry);
    this.dividends = incomeService.open(
        new AlgoReplayIncomeBookingService.Context(run.getIdSimulationResult(), run.getIdTenant(), tenant.getCurrency(),
            market),
        securities.values(), run.getOpeningDate(), run.getEndDate(), inputs, taxes, precision(tenant.getCurrency()),
        locale, entry -> write(entry.type(), entry.date(), null, entry.security(), entry.units(), entry.price(),
            entry.amount(), entry.currency(), entry.rationale(), entry.details()));
    market.setReceivables(dividends::receivables);
  }

  /**
   * What the environment is worth on the day being evaluated, for a fee model that grades its fee by the size of the
   * account. Calculated only if an order needs it, then retained before any funding movement that order makes.
   */
  double equityNow() {
    return currentValuation == null ? 0
        : decisionEquity.computeIfAbsent(lastValuationDate(), _ -> currentValuation.get()).equity();
  }

  void observeEquity(LocalDate date, Supplier<AlgoHistoricalValuationService.Snapshot> valuation) {
    if (valuationDates.isEmpty() || !valuationDates.getLast().equals(date))
      valuationDates.add(date);
    currentValuation = valuation;
  }

  LocalDate lastValuationDate() {
    return valuationDates.isEmpty() ? run.getOpeningDate() : valuationDates.getLast();
  }

  Integer idTenant() {
    return run.getIdTenant();
  }

  /** A newly encountered strategy instrument also receives its captured expiry event. */
  void includeSecurity(Security security) {
    if (securities.putIfAbsent(security.getId(), security) == null) {
      registerExpiry(security.getId());
    }
  }

  private void registerExpiry(Integer security) {
    var instrument = inputs.instruments().get(security);
    if (instrument != null) {
      terminalSchedule.register(security, instrument.activeToDate());
    }
  }

  void addExternalCashFlow(LocalDate date, String currency, double amount) {
    externalCashFlows.computeIfAbsent(date, _ -> new LinkedHashMap<>()).merge(currency, amount, Double::sum);
  }

  double consumeExternalCashFlow(LocalDate date, Map<String, Double> fx) {
    var throughDate = externalCashFlows.headMap(date, true);
    double flow = throughDate.values().stream().flatMap(amounts -> amounts.entrySet().stream())
        .mapToDouble(entry -> entry.getValue() * fx.get(entry.getKey())).sum();
    throughDate.clear();
    return flow;
  }

  void write(AlgoEventType type, LocalDate date, Integer idAlgoStrategy, Integer idSecurity, Double units, Double price,
      Double amount, String currency, String rationale) {
    write(type, date, idAlgoStrategy, idSecurity, units, price, amount, currency, rationale, null);
  }

  void write(AlgoEventType type, LocalDate date, Integer idAlgoStrategy, Integer idSecurity, Double units, Double price,
      Double amount, String currency, String rationale, String details) {
    if (eventBudgetExhausted) {
      return;
    }
    if (eventLimit != null && events.countByIdTenant(idTenant()) >= eventLimit) {
      eventBudgetExhausted = true;
      return;
    }
    AlgoEventLog entry = new AlgoEventLog();
    entry.setIdSimulationResult(run.getIdSimulationResult());
    entry.setIdTenant(idTenant());
    entry.setIdAlgoStrategy(idAlgoStrategy);
    entry.setIdSecuritycurrency(idSecurity);
    entry.setEventDate(date);
    entry.setEventType(type);
    entry.setUnits(round(units, BaseConstants.FID_MAX_FRACTION_DIGITS));
    entry.setPrice(round(price, BaseConstants.FID_MAX_FRACTION_DIGITS));
    entry.setAmount(round(amount, precision(currency)));
    entry.setCurrency(currency);
    entry.setRationale(truncate(rationale, 255));
    entry.setDetails(truncate(details, 1000));
    transactionTemplate.executeWithoutResult(_ -> events.save(entry));
  }

  /** The two rows that bound the trail carry no instrument and no amount, only what the run was asked to do. */
  void writeMarker(AlgoEventType type, LocalDate date, String details) {
    write(type, date, null, null, null, null, null, null, null, details);
  }

  private static String truncate(String value, int length) {
    return value == null || value.length() <= length ? value : value.substring(0, length);
  }

  private int precision(String currency) {
    return currency == null ? BaseConstants.FID_STANDARD_FRACTION_DIGITS
        : currencyPrecision.getOrDefault(currency, BaseConstants.FID_STANDARD_FRACTION_DIGITS);
  }

  private static Double round(Double value, int precision) {
    return value == null ? null : DataHelper.round(value, precision);
  }
}
