package grafioschtrader.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitycashaccount;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.Watchlist;

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
    updateTenantReference(sourceIdTenant, targetIdTenant, watchlistMap);
  }

  private void deleteData(Integer targetIdTenant) {
    String[] tables = new String[] { Transaction.TABNAME, Watchlist.TABNAME, Securitycashaccount.TABNAME,
        Portfolio.TABNAME };
    for (int i = 0; i < tables.length; i++) {
      String deleteSQL = "DELETE FROM " + tables[i] + " WHERE id_tenant=?";
      jdbcTemplate.update(deleteSQL, targetIdTenant);
    }

  }

  private Map<Integer, Portfolio> copyPortfolio(Integer sourceIdTenant, Integer targetIdTenant) {
    Map<Integer, Portfolio> portfolioMap = new HashMap<>();
    List<Portfolio> portfolios = em.createQuery("SELECT p from Portfolio p where p.idTenant = ?1")
        .setParameter(1, sourceIdTenant).getResultList();
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
    List<Securityaccount> securityaccounts = em.createQuery("SELECT c from Securityaccount c where c.idTenant = ?1")
        .setParameter(1, sourceIdTenant).getResultList();
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
    List<Cashaccount> cashaccounts = em.createQuery("SELECT c from Cashaccount c where c.idTenant = ?1")
        .setParameter(1, sourceIdTenant).getResultList();
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

    List<Watchlist> watchlists = em.createQuery("SELECT c from Watchlist c where c.idTenant = ?1")
        .setParameter(1, sourceIdTenant).getResultList();
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

  private void copyTransaction(Integer sourceIdTenant, Integer targetIdTenant,
      Map<Integer, Securityaccount> securityaccountMap, Map<Integer, Cashaccount> cashaccoutMap) {
    Map<Integer, Transaction> transactionMap = new HashMap<>();
    Map<Integer, Transaction> newNotFinishedMap = new HashMap<>();
    List<Transaction> transactionList = em.createQuery("SELECT t from Transaction t where t.idTenant = ?1")
        .setParameter(1, sourceIdTenant).getResultList();
    em.clear();
    for (Transaction transaction : transactionList) {

      Integer id = transaction.getId();
      transaction.setIdTenant(targetIdTenant);
      transaction.setIdTransaction(null);
      transaction.setCashaccount(cashaccoutMap.get(transaction.getCashaccount().getIdSecuritycashAccount()));
      if (transaction.getIdSecurityaccount() != null) {
        transaction.setIdSecurityaccount(
            securityaccountMap.get(transaction.getIdSecurityaccount()).getIdSecuritycashAccount());
      }
      if (transaction.getConnectedIdTransaction() != null) {
        Transaction connectedConnection = transactionMap.get(transaction.getConnectedIdTransaction());
        if (connectedConnection == null) {
          newNotFinishedMap.put(id, transaction);
          transaction.setConnectedIdTransaction(null);
        } else {
          transaction.setConnectedIdTransaction(connectedConnection.getIdTransaction());
        }
      }

      em.persist(transaction);
      transactionMap.put(id, transaction);
    }

    for (Map.Entry<Integer, Transaction> entry : newNotFinishedMap.entrySet()) {
      Transaction transaction = entry.getValue();
      transaction.setConnectedIdTransaction(transactionMap.get(entry.getKey()).getIdTransaction());
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
