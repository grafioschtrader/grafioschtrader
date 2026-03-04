package grafioschtrader.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.TaskDataChange;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.repository.TenantLimitsHelper;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.algo.SimulationTenantCreateDTO;
import grafioschtrader.algo.SimulationTenantInfo;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.SecaccountTradingPeriod;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitycashaccount;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.types.TaskTypeExtended;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

/**
 * Service for managing simulation tenants created from AlgoTop strategies.
 * Handles creation, listing, and deletion of simulation environments.
 */
@Service
@Transactional
public class SimulationTenantService {

  @PersistenceContext
  private EntityManager em;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private AlgoTopJpaRepository algoTopJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  /**
   * Creates a simulation tenant from the given AlgoTop strategy. Copies portfolios, security accounts,
   * and cash accounts from the user's main tenant. Optionally copies transactions up to the AlgoTop's
   * reference date or creates deposit transactions from user-specified cash balances.
   *
   * @param dto the creation request containing AlgoTop ID, copy mode, and optional cash balances
   * @return the created simulation Tenant
   */
  public Tenant createSimulationTenant(SimulationTenantCreateDTO dto) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer mainIdTenant = user.getActualIdTenant();

    // 1. Validate max simulation count
    int currentCount = tenantJpaRepository.countByIdParentTenant(mainIdTenant);
    int maxAllowed = TenantLimitsHelper.getMaxValueByKey(em,
        GlobalParamKeyDefault.GLOB_KEY_MAX_SIMULATION_ENVIRONMENTS);
    if (currentCount >= maxAllowed) {
      throw new DataViolationException("id.algo.top", "simulation.max.exceeded", new Object[] { maxAllowed });
    }

    // 2. Load and validate AlgoTop
    AlgoTop algoTop = algoTopJpaRepository.findById(dto.getIdAlgoTop()).orElse(null);
    if (algoTop == null || !mainIdTenant.equals(algoTop.getIdTenant())) {
      throw new DataViolationException("id.algo.top", "simulation.algotop.not.found", null);
    }

    // 3. Validate referenceDate for transaction copy
    if (dto.isCopyTransactions() && algoTop.getReferenceDate() == null) {
      throw new DataViolationException("reference.date", "simulation.no.reference.date", null);
    }

    // 4. Create simulation tenant
    Tenant mainTenant = tenantJpaRepository.getReferenceById(mainIdTenant);
    Tenant simTenant = new Tenant(dto.getTenantName(), mainTenant.getCurrency(),
        user.getIdUser(), TenantKindType.SIMULATION_COPY, mainTenant.isExcludeDivTax());
    simTenant.setIdParentTenant(mainIdTenant);
    simTenant.setIdAlgoTop(dto.getIdAlgoTop());
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

    if (dto.isCopyTransactions()) {
      // 9a. Copy transactions up to referenceDate
      copyTransactionsUpToDate(mainIdTenant, simIdTenant, securityAccountMap, cashAccountMap,
          algoTop.getReferenceDate());

      // Trigger holding table rebuild
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.CURRENCY_CHANGED_ON_TENANT_OR_PORTFOLIO,
          TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now(), simIdTenant, Tenant.class.getSimpleName()));
    } else if (dto.getCashBalances() != null && !dto.getCashBalances().isEmpty()) {
      // 9b. Create deposit transactions for specified cash balances
      createDepositTransactions(simIdTenant, cashAccountMap, dto.getCashBalances());
    }

    return simTenant;
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
      Long txCount = em.createQuery("SELECT COUNT(t) FROM Transaction t WHERE t.idTenant = :tid", Long.class)
          .setParameter("tid", sim.getIdTenant()).getSingleResult();

      result.add(new SimulationTenantInfo(sim.getIdTenant(), sim.getTenantName(), sim.getIdAlgoTop(), algoTopName,
          txCount > 0));
    }
    return result;
  }

  /**
   * Deletes a simulation tenant and all its data. Validates that the caller owns the simulation tenant.
   *
   * @param idSimTenant the ID of the simulation tenant to delete
   */
  public void deleteSimulationTenant(Integer idSimTenant) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer mainIdTenant = user.getActualIdTenant();

    Tenant simTenant = tenantJpaRepository.findById(idSimTenant).orElse(null);
    if (simTenant == null || simTenant.getTenantKindType() != TenantKindType.SIMULATION_COPY
        || !mainIdTenant.equals(simTenant.getIdParentTenant())) {
      throw new DataViolationException("id.tenant", "simulation.algotop.not.found", null);
    }

    // Delete all tenant data in correct order (child tables first)
    String[] tables = { Transaction.TABNAME, Securitycashaccount.TABNAME, Portfolio.TABNAME };
    for (String table : tables) {
      jdbcTemplate.update("DELETE FROM " + table + " WHERE id_tenant=?", idSimTenant);
    }

    // Delete holding tables
    jdbcTemplate.update("DELETE FROM hold_securityaccount_security WHERE id_tenant=?", idSimTenant);
    jdbcTemplate.update("DELETE FROM hold_cashaccount_balance WHERE id_tenant=?", idSimTenant);
    jdbcTemplate.update("DELETE FROM hold_cashaccount_deposit WHERE id_tenant=?", idSimTenant);

    // Delete watchlists and their security associations
    jdbcTemplate.update(
        "DELETE wsc FROM " + Watchlist.TABNAME_SEC_CUR + " wsc INNER JOIN " + Watchlist.TABNAME
            + " w ON wsc.id_watchlist = w.id_watchlist WHERE w.id_tenant=?",
        idSimTenant);
    jdbcTemplate.update("DELETE FROM " + Watchlist.TABNAME + " WHERE id_tenant=?", idSimTenant);

    tenantJpaRepository.deleteById(idSimTenant);
  }

  /**
   * Copies the watchlist referenced by the AlgoTop strategy to the simulation tenant. The copied watchlist contains the
   * same securities as the original but belongs to the simulation tenant.
   *
   * @param algoTop       the AlgoTop strategy whose watchlist should be copied
   * @param simIdTenant   the ID of the simulation tenant
   */
  private void copyWatchlistForAlgoTop(AlgoTop algoTop, Integer simIdTenant) {
    if (algoTop.getIdWatchlist() == null) {
      return;
    }
    Watchlist sourceWatchlist = em.find(Watchlist.class, algoTop.getIdWatchlist());
    if (sourceWatchlist == null) {
      return;
    }
    // Initialize lazy-loaded security list before detaching
    List<Securitycurrency<?>> securities = new ArrayList<>(sourceWatchlist.getSecuritycurrencyList());
    Watchlist simWatchlist = new Watchlist(simIdTenant, sourceWatchlist.getName());
    simWatchlist.setSecuritycurrencyList(securities);
    em.persist(simWatchlist);
    em.flush();
  }

  private Map<Integer, Portfolio> copyPortfolios(Integer sourceIdTenant, Integer targetIdTenant) {
    Map<Integer, Portfolio> portfolioMap = new HashMap<>();
    TypedQuery<Portfolio> q = em.createQuery("SELECT p FROM Portfolio p WHERE p.idTenant = ?1", Portfolio.class);
    List<Portfolio> portfolios = q.setParameter(1, sourceIdTenant).getResultList();
    em.clear();
    for (Portfolio portfolio : portfolios) {
      Integer oldId = portfolio.getId();
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
    TypedQuery<Securityaccount> q = em.createQuery("SELECT c FROM Securityaccount c WHERE c.idTenant = ?1",
        Securityaccount.class);
    List<Securityaccount> securityaccounts = q.setParameter(1, sourceIdTenant).getResultList();
    em.clear();
    for (Securityaccount sa : securityaccounts) {
      Integer oldId = sa.getId();
      sa.setIdTenant(targetIdTenant);
      sa.setIdSecuritycashAccount(null);
      sa.setSecurityTransactionList(null);
      sa.setPortfolio(portfolioMap.get(sa.getPortfolio().getIdPortfolio()));
      List<SecaccountTradingPeriod> freshPeriods = new ArrayList<>();
      for (SecaccountTradingPeriod tp : sa.getTradingPeriods()) {
        tp.setIdSecaccountTradingPeriod(null);
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
    TypedQuery<Cashaccount> q = em.createQuery("SELECT c FROM Cashaccount c WHERE c.idTenant = ?1", Cashaccount.class);
    List<Cashaccount> cashaccounts = q.setParameter(1, sourceIdTenant).getResultList();
    em.clear();
    for (Cashaccount ca : cashaccounts) {
      Integer oldId = ca.getId();
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
   * Copies transactions from the source tenant to the simulation tenant up to the given reference date (inclusive).
   * All tenant-specific references are remapped to the simulation tenant's entities to ensure no references to the
   * source tenant remain:
   * <ul>
   * <li>{@code idTransaction} — reset to null, JPA auto-generates a new primary key</li>
   * <li>{@code idTenant} — set to {@code targetIdTenant}</li>
   * <li>{@code cashaccount} — remapped via {@code cashAccountMap} (source cash account ID → simulation Cashaccount)</li>
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
    Map<Integer, Transaction> transactionReMap = new HashMap<>();
    List<Transaction> newNotFinishedList = new ArrayList<>();
    Map<Integer, Integer> connectIdToIdMap = new HashMap<>();

    // Convert referenceDate to end-of-day timestamp for inclusive comparison
    LocalDateTime cutoff = LocalDateTime.of(referenceDate, LocalTime.MAX);

    TypedQuery<Transaction> q = em.createQuery(
        "SELECT t FROM Transaction t WHERE t.idTenant = ?1 AND t.transactionTime <= ?2 ORDER BY t.transactionTime",
        Transaction.class);
    List<Transaction> transactionList = q.setParameter(1, sourceIdTenant)
        .setParameter(2, cutoff).getResultList();
    em.clear();

    for (Transaction tx : transactionList) {
      Integer oldId = tx.getId();
      tx.setIdTenant(targetIdTenant);
      tx.setIdTransaction(null);
      tx.setCashaccount(cashAccountMap.get(tx.getCashaccount().getIdSecuritycashAccount()));
      if (tx.getIdSecurityaccount() != null) {
        Securityaccount mapped = securityAccountMap.get(tx.getIdSecurityaccount());
        tx.setIdSecurityaccount(mapped != null ? mapped.getIdSecuritycashAccount() : null);
      }

      if (tx.getConnectedIdTransaction() != null) {
        Transaction connected = transactionReMap.get(tx.getConnectedIdTransaction());
        if (connected == null) {
          newNotFinishedList.add(tx);
          tx.setConnectedIdTransaction(null);
        } else {
          tx.setConnectedIdTransaction(connected.getIdTransaction());
        }
      }

      em.persist(tx);
      transactionReMap.put(oldId, tx);
      if (tx.getConnectedIdTransaction() != null) {
        connectIdToIdMap.put(tx.getConnectedIdTransaction(), tx.getIdTransaction());
      }
    }

    for (Transaction tx : newNotFinishedList) {
      tx.setConnectedIdTransaction(connectIdToIdMap.get(tx.getIdTransaction()));
      em.persist(tx);
    }
    em.flush();
  }

  private void createDepositTransactions(Integer simIdTenant, Map<Integer, Cashaccount> cashAccountMap,
      Map<Integer, Double> cashBalances) {
    LocalDateTime now = LocalDateTime.now();

    for (Map.Entry<Integer, Double> entry : cashBalances.entrySet()) {
      Integer originalCashAccountId = entry.getKey();
      Double amount = entry.getValue();
      if (amount == null || amount <= 0.0) {
        continue;
      }

      Cashaccount simCashAccount = cashAccountMap.get(originalCashAccountId);
      if (simCashAccount == null) {
        continue;
      }

      Transaction deposit = new Transaction(simCashAccount, amount, TransactionType.DEPOSIT, now);
      deposit.setIdTenant(simIdTenant);
      em.persist(deposit);
    }
    em.flush();
  }
}
