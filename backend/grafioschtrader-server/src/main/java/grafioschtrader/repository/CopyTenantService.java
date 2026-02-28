package grafioschtrader.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.entities.UDFData;
import grafiosch.entities.UDFData.UDFDataKey;
import grafiosch.entities.UDFMetadata;
import grafiosch.entities.UDFMetadataGeneral;
import grafiosch.entities.User;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.SecaccountTradingPeriod;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitycashaccount;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.StandingOrder;
import grafioschtrader.entities.StandingOrderCashaccount;
import grafioschtrader.entities.StandingOrderSecurity;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.entities.Watchlist;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

/**
 * Copies all tenant-owned data from a source tenant to a target tenant. Used by
 * {@link grafioschtrader.task.exec.CopyTenantToDemoAccountsTask} to refresh demo accounts daily.
 *
 * <p>The copy runs inside a single transaction. It first deletes all existing data in the target tenant
 * (respecting FK ordering), then copies entities one by one from the source, remapping all tenant-specific
 * foreign keys to the newly persisted target entities. Global references (securities, currency pairs) are
 * shared and left unchanged.
 *
 * <p>Entity copy order follows FK dependencies:
 * <ol>
 *   <li>Portfolio (no FK dependencies within tenant)</li>
 *   <li>Securityaccount (FK to Portfolio, cascade-persists SecaccountTradingPeriod)</li>
 *   <li>Cashaccount (FK to Portfolio, optional FK to Securityaccount)</li>
 *   <li>StandingOrder (FK to Cashaccount, StandingOrderSecurity FK to Securityaccount)</li>
 *   <li>Watchlist (no tenant-internal FK dependencies)</li>
 *   <li>Transaction (FK to Cashaccount, Securityaccount, StandingOrder, self-referential connectedIdTransaction)</li>
 *   <li>CorrelationSet (references global securities only)</li>
 *   <li>UDF metadata and data (keyed by user ID, not tenant ID)</li>
 * </ol>
 */
@Service
@Transactional
public class CopyTenantService {

  @PersistenceContext
  private EntityManager em;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  /**
   * Copies all data from the source user's tenant to the target user's tenant. Existing data in the target
   * tenant is deleted first. Each entity type is copied with its tenant-specific FKs remapped via ID maps
   * built during earlier copy steps.
   *
   * @param sourceUser the user whose tenant data is copied from
   * @param targetUser the user whose tenant data is replaced
   */
  public void copyTenant(User sourceUser, User targetUser) {
    deleteData(targetUser.getIdTenant(), targetUser.getIdUser());
    Map<Integer, Portfolio> portfolioMap = copyPortfolio(sourceUser.getIdTenant(), targetUser.getIdTenant());
    Map<Integer, Securityaccount> securityaccountMap = copySecurityAccount(sourceUser.getIdTenant(),
        targetUser.getIdTenant(), portfolioMap);
    Map<Integer, Cashaccount> cashaccoutMap = copyCashaccount(sourceUser.getIdTenant(), targetUser.getIdTenant(),
        portfolioMap, securityaccountMap);
    Map<Integer, StandingOrder> standingOrderMap = copyStandingOrder(sourceUser.getIdTenant(),
        targetUser.getIdTenant(), securityaccountMap, cashaccoutMap);
    Map<Integer, Watchlist> watchlistMap = copyWatchlist(sourceUser.getIdTenant(), targetUser.getIdTenant());
    copyTransaction(sourceUser.getIdTenant(), targetUser.getIdTenant(), securityaccountMap, cashaccoutMap,
        standingOrderMap);
    copyCorrelationSet(sourceUser.getIdTenant(), targetUser.getIdTenant());
    Map<Integer, Integer> fieldMap = copyUDFMetadataGeneral(sourceUser.getIdUser(), targetUser.getIdUser());
    copyUDFMetadataSecurity(fieldMap, sourceUser.getIdUser(), targetUser.getIdUser());
    copyUDFData(fieldMap, sourceUser.getIdUser(), targetUser.getIdUser());
    updateTenantReference(sourceUser.getIdTenant(), targetUser.getIdTenant(), watchlistMap);
  }

  /**
   * Deletes all tenant-owned data from the target tenant in FK-safe order. Child rows (import positions,
   * transactions) are deleted before parent rows (accounts, portfolios). Standing order child tables
   * ({@code standing_order_cashaccount}, {@code standing_order_security}, {@code standing_order_failure})
   * cascade-delete automatically via {@code ON DELETE CASCADE} when the parent {@code standing_order} row
   * is removed. UDF tables use {@code id_user} instead of {@code id_tenant}.
   *
   * @param targetIdTenant the tenant ID whose data is deleted
   * @param targetIdUser   the user ID for UDF-related tables
   */
  private void deleteData(Integer targetIdTenant, Integer targetIdUser) {
    DelTab[] tables = new DelTab[] { new DelTab(ImportTransactionPos.TABNAME, true),
        new DelTab(ImportTransactionHead.TABNAME, true), new DelTab(Transaction.TABNAME, true),
        new DelTab(StandingOrder.TABNAME, true), new DelTab(Watchlist.TABNAME, true),
        new DelTab(Securitycashaccount.TABNAME, true),
        new DelTab(Portfolio.TABNAME, true), new DelTab(CorrelationSet.TABNAME, true),
        new DelTab(UDFData.TABNAME, false), new DelTab(UDFMetadata.TABNAME, false) };
    for (DelTab delTab : tables) {
      String deleteSQL = "DELETE FROM " + delTab.tabName + " WHERE " + (delTab.useIdTenant ? "id_tenant" : "id_user")
          + "=?";
      jdbcTemplate.update(deleteSQL, delTab.useIdTenant ? targetIdTenant : targetIdUser);
    }
  }

  /**
   * Copies all portfolios from the source tenant, resetting PKs and reassigning to the target tenant.
   * The {@code securitycashaccountList} is cleared to avoid cascading stale references — accounts are
   * copied separately. After {@code em.clear()}, loaded entities become detached; nulling the PK lets
   * JPA treat them as new inserts.
   *
   * @param sourceIdTenant the source tenant ID
   * @param targetIdTenant the target tenant ID
   * @return map from source portfolio ID to the newly persisted target Portfolio
   */
  private Map<Integer, Portfolio> copyPortfolio(Integer sourceIdTenant, Integer targetIdTenant) {
    Map<Integer, Portfolio> portfolioMap = new HashMap<>();
    TypedQuery<Portfolio> q = em.createQuery("SELECT p FROM Portfolio p WHERE p.idTenant = ?1", Portfolio.class);
    List<Portfolio> portfolios = q.setParameter(1, sourceIdTenant).getResultList();
    em.clear();
    for (Portfolio portfolio : portfolios) {
      Integer idPortfolio = portfolio.getId();
      portfolio.setIdTenant(targetIdTenant);
      portfolio.setIdPortfolio(null);
      portfolio.setSecuritycashaccountList(new ArrayList<>());
      em.persist(portfolio);
      portfolioMap.put(idPortfolio, portfolio);
    }
    em.flush();
    return portfolioMap;
  }

  /**
   * Copies all security accounts from the source tenant. The portfolio FK is remapped via {@code portfolioMap}.
   * The transaction list is cleared to avoid stale cascade references.
   *
   * <p>Trading periods require special handling: after {@code em.clear()}, the {@code tradingPeriods} field
   * holds a Hibernate PersistentBag from the old persistence context. The regular setter preserves this
   * reference (for orphanRemoval tracking), which causes "Don't change the reference to a collection with
   * delete-orphan enabled" on {@code em.persist()}. {@code replaceTradingPeriods()} replaces the PersistentBag
   * with a plain ArrayList. Each period's PK is nulled to let JPA auto-generate new IDs.
   *
   * @param sourceIdTenant the source tenant ID
   * @param targetIdTenant the target tenant ID
   * @param portfolioMap   mapping from source portfolio IDs to target Portfolio entities
   * @return map from source security account ID to the newly persisted target Securityaccount
   */
  private Map<Integer, Securityaccount> copySecurityAccount(Integer sourceIdTenant, Integer targetIdTenant,
      Map<Integer, Portfolio> portfolioMap) {
    Map<Integer, Securityaccount> securityAccountMap = new HashMap<>();
    TypedQuery<Securityaccount> q = em.createQuery("SELECT c FROM Securityaccount c WHERE c.idTenant = ?1",
        Securityaccount.class);
    List<Securityaccount> securityaccounts = q.setParameter(1, sourceIdTenant).getResultList();
    em.clear();
    for (Securityaccount securityaccount : securityaccounts) {
      Integer idSecurityaccount = securityaccount.getId();
      securityaccount.setIdTenant(targetIdTenant);
      securityaccount.setIdSecuritycashAccount(null);
      securityaccount.setSecurityTransactionList(null);
      securityaccount.setPortfolio(portfolioMap.get(securityaccount.getPortfolio().getIdPortfolio()));
      List<SecaccountTradingPeriod> freshPeriods = new ArrayList<>();
      for (SecaccountTradingPeriod tp : securityaccount.getTradingPeriods()) {
        tp.setIdSecaccountTradingPeriod(null);
        freshPeriods.add(tp);
      }
      securityaccount.replaceTradingPeriods(freshPeriods);
      em.persist(securityaccount);
      securityAccountMap.put(idSecurityaccount, securityaccount);
    }
    em.flush();
    return securityAccountMap;
  }

  /**
   * Copies all cash accounts from the source tenant. The portfolio FK is remapped via {@code portfolioMap}.
   * The optional {@code connectIdSecurityaccount} (margin account link) is remapped via
   * {@code securityaccountMap}. The transaction list is cleared to prevent stale cascade references.
   *
   * @param sourceIdTenant    the source tenant ID
   * @param targetIdTenant    the target tenant ID
   * @param portfolioMap      mapping from source portfolio IDs to target Portfolio entities
   * @param securityaccountMap mapping from source security account IDs to target Securityaccount entities
   * @return map from source cash account ID to the newly persisted target Cashaccount
   */
  private Map<Integer, Cashaccount> copyCashaccount(Integer sourceIdTenant, Integer targetIdTenant,
      Map<Integer, Portfolio> portfolioMap, Map<Integer, Securityaccount> securityaccountMap) {
    Map<Integer, Cashaccount> cashaccountMap = new HashMap<>();
    TypedQuery<Cashaccount> q = em.createQuery("SELECT c FROM Cashaccount c WHERE c.idTenant = ?1", Cashaccount.class);
    List<Cashaccount> cashaccounts = q.setParameter(1, sourceIdTenant).getResultList();
    em.clear();
    for (Cashaccount cashaccount : cashaccounts) {
      Integer idCashaccount = cashaccount.getId();
      cashaccount.setIdTenant(targetIdTenant);
      cashaccount.setTransactionList(null);
      if (cashaccount.getConnectIdSecurityaccount() != null) {
        cashaccount.setConnectIdSecurityaccount(
            securityaccountMap.get(cashaccount.getConnectIdSecurityaccount()).getIdSecuritycashAccount());
      }
      cashaccount.setIdSecuritycashAccount(null);
      cashaccount.setPortfolio(portfolioMap.get(cashaccount.getPortfolio().getIdPortfolio()));
      em.persist(cashaccount);
      cashaccountMap.put(idCashaccount, cashaccount);
    }
    em.flush();
    return cashaccountMap;
  }

  /**
   * Copies all standing orders from the source tenant. Uses a polymorphic JPA query that returns both
   * {@link StandingOrderCashaccount} and {@link StandingOrderSecurity} subtypes via JOINED inheritance.
   * The cash account FK is remapped for all standing orders. For {@code StandingOrderSecurity}, the
   * {@code idSecurityaccount} is additionally remapped. Global references ({@code security},
   * {@code idCurrencypair}) are left unchanged.
   *
   * <p>Child tables ({@code standing_order_cashaccount}, {@code standing_order_security}) are auto-persisted
   * by JPA JOINED inheritance. {@code StandingOrderFailure} rows (runtime failure history) are intentionally
   * not copied.
   *
   * @param sourceIdTenant    the source tenant ID
   * @param targetIdTenant    the target tenant ID
   * @param securityaccountMap mapping from source security account IDs to target Securityaccount entities
   * @param cashaccountMap    mapping from source cash account IDs to target Cashaccount entities
   * @return map from source standing order ID to the newly persisted target StandingOrder
   */
  private Map<Integer, StandingOrder> copyStandingOrder(Integer sourceIdTenant, Integer targetIdTenant,
      Map<Integer, Securityaccount> securityaccountMap, Map<Integer, Cashaccount> cashaccountMap) {
    Map<Integer, StandingOrder> standingOrderMap = new HashMap<>();
    TypedQuery<StandingOrder> q = em.createQuery("SELECT s FROM StandingOrder s WHERE s.idTenant = ?1",
        StandingOrder.class);
    List<StandingOrder> standingOrders = q.setParameter(1, sourceIdTenant).getResultList();
    em.clear();
    for (StandingOrder so : standingOrders) {
      Integer oldId = so.getIdStandingOrder();
      so.setIdStandingOrder(null);
      so.setIdTenant(targetIdTenant);
      so.setCashaccount(cashaccountMap.get(so.getCashaccount().getIdSecuritycashAccount()));
      if (so instanceof StandingOrderSecurity sos) {
        sos.setIdSecurityaccount(
            securityaccountMap.get(sos.getIdSecurityaccount()).getIdSecuritycashAccount());
      }
      em.persist(so);
      standingOrderMap.put(oldId, so);
    }
    em.flush();
    return standingOrderMap;
  }

  /**
   * Copies all watchlists from the source tenant. Creates new Watchlist instances for the target tenant
   * and copies the security/currency-pair associations. Does not use {@code em.clear()} because the
   * security list must remain managed for the many-to-many join table insert.
   *
   * @param sourceIdTenant the source tenant ID
   * @param targetIdTenant the target tenant ID
   * @return map from source watchlist ID to the newly persisted target Watchlist
   */
  private Map<Integer, Watchlist> copyWatchlist(Integer sourceIdTenant, Integer targetIdTenant) {
    Map<Integer, Watchlist> watchlistMap = new HashMap<>();
    TypedQuery<Watchlist> q = em.createQuery("SELECT c FROM Watchlist c WHERE c.idTenant = ?1", Watchlist.class);
    List<Watchlist> watchlists = q.setParameter(1, sourceIdTenant).getResultList();
    for (Watchlist watchlist : watchlists) {
      Integer id = watchlist.getId();
      Watchlist watchlistNew = new Watchlist(targetIdTenant, watchlist.getName());
      List<Securitycurrency<?>> securities = new ArrayList<>();
      securities.addAll(watchlist.getSecuritycurrencyList());
      watchlistNew.setSecuritycurrencyList(securities);
      em.persist(watchlistNew);
      watchlistMap.put(id, watchlistNew);
    }
    em.flush();
    return watchlistMap;
  }

  /**
   * Copies all correlation sets from the source tenant, preserving the security/currency-pair
   * associations and date/sampling configuration.
   *
   * @param sourceIdTenant the source tenant ID
   * @param targetIdTenant the target tenant ID
   */
  private void copyCorrelationSet(Integer sourceIdTenant, Integer targetIdTenant) {
    TypedQuery<CorrelationSet> q = em.createQuery("SELECT c FROM CorrelationSet c WHERE c.idTenant = ?1",
        CorrelationSet.class);
    List<CorrelationSet> correlationSetList = q.setParameter(1, sourceIdTenant).getResultList();
    for (CorrelationSet cs : correlationSetList) {
      CorrelationSet correlationSetNew = new CorrelationSet(targetIdTenant, cs.getName(), null, cs.getDateFrom(),
          cs.getDateTo(), cs.getSamplingPeriod().getValue(), cs.getRolling(), cs.isAdjustCurrency());
      List<Securitycurrency<?>> securities = new ArrayList<>();
      securities.addAll(cs.getSecuritycurrencyList());
      correlationSetNew.setSecuritycurrencyList(securities);
      em.persist(correlationSetNew);
    }
    em.flush();
  }

  /**
   * Copies general UDF metadata definitions from the source user to the target user.
   *
   * @param sourceIdUser the source user ID
   * @param targetIdUser the target user ID
   * @return map from source UDF metadata ID to the newly generated target UDF metadata ID
   */
  private Map<Integer, Integer> copyUDFMetadataGeneral(Integer sourceIdUser, Integer targetIdUser) {
    Map<Integer, Integer> fieldMap = new HashMap<>();
    TypedQuery<UDFMetadataGeneral> q = em.createQuery("SELECT u FROM UDFMetadataGeneral u WHERE u.idUser = ?1",
        UDFMetadataGeneral.class);

    List<UDFMetadataGeneral> udfMgList = q.setParameter(1, sourceIdUser).getResultList();
    for (UDFMetadataGeneral u : udfMgList) {
      UDFMetadataGeneral uDFMetadataGeneral = new UDFMetadataGeneral(u.getEntity(), targetIdUser,
          u.getUdfSpecialTypeAsByte(), u.getDescription(), u.getDescriptionHelp(), u.getUdfDataType(), u.getFieldSize(),
          u.getUiOrder());
      em.persist(uDFMetadataGeneral);
      fieldMap.put(u.getIdUDFMetadata(), uDFMetadataGeneral.getIdUDFMetadata());
    }
    em.flush();
    return fieldMap;
  }

  /**
   * Copies security-specific UDF metadata definitions from the source user to the target user.
   * Updates {@code fieldMap} in-place with the new mappings.
   *
   * @param fieldMap     map from source UDF metadata ID to target UDF metadata ID (updated in-place)
   * @param sourceIdUser the source user ID
   * @param targetIdUser the target user ID
   */
  private void copyUDFMetadataSecurity(Map<Integer, Integer> fieldMap, Integer sourceIdUser, Integer targetIdUser) {
    TypedQuery<UDFMetadataSecurity> q = em.createQuery("SELECT u FROM UDFMetadataSecurity u WHERE idUser = ?1",
        UDFMetadataSecurity.class);
    List<UDFMetadataSecurity> udfMgList = q.setParameter(1, sourceIdUser).getResultList();
    for (UDFMetadataSecurity u : udfMgList) {
      UDFMetadataSecurity uDFMetadataSecurity = new UDFMetadataSecurity(u.getCategoryTypes(),
          u.getSpecialInvestmentInstruments(), targetIdUser, u.getUdfSpecialTypeAsByte(), u.getDescription(),
          u.getDescriptionHelp(), u.getUdfDataType(), u.getFieldSize(), u.getUiOrder());
      em.persist(uDFMetadataSecurity);
      fieldMap.put(u.getIdUDFMetadata(), uDFMetadataSecurity.getIdUDFMetadata());
    }
    em.flush();
  }

  /**
   * Copies UDF data rows from the source user to the target user, remapping field ID references
   * in the JSON values map using {@code fieldMap}.
   *
   * @param fieldMap     map from source UDF metadata ID to target UDF metadata ID
   * @param sourceIdUser the source user ID
   * @param targetIdUser the target user ID
   */
  private void copyUDFData(Map<Integer, Integer> fieldMap, Integer sourceIdUser, Integer targetIdUser) {
    TypedQuery<UDFData> q = em.createQuery("SELECT u FROM UDFData u WHERE u.uDFDataKey.idUser = ?1", UDFData.class);
    List<UDFData> udfDataList = q.setParameter(1, sourceIdUser).getResultList();
    for (UDFData u : udfDataList) {
      Map<String, Object> jvNew = u.getJsonValues().entrySet().stream()
          .collect(Collectors.toMap(
              e -> BaseConstants.UDF_FIELD_PREFIX + fieldMap.get(Integer.parseInt(e.getKey().substring(1))),
              e -> e.getValue()));
      UDFData uDFData = new UDFData(
          new UDFDataKey(targetIdUser, u.getuDFDataKey().getEntity(), u.getuDFDataKey().getIdEntity()), jvNew);
      em.persist(uDFData);
    }
    em.flush();
  }

  /**
   * Copies all transactions from the source tenant, remapping tenant-specific FKs:
   * <ul>
   *   <li>{@code cashaccount} — remapped via {@code cashaccoutMap}</li>
   *   <li>{@code idSecurityaccount} — remapped via {@code securityaccountMap}</li>
   *   <li>{@code idStandingOrder} — remapped via {@code standingOrderMap} (set to null if not found)</li>
   *   <li>{@code connectedIdTransaction} — remapped using a two-pass algorithm: the first pass resolves
   *       backward references (connected transaction already copied), while forward references are deferred
   *       to the second pass</li>
   * </ul>
   * Global references ({@code security}, {@code idCurrencypair}) are left unchanged.
   *
   * @param sourceIdTenant    the source tenant ID
   * @param targetIdTenant    the target tenant ID
   * @param securityaccountMap mapping from source security account IDs to target Securityaccount entities
   * @param cashaccoutMap     mapping from source cash account IDs to target Cashaccount entities
   * @param standingOrderMap  mapping from source standing order IDs to target StandingOrder entities
   */
  private void copyTransaction(Integer sourceIdTenant, Integer targetIdTenant,
      Map<Integer, Securityaccount> securityaccountMap, Map<Integer, Cashaccount> cashaccoutMap,
      Map<Integer, StandingOrder> standingOrderMap) {
    Map<Integer, Transaction> transactionReMap = new HashMap<>();
    List<Transaction> newNotFinishedList = new ArrayList<>();
    Map<Integer, Integer> connectIdToIdMap = new HashMap<>();
    TypedQuery<Transaction> q = em
        .createQuery("SELECT t from Transaction t where t.idTenant = ?1 ORDER BY t.transactionTime", Transaction.class);
    List<Transaction> transactionList = q.setParameter(1, sourceIdTenant).getResultList();
    em.clear();
    for (Transaction transaction : transactionList) {
      Integer idSource = transaction.getId();
      transaction.setIdTenant(targetIdTenant);
      transaction.setIdTransaction(null);
      transaction.setCashaccount(cashaccoutMap.get(transaction.getCashaccount().getIdSecuritycashAccount()));
      if (transaction.getIdSecurityaccount() != null) {
        transaction.setIdSecurityaccount(
            securityaccountMap.get(transaction.getIdSecurityaccount()).getIdSecuritycashAccount());
      }

      if (transaction.getIdStandingOrder() != null) {
        StandingOrder mapped = standingOrderMap.get(transaction.getIdStandingOrder());
        transaction.setIdStandingOrder(mapped != null ? mapped.getIdStandingOrder() : null);
      }

      if (transaction.getConnectedIdTransaction() != null) {
        Transaction connectedConnection = transactionReMap.get(transaction.getConnectedIdTransaction());
        if (connectedConnection == null) {
          newNotFinishedList.add(transaction);
          transaction.setConnectedIdTransaction(null);
        } else {
          transaction.setConnectedIdTransaction(connectedConnection.getIdTransaction());
        }
      }

      em.persist(transaction);
      transactionReMap.put(idSource, transaction);
      if (transaction.getConnectedIdTransaction() != null) {
        connectIdToIdMap.put(transaction.getConnectedIdTransaction(), transaction.getIdTransaction());
      }
    }

    for (Transaction transaction : newNotFinishedList) {
      transaction.setConnectedIdTransaction(connectIdToIdMap.get(transaction.getIdTransaction()));
      em.persist(transaction);
    }
  }

  /**
   * Updates the target tenant's performance watchlist reference to point to the copied watchlist
   * that corresponds to the source tenant's performance watchlist.
   *
   * @param sourceIdTenant the source tenant ID
   * @param targetIdTenant the target tenant ID
   * @param watchlistMap   mapping from source watchlist IDs to target Watchlist entities
   */
  private void updateTenantReference(Integer sourceIdTenant, Integer targetIdTenant,
      Map<Integer, Watchlist> watchlistMap) {
    Tenant sourceTenant = em.find(Tenant.class, sourceIdTenant);
    Tenant targetTenant = em.find(Tenant.class, targetIdTenant);
    Watchlist watchlist = watchlistMap.get(sourceTenant.getIdWatchlistPerformance());
    if (watchlist != null) {
      targetTenant.setIdWatchlistPerformance(watchlist.getIdWatchlist());
      em.persist(targetTenant);
      em.flush();
    }
  }

  /**
   * Helper record for the deletion table list. Each entry specifies a table name and whether to filter
   * by {@code id_tenant} (true) or {@code id_user} (false).
   */
  private static class DelTab {
    public String tabName;
    public boolean useIdTenant;

    public DelTab(String tabName, boolean useIdTenant) {
      this.tabName = tabName;
      this.useIdTenant = useIdTenant;
    }

  }

}
