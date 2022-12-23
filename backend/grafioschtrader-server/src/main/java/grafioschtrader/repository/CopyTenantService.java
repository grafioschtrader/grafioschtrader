package grafioschtrader.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitycashaccount;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
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

  public void copyTenant(Integer sourceIdTenant, Integer targetIdTenant) {
    deleteData(targetIdTenant);
    Map<Integer, Portfolio> portfolioMap = copyPortfolio(sourceIdTenant, targetIdTenant);
    Map<Integer, Securityaccount> securityaccountMap = copySecurityAccount(sourceIdTenant, targetIdTenant,
        portfolioMap);
    Map<Integer, Cashaccount> cashaccoutMap = copyCashaccount(sourceIdTenant, targetIdTenant, portfolioMap,
        securityaccountMap);
    Map<Integer, Watchlist> watchlistMap = copyWatchlist(sourceIdTenant, targetIdTenant);
    copyTransaction(sourceIdTenant, targetIdTenant, securityaccountMap, cashaccoutMap);
    copyCorrelationSet(sourceIdTenant, targetIdTenant);
    updateTenantReference(sourceIdTenant, targetIdTenant, watchlistMap);
  }

  private void deleteData(Integer targetIdTenant) {
    String[] tables = new String[] { Transaction.TABNAME, Watchlist.TABNAME, Securitycashaccount.TABNAME,
        Portfolio.TABNAME, CorrelationSet.TABNAME };
    for (String table : tables) {
      String deleteSQL = "DELETE FROM " + table + " WHERE id_tenant=?";
      jdbcTemplate.update(deleteSQL, targetIdTenant);
    }
  }

  private Map<Integer, Portfolio> copyPortfolio(Integer sourceIdTenant, Integer targetIdTenant) {
    Map<Integer, Portfolio> portfolioMap = new HashMap<>();
    TypedQuery<Portfolio> q = em.createQuery("SELECT p from Portfolio p where p.idTenant = ?1", Portfolio.class);
    List<Portfolio> portfolios = q.setParameter(1, sourceIdTenant).getResultList();
    em.clear();
    for (Portfolio portfolio : portfolios) {
      Integer idPortfolio = portfolio.getId();
      portfolio.setIdTenant(targetIdTenant);
      portfolio.setIdPortfolio(null);
      portfolio.setSecuritycashaccountList(new ArrayList<Securitycashaccount>());
      em.persist(portfolio);
      portfolioMap.put(idPortfolio, portfolio);
    }
    em.flush();
    return portfolioMap;
  }

  private Map<Integer, Securityaccount> copySecurityAccount(Integer sourceIdTenant, Integer targetIdTenant,
      Map<Integer, Portfolio> portfolioMap) {
    Map<Integer, Securityaccount> securityAccountMap = new HashMap<>();
    TypedQuery<Securityaccount> q = em.createQuery("SELECT c from Securityaccount c where c.idTenant = ?1",
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
    TypedQuery<Cashaccount> q = em.createQuery("SELECT c from Cashaccount c where c.idTenant = ?1", Cashaccount.class);
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
    TypedQuery<Watchlist> q = em.createQuery("SELECT c from Watchlist c where c.idTenant = ?1", Watchlist.class);
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
    TypedQuery<CorrelationSet> q = em.createQuery("SELECT c from CorrelationSet c where c.idTenant = ?1", CorrelationSet.class);
    List<CorrelationSet> correlationSetList = q.setParameter(1, sourceIdTenant).getResultList();
    for (CorrelationSet cs : correlationSetList) {
      CorrelationSet correlationSetNew = new CorrelationSet(targetIdTenant, cs.getName(), null, cs.getDateFrom(), cs.getDateTo(),
          cs.getSamplingPeriod().getValue(), cs.getRolling(), cs.isAdjustCurrency());
      List<Securitycurrency<?>> securities = new ArrayList<>();
      securities.addAll(cs.getSecuritycurrencyList());
      correlationSetNew.setSecuritycurrencyList(securities);
      em.persist(correlationSetNew);
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

}
