package grafioschtrader.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.security.UserAuthentication;
import grafiosch.service.EntityLimitService;
import grafioschtrader.algo.RebalancingPlan;
import grafioschtrader.config.AlgoReplayConfig;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.dto.AlgoHierarchyDto;
import grafioschtrader.entities.AlgoEventLog;
import grafioschtrader.entities.AlgoSimulationResult;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.exceptions.TransactionLimitExceededException;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoEventLogJpaRepository;
import grafioschtrader.repository.AlgoExecutionStateJpaRepository;
import grafioschtrader.repository.AlgoReplayRepository;
import grafioschtrader.repository.AlgoSecurityJpaRepository;
import grafioschtrader.repository.AlgoSimulationResultJpaRepository;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.repository.AlgoTradingRepository;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.SimulationLedgerCache;
import grafioschtrader.repository.SimulationSourceRepository;
import grafioschtrader.service.AlgoMeanReversionDecisionService.Decision;
import grafioschtrader.service.AlgoMeanReversionScopeEvaluator.Proposal;
import grafioschtrader.types.AlgoEventType;
import grafioschtrader.types.AlgoRebalancingTrigger;
import grafioschtrader.types.AlgoRecommendationAction;
import grafioschtrader.types.AlgoSimulationRunStatus;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.TransactionType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Replays a simulation environment through history: the mean reversion modules decide at every closing day after the
 * opening date, the rebalancing decides at its configured interval, and the orders they produce are booked as ordinary
 * portfolio transactions.
 *
 * <p>
 * How the environment opened decides how it starts trading. One that holds nothing - manual cash, or a liquidated copy
 * of the portfolio - first performs an initial purchase, sized by the rebalancing rules but recorded as a purchase of
 * its own, and the checkpoint interval starts counting at the day that purchase completed. One that opens holding
 * positions has no purchase phase: its first checkpoint is due immediately and brings the existing holdings to their
 * targets.
 * </p>
 *
 * <p>
 * After that the rebalancing is compared every {@code 365 / timePeriodPerYear} days and not in between. The two
 * configured values answer two different questions - the interval whether to compare at all, the tolerance which lines
 * to trade - so a drift on a day between two checkpoints is reported but not acted on.
 * </p>
 *
 * <p>
 * Two properties are what make the result mean anything. The run reads its observations exclusively through
 * {@link AlgoReplayMarketData}, which cannot return a quote later than the day being decided, so no decision can see
 * its own future. And it restores the recorded opening state before it starts, so a second run of the same
 * configuration produces the same fills instead of trading on top of the previous run's positions.
 * </p>
 *
 * <p>
 * The conventions the numbers are calculated under are recorded with the run rather than assumed: an order decided at a
 * close is filled at the next close of that instrument, with the captured fee model and without slippage, and the
 * metrics follow {@link AlgoReplayMetrics}. Imported margin instruments and instruments whose leverage differs from one
 * are closed before ordinary trading begins. Margin closures retain each opening lot and its value per point; excluded
 * instruments cannot be bought again, and their allocation is redistributed for this run only.
 * </p>
 *
 * <p>
 * One of those conventions is worth stating on its own, because it changes the environment rather than only measuring
 * it. A rebalancing is sized against the equity of the whole environment, while one order is paid by one cash account,
 * so an environment whose cash is spread over several accounts could not execute an allocation it has the money for.
 * The run therefore moves money between the cash accounts of the environment when, and only when, no single account can
 * pay an order outright - the same thing the holder of such a portfolio would do. It follows that from the day of the
 * first such transfer the currency exposure of the environment is not the one it was opened with any more, which is
 * what {@code FUND_BY_INTERNAL_TRANSFER} in the conventions of the run says.
 * </p>
 */
@Service
public class AlgoHistoricalReplayService {

  private static final Logger log = LoggerFactory.getLogger(AlgoHistoricalReplayService.class);
  /** Shortest wall-clock distance between two progress writes of a running replay. */
  private static final long PROGRESS_INTERVAL_NANOS = 1_000_000_000L;

  /** The assumptions that hold for every run, whatever the environment it replays is configured with. */
  public static final String CONVENTIONS = "NEXT_CLOSE_FILL NO_SLIPPAGE"
      + " TRADE_PL_IN_INSTRUMENT_CURRENCY SHARPE_RF_ZERO ANNUALIZE_365 SHARPE_ANNUALIZE_252"
      + " INITIAL_PURCHASE_THEN_CHECKPOINT_REBALANCE FUND_BY_INTERNAL_TRANSFER"
      + " REPLAY_GROSS_DIVIDENDS REPLAY_DIVIDENDS_AFTER_OPENING REPLAY_TRADE_PL_EXCLUDES_DIVIDENDS"
      + " REPLAY_DIVIDEND_DATA_COVERAGE REPLAY_BOND_REDEEM_AT_PAR REPLAY_EXTERNAL_CASH_FLOWS";

  /** Stated by a run no security account of which resolves a fee model; that run trades for nothing. */
  public static final String NO_TRANSACTION_COST = "NO_TRANSACTION_COST";

  /** Stated by a run that charges what the fee model of the settling security account charges. */
  public static final String FEE_MODEL_TRANSACTION_COST = "FEE_MODEL_TRANSACTION_COST";

  /**
   * What the result view lists as the assumptions the figures were produced under. Whether trades cost anything is the
   * one assumption that differs between two environments, so it is stated per run instead of being part of the
   * constant.
   *
   * @param feeModelActive whether any security account of the environment resolves a fee model
   * @return the conventions of that run, as space separated NLS keys
   */
  public static String conventions(boolean feeModelActive) {
    return CONVENTIONS + " " + (feeModelActive ? FEE_MODEL_TRANSACTION_COST : NO_TRANSACTION_COST);
  }

  /**
   * Rationale of the one row that records a day the run could not value completely. It is a key of its own rather than
   * the message of the valuation, because the trail translates a rationale on the client and a server side message
   * carries arguments the client cannot fill; the names go into the details instead.
   */
  private static final String RATIONALE_DAY_NOT_PRICED = "REPLAY_DAY_NOT_PRICED";

  /** Rationale of a rebalancing that was refused for a reason other than being unconfigured. */
  private static final String RATIONALE_REBALANCE_UNAVAILABLE = "REPLAY_REBALANCE_UNAVAILABLE";

  /** Rationale of the one row that names what the initial purchase never managed to buy. */
  private static final String RATIONALE_PURCHASE_INCOMPLETE = "REPLAY_PURCHASE_INCOMPLETE";

  /**
   * Rationale of the one row that says the hierarchy names no instrument at all. Such a run walks every day and
   * completes without a single order, which is indistinguishable from a run that was never started unless it says so:
   * the rebalancing only ever executes security level lines, so a hierarchy of empty buckets can refuse nothing and
   * would otherwise leave a trail of two markers and nothing between them.
   */
  private static final String RATIONALE_NO_INSTRUMENTS = "REPLAY_NO_INSTRUMENTS";

  /** Details of a fill the settling account could only pay a part of, so the order was cut down to it. */
  private static final String RATIONALE_ORDER_REDUCED = "REPLAY_ORDER_REDUCED";

  /**
   * Trading days the initial purchase may take. An order decided at a close is filled at the next one, so the phase
   * legitimately spans more than a single day; a line that cannot be funded at all must not hold it open for the rest
   * of the run.
   */
  private static final int MAX_INITIAL_PURCHASE_DAYS = 5;

  /** Rationale of the plan row of a trading day that completes the purchases of the preceding checkpoint. */
  private static final String RATIONALE_REBALANCE_FOLLOW_UP = "REPLAY_REBALANCE_FOLLOW_UP";

  /**
   * Trading days after a checkpoint on which its unpaid purchases are completed. Money a sale of the checkpoint
   * released is spendable from the following day, so one day usually suffices; the bound keeps a purchase that cannot
   * be funded at all from being retried until the next checkpoint.
   */
  private static final int MAX_REBALANCE_FOLLOW_UP_DAYS = 5;

  /** Follow-up days that are tried without a fill, because the proceeds of the checkpoint's sales may still settle. */
  private static final int SETTLEMENT_FOLLOW_UP_DAYS = 2;

  @Autowired
  private AlgoSimulationResultJpaRepository results;
  @Autowired
  private AlgoEventLogJpaRepository events;
  @Autowired
  private AlgoReplayRepository replayRepository;
  @Autowired
  private AlgoExecutionStateJpaRepository executionStates;
  @Autowired
  private AlgoTopJpaRepository algoTops;
  @Autowired
  private AlgoTopReadinessService readinessService;
  @Autowired
  private AlgoAssetclassJpaRepository algoBuckets;
  @Autowired
  private AlgoSecurityJpaRepository algoMembers;
  @Autowired
  private AlgoTradingRepository data;
  @Autowired
  private AlgoMeanReversionScopeEvaluator evaluator;
  @Autowired
  private AlgoMeanReversionSimulationAdapter simulationAdapter;
  @Autowired
  private AlgoRebalancingService rebalancing;
  @Autowired
  private AlgoHistoricalValuationService valuation;

  @Autowired
  private AlgoReplayCustodyService custodyService;
  @Autowired
  private AlgoReplayCalendar calendar;
  @Autowired
  private AlgoReplayRunRegistry registry;
  @Autowired
  private AlgoReplayBooking booking;
  @Autowired
  private AlgoReplayLiquidation liquidation;
  @Autowired
  private SimulationSourceRepository source;
  @Autowired
  private TransactionCostEvalExEstimator costEstimator;
  @Autowired
  private HoldSecurityaccountSecurityJpaRepository securityHoldings;
  @Autowired
  private HoldCashaccountBalanceJpaRepository cashHoldings;
  @Autowired
  private HoldCashaccountDepositJpaRepository deposits;
  @Autowired
  private EntityLimitService entityLimitService;
  @Autowired
  private GlobalparametersService globalparametersService;
  @Autowired
  private AlgoHierarchyViewService hierarchyView;
  @Autowired
  private JsonMapper jsonMapper;
  @Autowired
  private AlgoReplayIncomeService incomeService;
  @Autowired
  private AlgoReplayInputs replayInputs;
  @Autowired
  private AlgoReplayStandingOrderService replayStandingOrders;
  @Autowired
  private TaxEvalExEstimator taxEstimator;
  @jakarta.persistence.PersistenceContext
  private jakarta.persistence.EntityManager entityManager;
  @Autowired
  private TransactionTemplate transactionTemplate;
  @Autowired
  @Qualifier(AlgoReplayConfig.EXECUTOR)
  private TaskExecutor executor;

  // -----------------------------------------------------------------------------------------------------------------
  // Run contract
  // -----------------------------------------------------------------------------------------------------------------

  /**
   * Accepts a replay and hands it to a worker. The run row is committed before the worker starts, so a client that
   * polls immediately already sees a running job rather than nothing.
   *
   * @param idSimTenant the simulation environment to replay
   * @param endDate     last day to evaluate
   * @return the recorded run, in state {@link AlgoSimulationRunStatus#RUNNING}
   */
  public AlgoSimulationResult submit(Integer idSimTenant, LocalDate endDate) {
    var request = new grafioschtrader.algo.SimulationRunRequestDTO();
    request.setEndDate(endDate);
    return submit(idSimTenant, request);
  }

  public AlgoSimulationResult submit(Integer idSimTenant, grafioschtrader.algo.SimulationRunRequestDTO request) {
    User user = currentUser();
    // The environment is claimed before the run is recorded. Recording it first would publish a run that is not yet
    // reserved, and a reader arriving in that window cannot tell it from a run whose worker has died - it would
    // reconcile a replay that is about to start into INTERRUPTED, which the worker then silently overwrites.
    if (!registry.reserve(idSimTenant)) {
      throw invalid("gt.simulation.run.already.running");
    }
    AlgoSimulationResult run;
    try {
      run = transactionTemplate.execute(_ -> prepare(idSimTenant, request, user));
    } catch (RuntimeException e) {
      registry.release(idSimTenant);
      throw e;
    }
    try {
      executor.execute(() -> execute(idSimTenant, run.getIdSimulationResult(), user));
    } catch (RuntimeException e) {
      // The row has to leave RUNNING before the reservation drops: a reader in between would see a running replay
      // with no worker and reconcile it, competing with the failure that is being recorded here.
      transactionTemplate.executeWithoutResult(_ -> fail(run.getIdSimulationResult(), "gt.simulation.run.busy"));
      registry.release(idSimTenant);
      throw invalid("gt.simulation.run.busy");
    }
    return run;
  }

  /**
   * @param idSimTenant the simulation environment
   * @return its run, or empty when it has never been replayed
   */
  public Optional<AlgoSimulationResult> status(Integer idSimTenant) {
    requireOwnedSimulation(idSimTenant, currentUser());
    return results.findByIdTenant(idSimTenant).map(this::reconcile);
  }

  /**
   * @param idSimTenant the simulation environment
   * @return what its latest run was based on, or empty when it has never been replayed
   */
  public Optional<SimulationRunSettingsDto> settings(Integer idSimTenant) {
    requireOwnedSimulation(idSimTenant, currentUser());
    return results.findByIdTenant(idSimTenant)
        .map(run -> new SimulationRunSettingsDto(
            run.getHierarchySnapshot() == null ? null : jsonMapper.readTree(run.getHierarchySnapshot()),
            run.getInputAssumptionsJson() == null ? null
                : AlgoReplayInputs.read(run.getInputAssumptionsJson()).allocation(),
            run.getStartedAt()));
  }

  /**
   * @param idSimTenant the simulation environment
   * @param page        zero based page index
   * @param size        page size
   * @return one page of the audit trail, newest day first
   */
  public Page<AlgoEventLog> events(Integer idSimTenant, int page, int size) {
    requireOwnedSimulation(idSimTenant, currentUser());
    AlgoSimulationResult run = results.findByIdTenant(idSimTenant).orElseThrow(() -> invalid("gt.simulation.run.none"));
    return events.findByIdSimulationResultOrderByEventDateDescIdAlgoEventDesc(run.getIdSimulationResult(),
        PageRequest.of(page, size));
  }

  /**
   * Asks a running replay to stop after the day it is evaluating. The fills booked so far stay in the environment and
   * the run is labelled cancelled, without metrics.
   *
   * @param idSimTenant the simulation environment
   */
  public void cancel(Integer idSimTenant) {
    requireOwnedSimulation(idSimTenant, currentUser());
    AlgoSimulationResult run = results.findByIdTenant(idSimTenant).orElseThrow(() -> invalid("gt.simulation.run.none"));
    if (!registry.cancel(run.getIdTenant())) {
      throw invalid("gt.simulation.run.none");
    }
  }

  /**
   * A run marked as running whose worker no longer exists describes progress that will never advance again - the server
   * was stopped, or the worker died. Nothing resumes such a run: the environment has to be replayed once more, which
   * restores its opening state first.
   *
   * <p>
   * This is checked whenever the run is looked at rather than once at startup. A startup sweep would query the table
   * before anything has asked for it, and it would not notice a worker that disappeared later, which is the same
   * situation from the reader's point of view.
   * </p>
   */
  private AlgoSimulationResult reconcile(AlgoSimulationResult run) {
    if (run == null || run.getStatus() != AlgoSimulationRunStatus.RUNNING || registry.isReserved(run.getIdTenant())) {
      return run;
    }
    run.setStatus(AlgoSimulationRunStatus.INTERRUPTED);
    run.setFinishedAt(LocalDateTime.now());
    return transactionTemplate.execute(_ -> results.save(run));
  }

  // -----------------------------------------------------------------------------------------------------------------
  // Submission
  // -----------------------------------------------------------------------------------------------------------------

  /**
   * Validates the request and records the run. Package visible so that a test can create and execute a run
   * synchronously; an asynchronous submit cannot be asserted on from inside a rolled back test transaction.
   */
  AlgoSimulationResult prepare(Integer idSimTenant, LocalDate endDate, User user) {
    var request = new grafioschtrader.algo.SimulationRunRequestDTO();
    request.setEndDate(endDate);
    return prepare(idSimTenant, request, user);
  }

  AlgoSimulationResult prepare(Integer idSimTenant, grafioschtrader.algo.SimulationRunRequestDTO request, User user) {
    LocalDate endDate = request.getEndDate();
    Tenant tenant = requireOwnedSimulation(idSimTenant, user);
    // Home before environment, the order every lifecycle operation uses, so that creating, replaying and deleting an
    // environment can never wait on each other in a cycle. The write path of a transaction and the replay itself take
    // the environment lock alone and never ask for home, so they cannot close one either.
    data.lockTenant(user.getActualIdTenant());
    data.lockTenant(idSimTenant);
    // Ownership was read unlocked a moment ago; re-read it now that nothing else can change it.
    tenant = requireOwnedSimulation(idSimTenant, user);
    LocalDate openingDate = tenant.getSimulationStartDate();
    if (openingDate == null || tenant.getSimulationInitializationMode() == null) {
      throw new DataViolationException(AlgoHistoricalValuationService.FIELD_SIMULATION_START_DATE,
          "gt.simulation.run.no.opening", null);
    }
    AlgoHistoricalValuationService.validateDate(endDate, "end.date");
    if (!endDate.isAfter(openingDate)) {
      throw new DataViolationException("end.date", "gt.simulation.run.date.invalid", new Object[] { openingDate });
    }
    AlgoTop algoTop = algoTops.findById(tenant.getIdAlgoTop())
        .orElseThrow(() -> new DataViolationException("id.algo.top", "simulation.algotop.not.found", null));
    // The strategy is shared with the main tenant and may have been edited since the environment was created.
    readinessService.requireReadyForReplay(algoTop, localeOf(user));
    List<LocalDate> runDates = calendar.runDates(openingDate, endDate);
    int maxRunTradingDays = globalparametersService.getSimulationMaxRunTradingDays();
    if (runDates.size() > maxRunTradingDays) {
      throw new DataViolationException("end.date", "gt.simulation.run.too.long", new Object[] { maxRunTradingDays });
    }
    AlgoSimulationResult run = results.findByIdTenant(idSimTenant).map(this::reconcile)
        .orElseGet(AlgoSimulationResult::new);
    if (run.getStatus() == AlgoSimulationRunStatus.RUNNING) {
      throw invalid("gt.simulation.run.already.running");
    }
    run.setIdTenant(idSimTenant);
    run.setIdAlgoTop(algoTop.getId());
    run.setOpeningDate(openingDate);
    run.setEndDate(endDate);
    run.setStatus(AlgoSimulationRunStatus.RUNNING);
    run.setStartedAt(LocalDateTime.now());
    run.setFinishedAt(null);
    run.setTradingDaysTotal(runDates.size());
    run.setTradingDaysDone(0);
    run.setHierarchySnapshot(snapshotHierarchy(algoTop));
    run.setConventions(conventions(AlgoReplayFees.anyModelActive(source.securityaccounts(idSimTenant))));
    run.setDividendPaymentDelayDays(globalparametersService.getSimulationDividendPaymentDelayDays());
    run.setApplyTaxModels(request.isApplyTaxModels());
    run.setGenerateBondCoupons(request.isGenerateBondCoupons());
    if (request.isApplyTaxModels())
      run.setConventions(run.getConventions().replace("REPLAY_GROSS_DIVIDENDS", "REPLAY_NET_INCOME"));
    if (request.isGenerateBondCoupons())
      run.setConventions(run.getConventions() + " REPLAY_GENERATED_COUPONS");
    var capturedInputs = replayInputs.capture(request.isApplyTaxModels(), request.isGenerateBondCoupons(),
        run.getDividendPaymentDelayDays(), idSimTenant, replaySecurities(tenant, algoTop).values(),
        source.securityaccounts(idSimTenant), openingDate, endDate);
    var effectiveAllocation = AlgoReplayAllocation.capture(algoTop,
        algoBuckets.findByIdTenantAndIdAlgoAssetclassParent(algoTop.getIdTenant(), algoTop.getId()),
        bucket -> algoMembers.findByIdAlgoSecurityParentAndIdTenant(bucket.getId(), algoTop.getIdTenant()),
        security -> capturedInputs.excluded(security.getId()));
    AlgoReplayInputs.Snapshot withFees;
    try {
      withFees = AlgoReplayCustodyService.capture(capturedInputs.withAllocation(effectiveAllocation),
          source.securityaccounts(idSimTenant), source.cashaccounts(idSimTenant), request.getCustodyOpeningYaml(),
          openingDate, endDate);
    } catch (IllegalArgumentException e) {
      throw new DataViolationException("custody.opening.yaml", "gt.simulation.custody.invalid",
          new Object[] { e.getMessage() });
    }
    run.setInputAssumptionsJson(AlgoReplayInputs.write(withFees));
    run.setConventions(run.getConventions() + " " + AlgoReplayFx.convention(withFees));
    run.setConventions(run.getConventions()
        + (withFees.custodyOpening().isEmpty() ? " REPLAY_CUSTODY_UNMODELLED" : " REPLAY_CUSTODY_MODELLED"));
    run.setTaxIncomeSummaryJson(null);
    run.setPaidDividends(null);
    run.setFxMarkupPaid(null);
    run.setFxUncoveredConversions(null);
    run.setDividendReceivables(null);
    run.setTotalReturn(null);
    run.setAnnualizedReturn(null);
    run.setMaxDrawdown(null);
    run.setSharpeRatio(null);
    run.setTotalTrades(null);
    run.setWinningTrades(null);
    run.setLosingTrades(null);
    run.setFailureMessage(null);
    return results.save(run);
  }

  private Map<Integer, Security> replaySecurities(Tenant tenant, AlgoTop algoTop) {
    Map<Integer, Security> securities = new TreeMap<>();
    source.transactions(tenant.getId(), tenant.getSimulationStartDate().plusDays(1)).forEach(transaction -> {
      if (transaction.getSecurity() != null)
        securities.put(transaction.getSecurity().getId(), transaction.getSecurity());
    });
    Integer owner = tenant.getIdParentTenant();
    algoBuckets.findByIdTenantAndIdAlgoAssetclassParent(owner, algoTop.getId())
        .forEach(bucket -> algoMembers.findByIdAlgoSecurityParentAndIdTenant(bucket.getId(), owner).forEach(member -> {
          if (member.getSecurity() != null)
            securities.put(member.getSecurity().getId(), member.getSecurity());
        }));
    return securities;
  }

  /**
   * Freezes the hierarchy in the shape the hierarchy view serves, serialized by the same mapper as a REST response, so
   * the run's settings tab can build the identical tree from it. The strategies stay editable while the result exists,
   * so without this copy a later edit would silently redefine what a recorded run was calculated from. It carries
   * names, weights, class overrides and every strategy with its parameters on every level.
   */
  private String snapshotHierarchy(AlgoTop algoTop) {
    AlgoHierarchyDto hierarchy = hierarchyView.getHierarchy(algoTop.getIdTenant(), algoTop.getId());
    Map<String, Object> frozen = new LinkedHashMap<>();
    frozen.put("algoTop", hierarchy.algoTop());
    frozen.put("algoAssetclassList", hierarchy.algoAssetclassList());
    return jsonMapper.writeValueAsString(frozen);
  }

  // -----------------------------------------------------------------------------------------------------------------
  // Execution
  // -----------------------------------------------------------------------------------------------------------------

  /**
   * Executes one replay on a worker thread. Package visible so that a test can run it synchronously, which is the only
   * way to assert on the fills a run produced.
   *
   * @param idSimTenant the environment being replayed, which the worker marks its thread with so that its own writes
   *                    are not refused by the guards that keep interactive requests out of an active environment
   * @param idRun       the run to execute
   * @param user        the authenticated owner, whose security context the worker adopts; the transaction write path
   *                    and the simulation fill adapter both read it
   */
  void execute(Integer idSimTenant, Integer idRun, User user) {
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(user));
    registry.beginWorker(idSimTenant);
    // Every replay day reads the environment's ledger several times; the cache serves them from memory and fetches only
    // the fills booked since the previous read.
    try (SimulationLedgerCache.Scope _ = SimulationLedgerCache.open(idSimTenant)) {
      replay(idRun);
    } catch (Exception e) {
      log.error("Historical replay {} failed", idRun, e);
      transactionTemplate.executeWithoutResult(_ -> fail(idRun, message(e)));
    } finally {
      registry.endWorker();
      SecurityContextHolder.clearContext();
      // Last, and only now: the final state of the run is committed by replay() or by the failure above, so the
      // environment becomes available again exactly when no worker can write to it any more.
      registry.release(idSimTenant);
    }
  }

  private void replay(Integer idRun) {
    AlgoSimulationResult run = results.findById(idRun).orElseThrow();
    Integer idTenant = run.getIdTenant();
    Tenant tenant = transactionTemplate.execute(_ -> restoreOpeningState(idTenant));
    AlgoTop algoTop = algoTops.findById(run.getIdAlgoTop()).orElseThrow();
    List<LocalDate> runDates = calendar.runDates(run.getOpeningDate(), run.getEndDate());
    AlgoReplayMarketData market = new AlgoReplayMarketData(data, run.getOpeningDate(), run.getEndDate());
    // The opening valuation answers which of the two openings this is, so it is taken before the state exists.
    AlgoHistoricalValuationService.Snapshot opening = valuation.value(idTenant, run.getOpeningDate(), market);
    AlgoReplayState state = openState(run, tenant, algoTop, market, opening.positions().isEmpty());
    state.meanReversionConfigured = Boolean.TRUE.equals(transactionTemplate
        .execute(_ -> evaluator.hasActiveMeanReversion(tenant.getIdParentTenant(), algoTop.getId(), market)));
    state.custody = custodyService.open(state, source.securityaccounts(idTenant), source.cashaccounts(idTenant));
    state.costs.setCustody(state.custody);
    market.setCustodyLiabilities(state.custody::liabilities);
    Map<LocalDate, List<AlgoReplayInputs.CashStandingOrder>> cashOrderSchedule = replayStandingOrders
        .schedule(state.inputs, run.getOpeningDate(), run.getEndDate());
    Set<LocalDate> evaluationDates = Set.copyOf(runDates);
    TreeSet<LocalDate> timeline = new TreeSet<>(runDates);
    timeline.addAll(cashOrderSchedule.keySet());
    timeline.addAll(state.custody.dates());
    try {
      opening = valuation.value(idTenant, run.getOpeningDate(), market);
      state.writeMarker(AlgoEventType.RUN_START, run.getOpeningDate(),
          run.getOpeningDate() + " .. " + run.getEndDate());
      var liquidations = liquidation.schedule(state);
      timeline.addAll(liquidations.keySet());
      LocalDate liquidationComplete = liquidations.isEmpty() ? null : liquidations.lastKey();
      if (state.inputs.allocation() != null
          && !state.inputs.allocation().originalMembers().equals(state.inputs.allocation().members()))
        state.write(AlgoEventType.ALLOCATION_PLAN, run.getOpeningDate(), null, null, null, null, null,
            tenant.getCurrency(), "REPLAY_ALLOCATION_REDISTRIBUTED");
      if (state.securities.isEmpty()) {
        state.write(AlgoEventType.UNAVAILABLE, run.getOpeningDate(), null, null, null, null, null, null,
            RATIONALE_NO_INSTRUMENTS);
      }
      var openingSnapshot = opening;
      state.observeEquity(run.getOpeningDate(), () -> openingSnapshot);
      state.decisionEquity.put(run.getOpeningDate(), opening);
      for (LocalDate date : timeline) {
        if (registry.isCancelled(idTenant)) {
          finish(state, AlgoSimulationRunStatus.CANCELLED, "gt.simulation.run.cancelled");
          return;
        }
        replayStandingOrders.execute(state, date, cashOrderSchedule.getOrDefault(date, List.of()));
        state.custody.process(date, true);
        if (liquidationComplete != null && !date.isAfter(liquidationComplete)) {
          processDividends(state, date);
          if (evaluationDates.contains(date)) {
            processTerminalEvents(state, date);
          }
          state.observeEquity(date, () -> valuation.value(state.idTenant(), date, state.market));
          liquidation.execute(state, date, liquidations.getOrDefault(date, List.of()));
          if (date.equals(liquidationComplete)) {
            var afterLiquidation = valuation.value(state.idTenant(), date, state.market);
            afterLiquidation.requireAvailable();
            state.decisionEquity.put(date, afterLiquidation);
            state.initialPurchaseRequired = afterLiquidation.positions().isEmpty();
          }
          if (date.isBefore(liquidationComplete)) {
            state.custody.process(date, false);
            if (evaluationDates.contains(date)) {
              state.done++;
              reportProgress(state, date);
            }
            continue;
          }
        }
        if (!evaluationDates.contains(date)) {
          // Income moves its own booking to a trading day, a redemption or terminal close does not and would be refused
          // as transaction.time.notrading. The terminal schedule keeps such an expiry pending for the next trading day.
          processDividends(state, date);
          state.custody.process(date, false);
          // Keep the existing trading-day sampling convention of the performance metrics.
          continue;
        }
        replayDay(state, date);
        state.custody.process(date, false);
        state.done++;
        reportProgress(state, date);
      }
      processDividends(state, run.getEndDate());
      LocalDate lastRunDate = runDates.isEmpty() ? null : runDates.getLast();
      if (lastRunDate == null || run.getEndDate().isAfter(lastRunDate)) {
        processTerminalEvents(state, run.getEndDate());
      }
      finish(state, AlgoSimulationRunStatus.COMPLETED, null);
    } catch (RuntimeException failure) {
      LocalDate lastDate = state.lastValuationDate();
      try {
        transactionTemplate.executeWithoutResult(_ -> progress(state, lastDate));
      } catch (RuntimeException diagnosticFailure) {
        failure.addSuppressed(diagnosticFailure);
      }
      throw failure;
    }
  }

  /**
   * Puts the environment back into the state its opening definition describes: replay-generated and user-entered
   * transactions are removed and the holdings are rebuilt from the remaining opening ledger, exactly as the creation of
   * the environment builds them.
   */
  private Tenant restoreOpeningState(Integer idTenant) {
    // A synchronous caller may still manage holdings from the previous run. The native rebuild must not merge into
    // those deleted instances. Flush the recorded run first, then load the opening ledger in a fresh context.
    entityManager.flush();
    entityManager.clear();
    replayRepository.clearGeneratedTransactionReferences(idTenant);
    replayRepository.deleteSecurityTransfers(idTenant);
    replayRepository.deleteSecurityActionApplications(idTenant);
    replayRepository.deleteGeneratedTransactions(idTenant);
    replayRepository.deleteEvents(idTenant);
    executionStates.deleteByIdTenant(idTenant);
    securityHoldings.createSecurityHoldingsEntireByTenant(idTenant);
    cashHoldings.createCashaccountBalanceEntireByTenant(idTenant);
    deposits.createCashaccountDepositTimeFrameByTenant(idTenant);
    return data.lockTenant(idTenant);
  }

  /**
   * One day of the replay, as a sequence of independent units of work rather than as one transaction.
   *
   * <p>
   * Every step the day is made of already carries {@code @Transactional} - the valuation and the rebalancing plan read
   * only, the write path of the portfolio and the fill adapter write - so each opens the transaction it needs and ends
   * it, exactly as it does for one request of a user. Two things forbid holding one transaction around the whole day
   * instead.
   * </p>
   *
   * <p>
   * A refusal is an ordinary outcome here: the allocation cannot be compared, the account is underfunded, the period is
   * closed. Raised out of a method that joined an enclosing transaction, it marks that transaction rollback-only, and
   * catching it is then not enough - the day would go on writing its trail and fail at commit with
   * {@code UnexpectedRollbackException}, ending the whole run over one refused order.
   * </p>
   *
   * <p>
   * Nor may the order be given a transaction of its own while the day holds one. Writing a row of the trail leaves the
   * day holding a shared lock on the tenant row, which the foreign key of {@code algo_event_log} makes InnoDB take, and
   * the strategy fill adapter opens with a {@code SELECT ... FOR UPDATE} on that same row. The order would wait for a
   * lock the day only releases once the order returns. The database sees no cycle to break there, so it is not a
   * deadlock it reports: every order waits out the lock timeout, the run stops advancing, and it does not answer a
   * cancellation either, which is only asked between two days. Ordinary transaction writes and their limit checks take
   * no tenant lock; concurrent requests may slightly exceed the transaction cap.
   * </p>
   */
  private void replayDay(AlgoReplayState state, LocalDate date) {
    processDividends(state, date);
    processTerminalEvents(state, date);
    state.observeEquity(date, () -> valuation.value(state.idTenant(), date, state.market));
    if (state.initialPurchaseRequired && !state.initialPurchaseSettled) {
      purchaseInitial(state, date);
    } else {
      rebalance(state, date);
    }
    applyProposals(state, date);
  }

  /**
   * At completion, keeps the closing equity of one day and says so when it is incomplete. A position or a currency
   * without a usable closing price is dropped from the valuation rather than failing it, so the equity of such a day is
   * missing whatever could not be valued. The point is kept for the record but marked as no observation, and the day is
   * named once in the trail: one row for the day and not one per instrument, because a stretch without prices would
   * otherwise consume the event budget of the tenant and truncate the rest of the trail without saying so.
   *
   * @param state    the running replay
   * @param date     the day that was valued
   * @param snapshot what the valuation of that day produced
   */
  private void recordEquity(AlgoReplayState state, LocalDate date, AlgoHistoricalValuationService.Snapshot snapshot) {
    boolean priced = snapshot.errors().isEmpty();
    double externalFlow = priced ? state.consumeExternalCashFlow(date, snapshot.fx()) : 0;
    state.equity.add(new AlgoReplayMetrics.EquityPoint(date, snapshot.equity(), priced, externalFlow));
    if (!priced) {
      state.write(AlgoEventType.UNAVAILABLE, date, null, null, null, null, null, null, RATIONALE_DAY_NOT_PRICED,
          String.join(", ", snapshot.errors()));
    }
  }

  /**
   * The one-time purchase of an environment that opened without positions. It is sized by the rebalancing rules - the
   * same targets, the same tolerance - but it is not a rebalancing: it is not gated by the checkpoint interval, it
   * retries the lines it could not fill, and it is what starts the interval once it is done.
   *
   * <p>
   * A tactical bucket is not bought in here. Its positions are opened by the entry strategy that makes it tactical, so
   * a cash-only environment whose buckets are all tactical performs no purchase at all and waits for its strategies,
   * which is correct rather than a missing buy.
   * </p>
   */
  private void purchaseInitial(AlgoReplayState state, LocalDate date) {
    if (!state.rebalancingConfigured) {
      state.initialPurchaseSettled = true;
      return;
    }
    RebalancingPlan plan;
    try {
      // The purchase is not a checkpoint, so the plan is asked to treat this very day as the last redeployment.
      plan = rebalancing.initialPlan(state.idTenant(), state.algoTop, date, state.locale, state.market);
    } catch (DataViolationException e) {
      state.write(AlgoEventType.UNAVAILABLE, date, null, null, null, null, null, null, RATIONALE_REBALANCE_UNAVAILABLE,
          booking.describe(e, state.locale));
      settleInitialPurchase(state, date, List.of());
      return;
    }
    List<RebalancingPlan.Line> due = bookingOrder(plan).stream().filter(line -> tradableOn(state, line, date)).toList();
    if (due.isEmpty() || state.initialPurchaseAttempts >= MAX_INITIAL_PURCHASE_DAYS) {
      settleInitialPurchase(state, date, due.isEmpty() ? List.of() : due);
      return;
    }
    state.initialPurchaseAttempts++;
    state.write(AlgoEventType.ALLOCATION_PLAN, date, null, null, null, null, null, plan.currency(),
        AlgoRebalancingTrigger.INITIAL.name());
    for (RebalancingPlan.Line line : due) {
      bookLine(state, date, line, AlgoEventType.ALLOCATION_FILL);
    }
  }

  /**
   * Ends the purchase phase and starts the checkpoint interval at the day it completed, so the first rebalancing falls
   * one interval after the portfolio was established and not one interval after the environment was opened.
   *
   * @param state    the running replay
   * @param date     the day the phase ended on
   * @param unfilled lines still outside their tolerance after the allowed attempts, named in one row so the reader
   *                 knows the portfolio never reached its targets
   */
  private void settleInitialPurchase(AlgoReplayState state, LocalDate date, List<RebalancingPlan.Line> unfilled) {
    state.initialPurchaseSettled = true;
    state.lastRebalancedOn = date;
    if (!unfilled.isEmpty()) {
      state.write(AlgoEventType.UNAVAILABLE, date, null, null, null, null, null, null, RATIONALE_PURCHASE_INCOMPLETE,
          unfilled.stream().map(RebalancingPlan.Line::label).collect(Collectors.joining(", ")));
    }
  }

  private void rebalance(AlgoReplayState state, LocalDate date) {
    // A hierarchy without a rebalancing strategy is a normal configuration; only its instruments are traded. That
    // case is decided before the call, so everything the plan still refuses is a real impediment and is reported.
    if (!state.rebalancingConfigured) {
      return;
    }
    RebalancingPlan plan;
    try {
      // The cheap half of the question first: building a plan values every position and reads a quote per instrument
      // and per currency pair, which a day between two checkpoints has no use for.
      if (!rebalancing.isCheckpointDue(state.idTenant(), state.algoTop, date, state.lastRebalancedOn)) {
        if (!state.rebalanceFollowUpClasses.isEmpty()) {
          completeCheckpoint(state, date);
        }
        return;
      }
      plan = rebalancing.plan(state.idTenant(), state.algoTop, date, state.locale, state.lastRebalancedOn,
          state.market);
    } catch (DataViolationException e) {
      // A configuration that cannot be compared is named once per interval rather than once per day, which is why the
      // interval advances here too.
      state.lastRebalancedOn = date;
      state.write(AlgoEventType.UNAVAILABLE, date, null, null, null, null, null, null, RATIONALE_REBALANCE_UNAVAILABLE,
          booking.describe(e, state.locale));
      return;
    }
    // The checkpoint was evaluated, so the next one is one interval away whether or not anything was traded.
    // Advancing only on a traded day would ask a checkpoint that found nothing again on the following day.
    state.lastRebalancedOn = date;
    state.rebalanceFollowUpClasses.clear();
    state.rebalanceFollowUpAttempts = 0;
    if (plan.trigger() == AlgoRebalancingTrigger.NONE) {
      recordClassResiduals(state, date, plan, Map.of(), null);
      return;
    }
    state.write(AlgoEventType.REBALANCE_PLAN, date, null, null, null, null, null, plan.currency(),
        plan.trigger().name());
    Map<Integer, Double> executed = new HashMap<>();
    for (RebalancingPlan.Line line : bookingOrder(plan)) {
      executed.merge(line.idParentNode(), bookLine(state, date, line, AlgoEventType.REBALANCE_FILL), Double::sum);
    }
    state.rebalanceFollowUpClasses.addAll(recordClassResiduals(state, date, plan, executed, null));
  }

  /**
   * Completes on a later trading day the purchases a checkpoint could not pay for. A rebalancing books its sales and
   * purchases on the same day, but the proceeds of a sale are spendable only from the following day, so a purchase that
   * depends on them is cut down to the cash already there. Without this step the remainder would wait a whole interval
   * for the next checkpoint.
   *
   * <p>
   * Only purchases of the classes the checkpoint left short are booked. The plan sizes those classes towards their
   * target without the tolerance, because the decision was already taken; sales are not repeated, since they never
   * depend on money arriving. The interval is not moved: the checkpoint remains the day the portfolio was compared.
   * </p>
   *
   * @param state the running replay
   * @param date  the day of the follow-up decision
   */
  private void completeCheckpoint(AlgoReplayState state, LocalDate date) {
    RebalancingPlan plan;
    try {
      plan = rebalancing.followUpPlan(state.idTenant(), state.algoTop, date, state.locale, state.lastRebalancedOn,
          state.market, Set.copyOf(state.rebalanceFollowUpClasses));
    } catch (DataViolationException e) {
      state.rebalanceFollowUpClasses.clear();
      return;
    }
    Set<Integer> classes = Set.copyOf(state.rebalanceFollowUpClasses);
    List<RebalancingPlan.Line> due = bookingOrder(plan).stream()
        .filter(
            line -> classes.contains(line.idParentNode()) && !line.reducesExposure() && line.recommendedUnits() != 0)
        .toList();
    state.rebalanceFollowUpClasses.clear();
    if (due.isEmpty()) {
      return;
    }
    state.rebalanceFollowUpAttempts++;
    state.write(AlgoEventType.REBALANCE_PLAN, date, null, null, null, null, null, plan.currency(),
        RATIONALE_REBALANCE_FOLLOW_UP);
    Map<Integer, Double> executed = new HashMap<>();
    for (RebalancingPlan.Line line : due) {
      executed.merge(line.idParentNode(), bookLine(state, date, line, AlgoEventType.REBALANCE_FILL), Double::sum);
    }
    Set<Integer> stillShort = recordClassResiduals(state, date, plan, executed, classes);
    // A sale of the checkpoint fills at the next close and is spendable the day after, so its proceeds can arrive on
    // the second follow-up day at the earliest. A later day that still buys nothing has no money to wait for.
    boolean progressed = executed.values().stream().anyMatch(amount -> Math.abs(amount) > 1e-8);
    if ((progressed || state.rebalanceFollowUpAttempts < SETTLEMENT_FOLLOW_UP_DAYS)
        && state.rebalanceFollowUpAttempts < MAX_REBALANCE_FOLLOW_UP_DAYS) {
      state.rebalanceFollowUpClasses.addAll(stillShort);
    }
  }

  /**
   * Amounts use decision-day exposure so rounding and funding shortfalls can be compared with the requested plan.
   *
   * @param state    the running replay
   * @param date     the day of the decision
   * @param plan     the plan that was executed
   * @param executed exposure change actually booked, by AlgoAssetclass id
   * @param only     the classes to report, or null for every class of the plan
   * @return the classes whose purchase was left short by the execution alone, which a later day can complete
   */
  private Set<Integer> recordClassResiduals(AlgoReplayState state, LocalDate date, RebalancingPlan plan,
      Map<Integer, Double> executed, Set<Integer> only) {
    Set<Integer> purchaseShortfalls = new TreeSet<>();
    for (var adjustment : plan.classAdjustments()) {
      if (only != null && !only.contains(adjustment.idNode())) {
        continue;
      }
      double filled = executed.getOrDefault(adjustment.idNode(), 0.0);
      double residual = adjustment.requestedAdjustment() - filled;
      if (residual > 1e-8 && adjustment.requestedAdjustment() > 0 && adjustment.limitingReason() == null) {
        purchaseShortfalls.add(adjustment.idNode());
      }
      if (Math.abs(residual) > 1e-8) {
        state.write(AlgoEventType.UNAVAILABLE, date, null, null, null, null, residual, plan.currency(),
            "REBALANCE_RESIDUAL",
            String.format(Locale.ROOT, "class=%d requested=%.2f planned=%.2f executed=%.2f residual=%.2f reason=%s",
                adjustment.idNode(), adjustment.requestedAdjustment(), adjustment.plannedAdjustment(), filled, residual,
                adjustment.limitingReason() == null ? "REBALANCE_EXECUTION_SHORTFALL" : adjustment.limitingReason()));
      }
    }
    return purchaseShortfalls;
  }

  /**
   * The executable lines of a plan in the order they have to be booked: reductions first, the same order the live
   * notification raises them in.
   *
   * <p>
   * It buys less than it looks like it does. The overdraft guard of the write path takes the balance <em>before</em>
   * the day of the order, so a sale booked on the same day as a purchase does not raise what that purchase may spend;
   * it only stops the account from being read as lower. Money a sale released is available from the following day, and
   * a purchase that cannot be paid on the day itself is funded out of the other accounts of the environment instead.
   * </p>
   *
   * @param plan the comparison to execute
   * @return the lines to book, sells before buys
   */
  private static List<RebalancingPlan.Line> bookingOrder(RebalancingPlan plan) {
    return plan.executableLines().stream()
        .sorted(Comparator.comparing((RebalancingPlan.Line line) -> !line.reducesExposure())).toList();
  }

  private double bookLine(AlgoReplayState state, LocalDate date, RebalancingPlan.Line line, AlgoEventType fillType) {
    Security security = state.securities.get(line.idSecuritycurrency());
    if (security == null || !tradableOn(state, security, date)) {
      return 0;
    }
    TransactionType type = line.action() == AlgoRecommendationAction.REBALANCE_BUY ? TransactionType.ACCUMULATE
        : TransactionType.REDUCE;
    try {
      Transaction fill = booking.book(state, security, null, Math.abs(line.recommendedUnits()), type, date);
      state.write(fillType, date, null, security.getId(), fill.getUnits(), fill.getQuotation(),
          line.recommendedAmount(), state.tenant.getCurrency(), line.rationale(),
          state.fx.details(fill.getAlgoFillId(), state.lastFillWasReduced ? RATIONALE_ORDER_REDUCED : null));
      return line.exposureChange() * Math.min(1, Math.abs(fill.getUnits() / line.recommendedUnits()));
    } catch (TransactionLimitExceededException | AlgoReplayFx.Failure e) {
      throw e;
    } catch (Exception e) {
      if (e.getMessage() != null && e.getMessage().contains("REPLAY_CUSTODY_FAILED"))
        throw new IllegalArgumentException(e.getMessage(), e);
      state.write(AlgoEventType.UNAVAILABLE, date, null, security.getId(), null, null, null, null,
          AlgoReplayBooking.rationaleOf(e), booking.detailsOf(e, state.locale));
    }
    return 0;
  }

  private void applyProposals(AlgoReplayState state, LocalDate date) {
    if (!state.meanReversionConfigured) {
      return;
    }
    // The evaluation is the one step that brings no transaction of its own, and it reads across several repositories.
    // It is given one so that it keeps working on a single persistence context; it is closed again before the first
    // fill, because a transaction still open here would be the one the fill waits for.
    List<Proposal> proposals = transactionTemplate.execute(_ -> evaluator.evaluate(state.idTenant(),
        state.tenant.getIdParentTenant(), state.algoTop.getId(), date, state.market));
    for (Proposal proposal : proposals) {
      Decision decision = proposal.decision();
      Security security = proposal.scope().security();
      state.includeSecurity(security);
      AlgoEventType type = decision.actionable() ? AlgoEventType.DECISION
          : decision.action() == AlgoMeanReversionDecisionService.Action.UNAVAILABLE ? AlgoEventType.UNAVAILABLE
              : AlgoEventType.BLOCKED;
      state.write(type, date, proposal.scope().strategy().getId(), security.getId(), decision.quantity(),
          decision.price(), proposal.amount(), state.tenant.getCurrency(), decision.rationale());
      if (decision.actionable() && tradableOn(state, security, date)) {
        bookProposalFill(state, date, proposal);
      }
    }
  }

  private void bookProposalFill(AlgoReplayState state, LocalDate date, Proposal proposal) {
    Decision decision = proposal.decision();
    Security security = proposal.scope().security();
    try {
      boolean buy = decision.increasesExposure() ? decision.direction() > 0 : decision.direction() < 0;
      Transaction fill = booking.book(state, security, proposal.scope().strategy().getId(), decision.quantity(),
          buy ? TransactionType.ACCUMULATE : TransactionType.REDUCE, date);
      fill = simulationAdapter.fill(proposal.context(), decision,
          state.run.getIdSimulationResult() + ":" + decision.identity(), fill);
      state.costs.committed(fill);
      state.fx.committed(fill.getAlgoFillId(), state.pendingFx);
      if (state.custody != null)
        state.custody.committed(fill, state.pendingCustodyCredit);
      state.taxes.committed(state.pendingTax, security.getId(), fill.getIdSecurityaccount(),
          fill.getTransactionTime().toLocalDate(), fill.getAlgoFillId());
      state.accounts.remember(security,
          new AlgoReplayAccounts.Booking(fill.getIdSecurityaccount(), fill.getCashaccount()));
      state.roundTrips.add(proposal.scope().strategy().getId(), security.getId(), fill.getTransactionType(),
          fill.getUnits(), fill.getQuotation(), fill.getTransactionCost(), fill.getTaxCost(),
          fill.getAssetInvestmentValue1(), fill.getTransactionDate());
      state.write(AlgoEventType.FILL, fill.getTransactionTime().toLocalDate(), proposal.scope().strategy().getId(),
          security.getId(), fill.getUnits(), fill.getQuotation(), fill.getCashaccountAmount(),
          fill.getCashaccount().getCurrency(), decision.rationale(), state.fx.details(fill.getAlgoFillId(), null));
    } catch (TransactionLimitExceededException | AlgoReplayFx.Failure e) {
      throw e;
    } catch (Exception e) {
      if (e.getMessage() != null && e.getMessage().contains("REPLAY_CUSTODY_FAILED"))
        throw new IllegalArgumentException(e.getMessage(), e);
      state.write(AlgoEventType.UNAVAILABLE, date, proposal.scope().strategy().getId(), security.getId(), null, null,
          null, null, AlgoReplayBooking.rationaleOf(e), booking.detailsOf(e, state.locale));
    }
  }

  // -----------------------------------------------------------------------------------------------------------------
  // Result and audit trail
  // -----------------------------------------------------------------------------------------------------------------

  /** Processes income on every due calendar date before valuation. */
  private void processDividends(AlgoReplayState state, LocalDate date) {
    state.dividends.processThrough(date);
  }

  record PositionHolding(Integer idSecurityaccount, Cashaccount cashaccount, double units) {
  }

  /**
   * Repays matured direct bonds at par and closes other leftover names on or after their captured {@code activeToDate}.
   * Skipped when the snapshot has no life dates, so an old completed run is left as it was.
   */
  void processTerminalEvents(AlgoReplayState state, LocalDate date) {
    List<Integer> due = state.terminalSchedule.due(date);
    if (due.isEmpty()) {
      return;
    }
    // Read before booking, once for the whole day. Each terminal fill affects only its own security, so the other
    // groups stay valid. Reload on the next day so refused closes can retry against the committed ledger.
    Map<Integer, List<Transaction>> ledger = source.transactions(state.idTenant(), date.plusDays(1)).stream()
        .filter(transaction -> transaction.getSecurity() != null)
        .collect(Collectors.groupingBy(transaction -> transaction.getSecurity().getId()));
    for (Integer idSecurity : due) {
      if (processTerminalEvent(state, state.securities.get(idSecurity), date,
          ledger.getOrDefault(idSecurity, List.of()))) {
        state.terminalSchedule.complete(idSecurity);
      }
    }
  }

  /**
   * A completed expiry cannot gain another position: replay orders never fill after the captured life end, and fills on
   * that end date are already in this ledger. A failed close stays pending and rereads its remaining holdings.
   */
  private boolean processTerminalEvent(AlgoReplayState state, Security security, LocalDate date,
      List<Transaction> ledger) {
    if (state.inputs.version() >= 3 && state.inputs.excluded(security.getId()))
      return true;
    var instrument = state.inputs.instruments().get(security.getId());
    boolean catchUp = instrument.activeToDate().isBefore(date);
    List<PositionHolding> held = terminalHoldings(state, security, date, ledger);
    boolean complete = true;
    if (instrument.directBond()) {
      for (PositionHolding holding : held) {
        try {
          Transaction fill = booking.redeem(state, security, instrument, holding.idSecurityaccount(),
              holding.cashaccount(), holding.units(), date);
          state.write(AlgoEventType.MATURITY_REDEMPTION, date, null, security.getId(), fill.getUnits(),
              fill.getQuotation(), fill.getCashaccountAmount(), fill.getCashaccount().getCurrency(),
              catchUp ? "REPLAY_REDEMPTION_CATCH_UP" : "REPLAY_BOND_REDEEM_AT_PAR",
              state.fx.details(fill.getAlgoFillId(), null));
        } catch (TransactionLimitExceededException | AlgoReplayFx.Failure e) {
          throw e;
        } catch (Exception e) {
          throw new IllegalStateException(AlgoReplayBooking.rationaleOf(e), e);
        }
      }
    } else if (!security.isMarginInstrument()) {
      for (PositionHolding holding : held) {
        try {
          Transaction fill = booking.terminalClose(state, security, holding.idSecurityaccount(), holding.cashaccount(),
              holding.units(), date, instrument.activeToDate());
          state.write(AlgoEventType.TERMINAL_CLOSE, date, null, security.getId(), fill.getUnits(), fill.getQuotation(),
              fill.getCashaccountAmount(), fill.getCashaccount().getCurrency(),
              catchUp ? "REPLAY_TERMINAL_CLOSE_CATCH_UP" : "REPLAY_TERMINAL_CLOSE",
              state.fx.details(fill.getAlgoFillId(), null));
        } catch (TransactionLimitExceededException | AlgoReplayFx.Failure e) {
          throw e;
        } catch (Exception e) {
          complete = false;
          state.write(AlgoEventType.UNAVAILABLE, date, null, security.getId(), null, null, null, null,
              AlgoReplayBooking.rationaleOf(e), booking.detailsOf(e, state.locale));
        }
      }
    }
    return complete;
  }

  /**
   * Holdings at the end of the terminal date. Replay fills are persisted when the decision is made and carry their
   * later fill date, so a fill already scheduled exactly on {@code through} must be closed as well. Income entitlement
   * intentionally keeps using its separate strictly-before-date calculation.
   */
  private List<PositionHolding> terminalHoldings(AlgoReplayState state, Security security, LocalDate through,
      List<Transaction> ledger) {
    var instrument = state.inputs.instruments().get(security.getId());
    List<Securitysplit> splits = instrument == null ? List.of()
        : instrument.splits().stream().map(split -> new Securitysplit(security.getId(), split.date(), split.from(),
            split.to(), CreateType.ADD_MODIFIED_USER)).toList();
    return terminalHoldings(ledger, security, through, splits);
  }

  /**
   * Nets positions on their custody account, regardless of which cash account settled each trade. Otherwise a sale paid
   * into a different cash account leaves a fictitious positive position on the purchase's cash account. Each custody
   * position produces one terminal fill, settled in instrument currency when one of its cash accounts allows it.
   * Historical quantities are expressed on the terminal date's split basis.
   */
  static List<PositionHolding> terminalHoldings(List<Transaction> ledger, Security security, LocalDate through,
      List<Securitysplit> splits) {
    Map<Integer, Double> units = new TreeMap<>();
    Map<Integer, Cashaccount> cash = new HashMap<>();
    Map<Integer, List<Securitysplit>> splitMap = Map.of(security.getId(), splits);
    Comparator<Cashaccount> settlementOrder = Comparator
        .comparingInt((Cashaccount account) -> account.getCurrency().equals(security.getCurrency()) ? 0 : 1)
        .thenComparing(Cashaccount::getId);
    for (Transaction transaction : ledger) {
      if (transaction.getSecurity() == null || !transaction.getSecurity().getId().equals(security.getId())
          || transaction.getIdSecurityaccount() == null || transaction.getCashaccount() == null
          || transaction.getTransactionDate().isAfter(through)) {
        continue;
      }
      TransactionType type = transaction.getTransactionType();
      if (type != TransactionType.ACCUMULATE && type != TransactionType.REDUCE) {
        continue;
      }
      double factor = Securitysplit.calcSplitFatorForFromDateAndToDate(security.getId(),
          transaction.getTransactionDate(), through.plusDays(1), splitMap).fromToDateFactor;
      units.merge(transaction.getIdSecurityaccount(),
          transaction.getUnits() * factor * (type == TransactionType.ACCUMULATE ? 1 : -1), Double::sum);
      cash.merge(transaction.getIdSecurityaccount(), transaction.getCashaccount(),
          (first, second) -> settlementOrder.compare(first, second) <= 0 ? first : second);
    }
    List<PositionHolding> result = new ArrayList<>();
    units.forEach((account, quantity) -> {
      if (quantity > 1e-8) {
        result.add(new PositionHolding(account, cash.get(account), quantity));
      }
    });
    return result;
  }

  private boolean tradableOn(AlgoReplayState state, RebalancingPlan.Line line, LocalDate date) {
    Security security = state.securities.get(line.idSecuritycurrency());
    return security != null && tradableOn(state, security, date);
  }

  /**
   * Strategy and rebalancing orders are not placed on or after a captured {@code activeToDate}: that morning already
   * closed or redeemed the name. Before {@code activeFromDate} they wait for a later checkpoint. From the captured
   * {@code tradingEndDate} of a failed issuer on, no order is placed at all.
   */
  private boolean tradableOn(AlgoReplayState state, Security security, LocalDate date) {
    var instrument = state.inputs.instruments().get(security.getId());
    if (instrument != null && (instrument.activeToDate() != null && !date.isBefore(instrument.activeToDate())
        || instrument.tradingStopped(date))) {
      return false;
    }
    return calendar.isMarketFillDate(security, date, instrument == null ? null : instrument.activeFromDate(),
        instrument == null ? null : instrument.activeToDate(), instrument == null ? null : instrument.directBond());
  }

  private void finish(AlgoReplayState state, AlgoSimulationRunStatus status, String failure) {
    var terminal = status == AlgoSimulationRunStatus.COMPLETED ? finishValuations(state) : null;
    boolean terminalPriced = terminal != null && terminal.errors().isEmpty();
    double terminalExternalFlow = terminalPriced ? state.consumeExternalCashFlow(state.run.getEndDate(), terminal.fx())
        : 0;
    transactionTemplate.executeWithoutResult(_ -> {
      AlgoSimulationResult run = results.findById(state.run.getIdSimulationResult()).orElseThrow();
      run.setStatus(status);
      run.setFinishedAt(LocalDateTime.now());
      run.setTradingDaysDone(state.done);
      run.setFailureMessage(failure);
      if (status == AlgoSimulationRunStatus.COMPLETED) {
        var metrics = AlgoReplayMetrics.of(state.equity, state.roundTrips.closedTrades(),
            new AlgoReplayMetrics.EquityPoint(state.run.getEndDate(), terminal.equity(), terminalPriced,
                terminalExternalFlow));
        run.setPaidDividends(state.dividends.paidTotal());
        run.setFxMarkupPaid(state.fx.paid());
        run.setFxUncoveredConversions(state.fx.uncovered());
        Map<String, Double> unpaid = state.dividends.dividendReceivables(state.run.getEndDate());
        run.setDividendReceivables(unpaid.keySet().stream().allMatch(terminal.fx()::containsKey)
            ? unpaid.entrySet().stream().mapToDouble(e -> e.getValue() * terminal.fx().get(e.getKey())).sum()
            : null);
        run.setTotalReturn(metrics.totalReturn());
        run.setAnnualizedReturn(metrics.annualizedReturn());
        run.setMaxDrawdown(metrics.maxDrawdown());
        run.setSharpeRatio(metrics.sharpeRatio());
        run.setTotalTrades(metrics.totalTrades());
        run.setWinningTrades(metrics.winningTrades());
        run.setLosingTrades(metrics.losingTrades());
      }
      run.setTaxIncomeSummaryJson(AlgoReplayInputs
          .write(new grafioschtrader.dto.TaxIncomeSummaryDto(1, state.taxes.warnings(), state.dividends.summary(
              status == AlgoSimulationRunStatus.COMPLETED ? state.run.getEndDate() : state.lastValuationDate()))));
      results.save(run);
    });
    state.writeMarker(AlgoEventType.RUN_END,
        status == AlgoSimulationRunStatus.COMPLETED ? state.run.getEndDate() : state.lastValuationDate(),
        status.name());
  }

  private AlgoHistoricalValuationService.Snapshot finishValuations(AlgoReplayState state) {
    // No more bookings follow. Income accrual can now safely reuse one immutable historical ledger.
    state.dividends.freezeForReporting(state.run.getEndDate());
    var dates = new TreeSet<>(state.valuationDates);
    dates.add(state.run.getEndDate());
    var snapshots = valuation.valueSeries(state.idTenant(), dates, state.market, state.securities);
    for (LocalDate date : state.valuationDates)
      recordEquity(state, date, state.decisionEquity.getOrDefault(date, snapshots.get(date)));
    return snapshots.get(state.run.getEndDate());
  }

  /**
   * Writes the progress of a running replay at most once per {@link #PROGRESS_INTERVAL_NANOS}. Every write reloads and
   * rewrites the whole result row with its large JSON columns and values the income diagnostics, which done for every
   * replayed day cost more database time than any other single statement of the run. The final state is written by
   * {@code finish} and by the failure path regardless.
   */
  private void reportProgress(AlgoReplayState state, LocalDate date) {
    long now = System.nanoTime();
    if (state.progressWrittenAt != 0 && now - state.progressWrittenAt < PROGRESS_INTERVAL_NANOS) {
      return;
    }
    state.progressWrittenAt = now;
    transactionTemplate.executeWithoutResult(_ -> progress(state, date));
  }

  private void progress(AlgoReplayState state, LocalDate date) {
    results.findById(state.run.getIdSimulationResult()).ifPresent(run -> {
      run.setTradingDaysDone(state.done);
      List<grafioschtrader.dto.TaxIncomeSummaryDto.IncomeTotals> income;
      try {
        income = state.dividends.summary(date);
      } catch (RuntimeException failure) {
        log.warn("Cannot value income diagnostics for replay {} on {}", run.getIdSimulationResult(), date, failure);
        income = List.of();
      }
      run.setTaxIncomeSummaryJson(
          AlgoReplayInputs.write(new grafioschtrader.dto.TaxIncomeSummaryDto(1, state.taxes.warnings(), income)));
      results.save(run);
    });
  }

  private void fail(Integer idRun, String failure) {
    results.findById(idRun).ifPresent(run -> {
      if (run.getStatus() == AlgoSimulationRunStatus.RUNNING) {
        run.setStatus(AlgoSimulationRunStatus.RUN_FAILED);
        run.setFinishedAt(LocalDateTime.now());
        run.setFailureMessage(failure);
        results.save(run);
      }
    });
  }

  private static String message(Exception e) {
    String text = e instanceof TransactionLimitExceededException limit ? limit.getMessageKey()
        : e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    return text.length() <= 500 ? text : text.substring(0, 500);
  }

  // -----------------------------------------------------------------------------------------------------------------
  // Access
  // -----------------------------------------------------------------------------------------------------------------

  /**
   * Establishes that the environment exists and belongs to the calling user. The read takes no lock: the question is
   * answered by three columns that never change during a run, and most callers - the status, the trail and the
   * cancellation - are read paths with no transaction of their own, where a pessimistic lock would raise
   * {@code No active transaction} rather than an answer. The one caller that has to serialize against a concurrent
   * writer, {@link #prepare}, locks the tenant itself inside its transaction.
   */
  private Tenant requireOwnedSimulation(Integer idSimTenant, User user) {
    Tenant tenant = data.findTenant(idSimTenant);
    if (tenant == null || tenant.getTenantKindType() != TenantKindType.SIMULATION_COPY
        || !user.getActualIdTenant().equals(tenant.getIdParentTenant())) {
      throw new DataViolationException("id.tenant", "simulation.algotop.not.found", null);
    }
    return tenant;
  }

  /**
   * The language of the user a run is executed for, falling back to the root language when the account carries none.
   *
   * @param user the owner of the run
   * @return the locale the details of the trail are resolved in
   */
  private static Locale localeOf(User user) {
    return user.getLocaleStr() == null || user.getLocaleStr().isBlank() ? Locale.ROOT : user.createAndGetJavaLocale();
  }

  private User currentUser() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getDetails() instanceof User user)) {
      throw new SecurityException(grafiosch.BaseConstants.CLIENT_SECURITY_BREACH);
    }
    return user;
  }

  private static DataViolationException invalid(String messageKey) {
    return new DataViolationException("id.tenant", messageKey, null);
  }

  private AlgoReplayState openState(AlgoSimulationResult run, Tenant tenant, AlgoTop algoTop,
      AlgoReplayMarketData market, boolean initialPurchaseRequired) {
    User user = currentUser();
    return new AlgoReplayState(run, tenant, algoTop, market, initialPurchaseRequired, localeOf(user),
        rebalancing.hasRebalancingStrategy(algoTop),
        entityLimitService.resolve(user, LimitKeyConfig.KEY_ALGO_EVENT_LOG).orElse(null), source, algoBuckets,
        algoMembers, costEstimator, taxEstimator, incomeService, events, transactionTemplate,
        globalparametersService.getCurrencyPrecision(), booking.fxRates(market));
  }
}
