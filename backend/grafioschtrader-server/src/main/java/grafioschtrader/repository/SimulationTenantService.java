package grafioschtrader.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.service.EntityLimitService;
import grafioschtrader.algo.SimulationDateBounds;
import grafioschtrader.algo.SimulationPreviewDto;
import grafioschtrader.algo.SimulationTenantCreateDTO;
import grafioschtrader.algo.SimulationTenantInfo;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.dto.IMinMaxDateHistoryquote;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.SecaccountTradingPeriod;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.service.AlgoHistoricalValuationService;
import grafioschtrader.types.SimulationInitializationMode;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Service for managing simulation tenants created from AlgoTop strategies. Handles creation, listing, and deletion of
 * simulation environments.
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class SimulationTenantService {

  @Autowired
  private SimulationCleanupRepository cleanup;
  @Autowired
  private AlgoHistoricalValuationService valuation;
  @Autowired
  private SimulationSourceRepository source;
  @Autowired
  private TransactionJpaRepository transactions;
  @Autowired
  private HoldSecurityaccountSecurityJpaRepository securityHoldings;
  @Autowired
  private HoldCashaccountBalanceJpaRepository cashHoldings;
  @Autowired
  private HoldCashaccountDepositJpaRepository deposits;

  @PersistenceContext
  private EntityManager em;

  @Autowired
  private EntityLimitService entityLimitService;

  @Autowired
  private grafioschtrader.service.SimulationRunActivityService runActivity;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private AlgoTopJpaRepository algoTopJpaRepository;

  @Autowired
  private WatchlistJpaRepository watchlistJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  /**
   * Creates a simulation tenant from the given AlgoTop strategy. Copies portfolios, security accounts, and cash
   * accounts from the user's main tenant. Establishes a dated opening ledger and reconstructs its holdings before
   * committing the environment.
   *
   * @param dto the creation request containing AlgoTop ID, copy mode, and optional cash balances
   * @return the created simulation Tenant
   */
  public Tenant createSimulationTenant(SimulationTenantCreateDTO dto) throws Exception {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer mainIdTenant = user.getActualIdTenant();

    Tenant mainTenant = em.find(Tenant.class, mainIdTenant, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    validateRequest(dto, mainIdTenant);
    Optional<Integer> maxAllowedOpt = entityLimitService.resolve(user, LimitKeyConfig.KEY_SIMULATION_TENANT);
    if (maxAllowedOpt.isPresent() && tenantJpaRepository.countByIdParentTenant(mainIdTenant) >= maxAllowedOpt.get()) {
      throw new DataViolationException("id.algo.top", "simulation.max.exceeded", new Object[] { maxAllowedOpt.get() });
    }
    AlgoTop algoTop = algoTopJpaRepository.findById(dto.getIdAlgoTop()).orElseThrow();
    SimulationPreviewDto preview = previewSimulation(dto);
    if (!preview.errors.isEmpty() || !preview.unresolvedPositions.isEmpty())
      throw AlgoHistoricalValuationService.invalid(AlgoHistoricalValuationService.FIELD_SIMULATION_START_DATE,
          "simulation.opening.unresolved", String.join(", ", preview.errors));

    // 4. Create simulation tenant
    Tenant simTenant = new Tenant(dto.getTenantName(), mainTenant.getCurrency(), user.getIdUser(),
        TenantKindType.SIMULATION_COPY, mainTenant.isExcludeDivTax());
    simTenant.setIdParentTenant(mainIdTenant);
    simTenant.setIdAlgoTop(dto.getIdAlgoTop());
    simTenant.setSimulationStartDate(dto.getSimulationStartDate());
    simTenant.setSimulationInitializationMode(dto.getInitializationMode());
    simTenant = tenantJpaRepository.save(simTenant);
    em.flush();

    Integer simIdTenant = simTenant.getIdTenant();

    // 5. Copy portfolios
    Map<Integer, Portfolio> portfolioMap = copyPortfolios(mainIdTenant, simIdTenant);

    // 6. Copy security accounts
    Map<Integer, Securityaccount> securityAccountMap = copySecurityAccounts(mainIdTenant, simIdTenant, portfolioMap);

    // 7. Copy cash accounts
    Map<Integer, Cashaccount> cashAccountMap = copyCashAccounts(mainIdTenant, simIdTenant, portfolioMap,
        securityAccountMap);

    // 8. Copy the watchlist referenced by the AlgoTop strategy
    copyWatchlistForAlgoTop(algoTop, simIdTenant);

    if (dto.getInitializationMode() == SimulationInitializationMode.COPY_PORTFOLIO) {
      copyTransactionsUpToDate(mainIdTenant, simIdTenant, securityAccountMap, cashAccountMap,
          dto.getSimulationStartDate());
    } else {
      Map<Integer, Double> opening = preview.accounts.stream().collect(Collectors
          .toMap(SimulationPreviewDto.AccountBalance::idCashaccount, SimulationPreviewDto.AccountBalance::balance));
      createDepositTransactions(simIdTenant, cashAccountMap, opening, dto.getSimulationStartDate());
    }
    em.flush();
    em.clear();
    simTenant = tenantJpaRepository.findById(simIdTenant).orElseThrow();
    securityHoldings.createSecurityHoldingsEntireByTenant(simIdTenant);
    cashHoldings.createCashaccountBalanceEntireByTenant(simIdTenant);
    deposits.createCashaccountDepositTimeFrameByTenant(simIdTenant);

    return simTenant;
  }

  /** Validates the complete request in the service so alternate callers cannot bypass the REST validator. */
  private void validateRequest(SimulationTenantCreateDTO dto, Integer mainIdTenant) {
    AlgoHistoricalValuationService.validateDate(dto.getSimulationStartDate(),
        AlgoHistoricalValuationService.FIELD_SIMULATION_START_DATE);
    if (dto.getInitializationMode() == null || dto.getTenantName() == null || dto.getTenantName().isBlank()
        || dto.getTenantName().length() > 40 || dto.getIdAlgoTop() == null)
      throw openingInvalid();
    AlgoTop top = algoTopJpaRepository.findById(dto.getIdAlgoTop()).orElse(null);
    if (top == null || !mainIdTenant.equals(top.getIdTenant()))
      throw new DataViolationException("id.algo.top", "simulation.algotop.not.found", null);
    if (top.getReferenceDate() != null) {
      LocalDate requiredDate = top.getReferenceDate().plusDays(1);
      if (!requiredDate.equals(dto.getSimulationStartDate()))
        throw AlgoHistoricalValuationService.invalid(AlgoHistoricalValuationService.FIELD_SIMULATION_START_DATE,
            "simulation.portfolio.start.date", requiredDate);
      if (dto.getInitializationMode() == SimulationInitializationMode.MANUAL_CASH)
        throw AlgoHistoricalValuationService.invalid(AlgoHistoricalValuationService.FIELD_INITIALIZATION_MODE,
            "simulation.portfolio.manual.cash", "");
    }
    if (dto.getCashBalances() != null && !dto.getCashBalances().isEmpty()
        && dto.getInitializationMode() != SimulationInitializationMode.MANUAL_CASH)
      throw AlgoHistoricalValuationService.invalid(AlgoHistoricalValuationService.FIELD_INITIALIZATION_MODE,
          "simulation.opening.invalid", "");
    if (dto.getLiquidationAssignments() != null && !dto.getLiquidationAssignments().isEmpty()
        && dto.getInitializationMode() != SimulationInitializationMode.LIQUIDATE_TO_CASH)
      throw AlgoHistoricalValuationService.invalid(AlgoHistoricalValuationService.FIELD_INITIALIZATION_MODE,
          "simulation.opening.invalid", "");
    validateOpeningDateHasSourceHistory(dto, mainIdTenant);
  }

  /**
   * Both modes that read the source portfolio need a day the source tenant actually reached. Before the first
   * transaction there are no holdings and no cash, so the environment would start empty in a way the user did not ask
   * for - the same rule the allocation applies to its reference date. Manual cash copies nothing and is therefore
   * unbounded.
   *
   * @param dto          the creation request
   * @param mainIdTenant the tenant the opening state is read from
   */
  private void validateOpeningDateHasSourceHistory(SimulationTenantCreateDTO dto, Integer mainIdTenant) {
    if (dto.getInitializationMode() == SimulationInitializationMode.MANUAL_CASH) {
      return;
    }
    LocalDate firstDate = source.firstTransactionDate(mainIdTenant);
    if (firstDate == null || dto.getSimulationStartDate().isBefore(firstDate)) {
      throw AlgoHistoricalValuationService.invalid(AlgoHistoricalValuationService.FIELD_SIMULATION_START_DATE,
          "algo.reference.date.before.first.transaction", firstDate);
    }
  }

  /** The opening definition is wrong in a way that points at no single field of the dialog. */
  private static DataViolationException openingInvalid() {
    return AlgoHistoricalValuationService.invalid(AlgoHistoricalValuationService.FIELD_SIMULATION_START_DATE,
        "simulation.opening.invalid", "");
  }

  /** Computes account-currency balances without writing a tenant, transaction or historical quote. */
  public SimulationPreviewDto previewSimulation(SimulationTenantCreateDTO dto) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer mainIdTenant = user.getActualIdTenant();
    validateRequest(dto, mainIdTenant);
    var accounts = source.cashaccounts(mainIdTenant);
    Map<Integer, Cashaccount> byId = accounts.stream().collect(Collectors.toMap(Cashaccount::getId, a -> a));
    Map<Integer, Double> opening = new HashMap<>();
    accounts.forEach(a -> opening.put(a.getId(), 0.0));
    SimulationPreviewDto result = new SimulationPreviewDto();
    if (dto.getInitializationMode() == SimulationInitializationMode.MANUAL_CASH) {
      if (dto.getCashBalances() != null)
        dto.getCashBalances().forEach((id, amount) -> {
          if (!byId.containsKey(id) || amount == null || !Double.isFinite(amount) || amount < 0)
            throw openingInvalid();
          opening.put(id, amount);
        });
    } else if (dto.getInitializationMode() == SimulationInitializationMode.LIQUIDATE_TO_CASH) {
      var snapshot = valuation.value(mainIdTenant, dto.getSimulationStartDate());
      result.errors.addAll(snapshot.errors());
      opening.putAll(snapshot.cashBalances());
      Map<String, Integer> assignments = dto.getLiquidationAssignments() == null ? Map.of()
          : dto.getLiquidationAssignments();
      Set<String> unresolvedKeys = snapshot.positions().stream().filter(p -> p.idCashaccount() == null)
          .map(AlgoHistoricalValuationService.Position::key).collect(Collectors.toSet());
      if (!unresolvedKeys.containsAll(assignments.keySet())
          || assignments.values().stream().anyMatch(id -> !byId.containsKey(id)))
        throw openingInvalid();
      var securityAccounts = source.securityaccounts(mainIdTenant).stream()
          .collect(Collectors.toMap(Securityaccount::getId, a -> a.getName()));
      for (var position : snapshot.positions()) {
        Integer destination = position.idCashaccount() != null ? position.idCashaccount()
            : assignments.get(position.key());
        if (destination == null) {
          result.unresolvedPositions.add(new SimulationPreviewDto.UnresolvedPosition(position.key(),
              position.security().getName(), securityAccounts.get(position.idSecurityaccount()),
              position.security().getCurrency(), position.units(), position.closingValue()));
        } else {
          Cashaccount account = byId.get(destination);
          if (account == null)
            throw openingInvalid();
          Double from = snapshot.fx().get(position.security().getCurrency());
          Double to = snapshot.fx().get(account.getCurrency());
          if (from != null && to != null)
            opening.merge(destination, position.closingValue() * from / to, Double::sum);
        }
      }
    } else {
      source.transactions(mainIdTenant, dto.getSimulationStartDate().plusDays(1))
          .forEach(tx -> opening.merge(tx.getCashaccount().getId(), tx.getCashaccountAmount(), Double::sum));
    }
    accounts.forEach(a -> result.accounts
        .add(new SimulationPreviewDto.AccountBalance(a.getId(), a.getName(), a.getCurrency(), opening.get(a.getId()))));
    return result;
  }

  /**
   * Returns all simulation tenants for the current user's main tenant, enriched with AlgoTop names.
   */
  public List<SimulationTenantInfo> getSimulationTenants() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer mainIdTenant = user.getActualIdTenant();

    List<Tenant> simTenants = tenantJpaRepository.findByIdParentTenant(mainIdTenant);
    List<SimulationTenantInfo> result = new ArrayList<>();

    for (Tenant sim : simTenants) {
      String algoTopName = null;
      if (sim.getIdAlgoTop() != null) {
        AlgoTop algoTop = algoTopJpaRepository.findById(sim.getIdAlgoTop()).orElse(null);
        if (algoTop != null) {
          algoTopName = algoTop.getName();
        }
      }

      // Check if simulation has transactions
      long txCount = transactions.countByIdTenant(sim.getIdTenant());

      SimulationTenantInfo info = new SimulationTenantInfo(sim.getIdTenant(), sim.getTenantName(), sim.getIdAlgoTop(),
          algoTopName, txCount > 0);
      info.setActive(runActivity.isActive(sim.getIdTenant()));
      info.setSimulationStartDate(sim.getSimulationStartDate());
      info.setInitializationMode(sim.getSimulationInitializationMode());
      result.add(info);
    }
    return result;
  }

  /**
   * Reports what limits the opening date of an environment of this hierarchy, so the dialog can say it up front. The
   * universe date is the latest of the first quotes of the instruments and their currency pairs, because a replay can
   * only decide once every one of them has data. Nothing here is enforced: an earlier opening date stays allowed and is
   * merely uninformative until the data begins.
   *
   * @param idAlgoTop   the hierarchy the environment would belong to
   * @param openingDate the date currently entered in the dialog, or null before one is chosen. It only adds what the
   *                    portfolio cannot value on that day and never rejects the date.
   * @return the dates, with the universe date left empty when an instrument has no price data at all
   */
  @Transactional(readOnly = true)
  public SimulationDateBounds getSimulationDateBounds(Integer idAlgoTop, LocalDate openingDate) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer mainIdTenant = user.getActualIdTenant();
    AlgoTop algoTop = algoTopJpaRepository.findById(idAlgoTop).orElse(null);
    if (algoTop == null || !mainIdTenant.equals(algoTop.getIdTenant()))
      throw new DataViolationException("id.algo.top", "simulation.algotop.not.found", null);
    SimulationDateBounds bounds = new SimulationDateBounds();
    bounds.firstTransactionDate = source.firstTransactionDate(mainIdTenant);
    if (openingDate != null && openingDate.isBefore(LocalDate.now()))
      bounds.unpricedAtOpeningDate.addAll(valuation.value(mainIdTenant, openingDate).errors());
    List<Security> securities = algoTop.getIdWatchlist() == null ? List.of()
        : watchlistJpaRepository.securitiesOfWatchlist(algoTop.getIdWatchlist());
    if (securities.isEmpty())
      return bounds;
    Tenant tenant = tenantJpaRepository.findById(mainIdTenant).orElseThrow();
    Map<Integer, String> universe = new LinkedHashMap<>();
    securities.forEach(s -> universe.put(s.getIdSecuritycurrency(), s.getName()));
    for (String currency : securities.stream().map(Security::getCurrency)
        .collect(Collectors.toCollection(TreeSet::new))) {
      if (currency.equals(tenant.getCurrency()))
        continue;
      Currencypair pair = currencypairJpaRepository.findByFromCurrencyAndToCurrency(currency, tenant.getCurrency());
      if (pair == null)
        pair = currencypairJpaRepository.findByFromCurrencyAndToCurrency(tenant.getCurrency(), currency);
      // A pair that does not exist yet is not a price problem of an instrument; the valuation reports it when it is
      // actually needed, and naming it here would only puzzle the user before anything was chosen.
      if (pair != null)
        universe.put(pair.getIdSecuritycurrency(), currency + "/" + tenant.getCurrency());
    }
    Map<Integer, LocalDate> firstQuote = historyquoteJpaRepository
        .getMinMaxDateByIdSecuritycurrencyIds(List.copyOf(universe.keySet())).stream()
        .filter(m -> m.getMinDate() != null)
        .collect(Collectors.toMap(IMinMaxDateHistoryquote::getIdSecuritycurrency, IMinMaxDateHistoryquote::getMinDate));
    LocalDate latestStart = null;
    for (var member : universe.entrySet()) {
      LocalDate start = firstQuote.get(member.getKey());
      if (start == null) {
        bounds.instrumentsWithoutHistory.add(member.getValue());
      } else if (latestStart == null || start.isAfter(latestStart)) {
        latestStart = start;
      }
    }
    bounds.universeFromDate = bounds.instrumentsWithoutHistory.isEmpty() ? latestStart : null;
    return bounds;
  }

  /**
   * Deletes a simulation tenant and all its data. Validates that the caller owns the simulation tenant.
   *
   * @param idSimTenant the ID of the simulation tenant to delete
   */
  public void deleteSimulationTenant(Integer idSimTenant) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer mainIdTenant = user.getActualIdTenant();

    // Home before environment, the same order creation and replay preparation use, so that the three can never wait
    // on each other in a cycle. Holding home also stops a replay being submitted between the check and the delete.
    em.find(Tenant.class, mainIdTenant, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    Tenant simTenant = tenantJpaRepository.findById(idSimTenant).orElse(null);
    if (simTenant == null || simTenant.getTenantKindType() != TenantKindType.SIMULATION_COPY
        || !mainIdTenant.equals(simTenant.getIdParentTenant())) {
      throw new DataViolationException("id.tenant", "simulation.algotop.not.found", null);
    }
    // Deleting an environment under its own worker would pull the accounts out from beneath the transactions it is
    // still booking. The user cancels and waits; a delete never cancels on their behalf, because the fills already
    // booked are theirs to look at first.
    if (runActivity.isActive(idSimTenant)) {
      throw new DataViolationException("id.tenant", "gt.simulation.delete.run.active", null);
    }
    em.find(Tenant.class, idSimTenant, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

    cleanup.deleteTenantData(idSimTenant);
    em.clear();
    tenantJpaRepository.deleteById(idSimTenant);
  }

  /**
   * Copies the watchlist referenced by the AlgoTop strategy to the simulation tenant. The copied watchlist contains the
   * same securities as the original but belongs to the simulation tenant.
   *
   * @param algoTop     the AlgoTop strategy whose watchlist should be copied
   * @param simIdTenant the ID of the simulation tenant
   */
  private void copyWatchlistForAlgoTop(AlgoTop algoTop, Integer simIdTenant) {
    if (algoTop.getIdWatchlist() == null) {
      return;
    }
    Watchlist sourceWatchlist = em.find(Watchlist.class, algoTop.getIdWatchlist());
    if (sourceWatchlist == null || !algoTop.getIdTenant().equals(sourceWatchlist.getIdTenant()))
      throw openingInvalid();
    // Copy membership while retaining the globally shared securities.
    List<Securitycurrency<?>> securities = new ArrayList<>(sourceWatchlist.getSecuritycurrencyList());
    Watchlist simWatchlist = new Watchlist(simIdTenant, sourceWatchlist.getName());
    simWatchlist.setSecuritycurrencyList(securities);
    em.persist(simWatchlist);
    em.flush();
  }

  private Map<Integer, Portfolio> copyPortfolios(Integer sourceIdTenant, Integer targetIdTenant) {
    Map<Integer, Portfolio> portfolioMap = new HashMap<>();
    List<Portfolio> portfolios = source.portfolios(sourceIdTenant);
    for (Portfolio original : portfolios) {
      Integer oldId = original.getId();
      Portfolio portfolio = new Portfolio();
      BeanUtils.copyProperties(original, portfolio);
      portfolio.setClosedUntil(null);
      portfolio.setIdTenant(targetIdTenant);
      portfolio.setIdPortfolio(null);
      portfolio.setSecuritycashaccountList(new ArrayList<>());
      em.persist(portfolio);
      portfolioMap.put(oldId, portfolio);
    }
    em.flush();
    return portfolioMap;
  }

  private Map<Integer, Securityaccount> copySecurityAccounts(Integer sourceIdTenant, Integer targetIdTenant,
      Map<Integer, Portfolio> portfolioMap) {
    Map<Integer, Securityaccount> securityAccountMap = new HashMap<>();
    List<Securityaccount> securityaccounts = source.securityaccounts(sourceIdTenant);
    for (Securityaccount original : securityaccounts) {
      Integer oldId = original.getId();
      Securityaccount sa = new Securityaccount();
      BeanUtils.copyProperties(original, sa);
      sa.setIdTenant(targetIdTenant);
      sa.setIdSecuritycashAccount(null);
      sa.setSecurityTransactionList(null);
      sa.setPortfolio(portfolioMap.get(sa.getPortfolio().getIdPortfolio()));
      List<SecaccountTradingPeriod> freshPeriods = new ArrayList<>();
      for (SecaccountTradingPeriod originalPeriod : original.getTradingPeriods()) {
        SecaccountTradingPeriod tp = new SecaccountTradingPeriod();
        BeanUtils.copyProperties(originalPeriod, tp);
        tp.setIdSecaccountTradingPeriod(null);
        tp.setIdSecuritycashAccount(null);
        freshPeriods.add(tp);
      }
      sa.replaceTradingPeriods(freshPeriods);
      em.persist(sa);
      securityAccountMap.put(oldId, sa);
    }
    em.flush();
    return securityAccountMap;
  }

  private Map<Integer, Cashaccount> copyCashAccounts(Integer sourceIdTenant, Integer targetIdTenant,
      Map<Integer, Portfolio> portfolioMap, Map<Integer, Securityaccount> securityAccountMap) {
    Map<Integer, Cashaccount> cashAccountMap = new HashMap<>();
    List<Cashaccount> cashaccounts = source.cashaccounts(sourceIdTenant);
    for (Cashaccount original : cashaccounts) {
      Integer oldId = original.getId();
      Cashaccount ca = new Cashaccount();
      BeanUtils.copyProperties(original, ca);
      ca.setIdTenant(targetIdTenant);
      ca.setTransactionList(null);
      if (ca.getConnectIdSecurityaccount() != null) {
        Securityaccount mapped = securityAccountMap.get(ca.getConnectIdSecurityaccount());
        ca.setConnectIdSecurityaccount(mapped != null ? mapped.getIdSecuritycashAccount() : null);
      }
      ca.setIdSecuritycashAccount(null);
      ca.setPortfolio(portfolioMap.get(ca.getPortfolio().getIdPortfolio()));
      em.persist(ca);
      cashAccountMap.put(oldId, ca);
    }
    em.flush();
    return cashAccountMap;
  }

  /**
   * Copies transactions from the source tenant to the simulation tenant up to the given reference date (inclusive). All
   * tenant-specific references are remapped to the simulation tenant's entities to ensure no references to the source
   * tenant remain:
   * <ul>
   * <li>{@code idTransaction} — reset to null, JPA auto-generates a new primary key</li>
   * <li>{@code idTenant} — set to {@code targetIdTenant}</li>
   * <li>{@code cashaccount} — remapped via {@code cashAccountMap} (source cash account ID → simulation
   * Cashaccount)</li>
   * <li>{@code idSecurityaccount} — remapped via {@code securityAccountMap} (source security account ID → simulation
   * Securityaccount)</li>
   * <li>{@code connectedIdTransaction} — remapped using a two-pass algorithm: the first pass resolves backward
   * references (connected transaction already copied), while forward references (connected transaction not yet copied)
   * are deferred and resolved in the second pass. This handles mutual references in account transfers and
   * one-directional references in margin instruments (close/finance cost → open position).</li>
   * </ul>
   * Fields referencing shared (non-tenant-specific) entities are left unchanged: {@code security} (global),
   * {@code idCurrencypair} (global).
   *
   * @param sourceIdTenant     the source (main) tenant ID to copy transactions from
   * @param targetIdTenant     the simulation tenant ID to copy transactions into
   * @param securityAccountMap mapping from source security account IDs to simulation Securityaccount entities
   * @param cashAccountMap     mapping from source cash account IDs to simulation Cashaccount entities
   * @param referenceDate      inclusive cutoff date — only transactions at or before this date are copied
   */
  private void copyTransactionsUpToDate(Integer sourceIdTenant, Integer targetIdTenant,
      Map<Integer, Securityaccount> securityAccountMap, Map<Integer, Cashaccount> cashAccountMap,
      LocalDate referenceDate) {
    Map<Integer, Transaction> copied = new HashMap<>();
    List<Transaction> originals = source.transactions(sourceIdTenant, referenceDate.plusDays(1));
    for (Transaction original : originals) {
      Transaction tx = new Transaction();
      BeanUtils.copyProperties(original, tx);
      // The security getter and setter have different JavaBean property names.
      tx.setSecuritycurrency(original.getSecurity());
      tx.setIdTransaction(null);
      tx.setTransactionTime(original.getTransactionDate().atTime(original.getTransactionTime().toLocalTime()));
      tx.setIdTenant(targetIdTenant);
      tx.setSimulationOpening(true);
      tx.setAlgoFillId(null);
      tx.setAlgoSignalId(null);
      tx.setIdStandingOrder(null);
      tx.setIdSecurityActionApp(null);
      tx.setIdSecurityTransfer(null);
      tx.setCashaccount(cashAccountMap.get(original.getCashaccount().getId()));
      tx.setConnectedIdTransaction(null);
      if (original.getIdSecurityaccount() != null) {
        Securityaccount account = securityAccountMap.get(original.getIdSecurityaccount());
        if (account == null)
          throw AlgoHistoricalValuationService.invalid(AlgoHistoricalValuationService.FIELD_SIMULATION_START_DATE,
              "simulation.opening.unresolved", original.getId());
        tx.setIdSecurityaccount(account.getId());
      }
      em.persist(tx);
      copied.put(original.getId(), tx);
    }
    em.flush();
    for (Transaction original : originals) {
      if (original.getConnectedIdTransaction() == null)
        continue;
      Transaction linked = copied.get(original.getConnectedIdTransaction());
      if (linked == null && original.getSecurity() != null && original.getSecurity().isMarginInstrument())
        throw AlgoHistoricalValuationService.invalid(AlgoHistoricalValuationService.FIELD_SIMULATION_START_DATE,
            "simulation.opening.unresolved", original.getId());
      copied.get(original.getId()).setConnectedIdTransaction(linked == null ? null : linked.getId());
    }
    em.flush();
  }

  private void createDepositTransactions(Integer simIdTenant, Map<Integer, Cashaccount> cashAccountMap,
      Map<Integer, Double> cashBalances, LocalDate openingDate) throws Exception {
    LocalDateTime now = openingDate.atStartOfDay();

    for (Map.Entry<Integer, Double> entry : cashBalances.entrySet()) {
      Integer originalCashAccountId = entry.getKey();
      Double amount = entry.getValue();
      if (amount == null || amount == 0.0) {
        continue;
      }

      Cashaccount simCashAccount = cashAccountMap.get(originalCashAccountId);
      if (simCashAccount == null) {
        continue;
      }

      // Opening cash may be negative even where normal trading disallows a new overdraft.
      Double borrowingRate = simCashAccount.getBorrowingRate();
      LocalDate activeToDate = simCashAccount.getActiveToDate();
      simCashAccount.setBorrowingRate(0.0);
      simCashAccount.setActiveToDate(null);
      Transaction deposit = new Transaction(simCashAccount, amount,
          amount > 0 ? TransactionType.DEPOSIT : TransactionType.WITHDRAWAL, now);
      deposit.setIdTenant(simIdTenant);
      deposit.setSimulationOpening(true);
      deposit.setSkipClosedUntilCheck(true);
      transactions.saveOnlyAttributes(deposit, null, Set.of());
      simCashAccount.setBorrowingRate(borrowingRate);
      simCashAccount.setActiveToDate(activeToDate);
    }
    em.flush();
  }
}
