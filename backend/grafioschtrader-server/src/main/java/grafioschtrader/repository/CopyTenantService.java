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
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitycashaccount;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.entities.Watchlist;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

/**
 * Copy a tenant to another tenant.
 */
@Service
@Transactional
public class CopyTenantService {

  @PersistenceContext
  private EntityManager em;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  public void copyTenant(User sourceUser, User targetUser) {
    deleteData(targetUser.getIdTenant(), targetUser.getIdUser());
    Map<Integer, Portfolio> portfolioMap = copyPortfolio(sourceUser.getIdTenant(), targetUser.getIdTenant());
    Map<Integer, Securityaccount> securityaccountMap = copySecurityAccount(sourceUser.getIdTenant(),
        targetUser.getIdTenant(), portfolioMap);
    Map<Integer, Cashaccount> cashaccoutMap = copyCashaccount(sourceUser.getIdTenant(), targetUser.getIdTenant(),
        portfolioMap, securityaccountMap);
    Map<Integer, Watchlist> watchlistMap = copyWatchlist(sourceUser.getIdTenant(), targetUser.getIdTenant());
    copyTransaction(sourceUser.getIdTenant(), targetUser.getIdTenant(), securityaccountMap, cashaccoutMap);
    copyCorrelationSet(sourceUser.getIdTenant(), targetUser.getIdTenant());
    Map<Integer, Integer> fieldMap = copyUDFMetadataGeneral(sourceUser.getIdUser(), targetUser.getIdUser());
    copyUDFMetadataSecurity(fieldMap, sourceUser.getIdUser(), targetUser.getIdUser());
    copyUDFData(fieldMap, sourceUser.getIdUser(), targetUser.getIdUser());
    updateTenantReference(sourceUser.getIdTenant(), targetUser.getIdTenant(), watchlistMap);
  }

  private void deleteData(Integer targetIdTenant, Integer targetIdUser) {
    DelTab[] tables = new DelTab[] { new DelTab(ImportTransactionPos.TABNAME, true),
        new DelTab(ImportTransactionHead.TABNAME, true), new DelTab(Transaction.TABNAME, true),
        new DelTab(Watchlist.TABNAME, true), new DelTab(Securitycashaccount.TABNAME, true),
        new DelTab(Portfolio.TABNAME, true), new DelTab(CorrelationSet.TABNAME, true),
        new DelTab(UDFData.TABNAME, false), new DelTab(UDFMetadata.TABNAME, false) };
    for (DelTab delTab : tables) {
      String deleteSQL = "DELETE FROM " + delTab.tabName + " WHERE " + (delTab.useIdTenant ? "id_tenant" : "id_user")
          + "=?";
      jdbcTemplate.update(deleteSQL, delTab.useIdTenant ? targetIdTenant : targetIdUser);
    }
  }

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
      em.persist(securityaccount);
      securityAccountMap.put(idSecurityaccount, securityaccount);
    }
    em.flush();
    return securityAccountMap;
  }

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

  private void copyTransaction(Integer sourceIdTenant, Integer targetIdTenant,
      Map<Integer, Securityaccount> securityaccountMap, Map<Integer, Cashaccount> cashaccoutMap) {
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

  private static class DelTab {
    public String tabName;
    public boolean useIdTenant;

    public DelTab(String tabName, boolean useIdTenant) {
      this.tabName = tabName;
      this.useIdTenant = useIdTenant;
    }

  }

}
