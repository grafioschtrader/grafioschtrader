package grafioschtrader.task.exec.unofficial;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.DividendJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.repository.DividendJpaRepository.DivdendForHoldings;
import grafioschtrader.task.ITask;
import grafioschtrader.types.Language;
import grafioschtrader.types.TaskType;
import grafioschtrader.types.TransactionType;

@Component
public class TransferDividendToTransactionTest implements ITask {

  @Autowired
  DividendJpaRepository dividendJpaRepository;

  @Autowired
  TransactionJpaRepository transactionJpaRepository;

  @Autowired
  SecurityJpaRepository securityJpaRepository;

  @Autowired
  PortfolioJpaRepository portfolioJpaRepository;

  @Autowired
  HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;

  @Override
  public TaskType getTaskType() {
    return TaskType.UNOFFICIAL_CREATE_TRANSACTION_FROM_DIVIDENDS_TABLE;
  }

  @Override
  @Transactional
  public void doWork(Integer idTenant, String entity) {

    transactionJpaRepository.removeSystemCreatedDividensFromTenant(idTenant);
    List<DivdendForHoldings> dfhList = dividendJpaRepository.getDivdendForSecurityHoldingByIdTenant(idTenant);

    Map<Integer, Map<Integer, List<DivdendForHoldings>>> dividendMap = dfhList.stream()
        .collect(Collectors.groupingBy(DivdendForHoldings::getIdPortfolio,
            Collectors.groupingBy(DivdendForHoldings::getIdSecurityaccount, Collectors.toList())));

    holdSecurityaccountSecurityJpaRepository.createSecurityHoldingsEntireByTenant(idTenant);

    for (Integer idPortfolio : dividendMap.keySet()) {
      Portfolio portfolio = portfolioJpaRepository.getOne(idPortfolio);
      Map<Integer, List<DivdendForHoldings>> securityaccountsMap = dividendMap.get(idPortfolio);
      for (Integer idSecurityaccount : securityaccountsMap.keySet()) {
        List<DivdendForHoldings> dividendsForHoldingsList = securityaccountsMap.get(idSecurityaccount);
        createTransactionsByDividend(idTenant, portfolio, idSecurityaccount, dividendsForHoldingsList);
      }
    }

  }

  private void createTransactionsByDividend(Integer idTenant, Portfolio portfolio, Integer idSecurityaccount,
      List<DivdendForHoldings> dfhList) {
    Integer lastIdSecurity = null;
    Security security = null;
    for (DivdendForHoldings dfh : dfhList) {
      if (lastIdSecurity == null || !lastIdSecurity.equals(dfh.getIdSecuritycurrency())) {
        security = securityJpaRepository.getOne(dfh.getIdSecuritycurrency());
        lastIdSecurity = dfh.getIdSecuritycurrency();
      }
      Cashaccount cashaccount = portfolio.getCashaccountList().stream()
          .filter(c -> c.getCurrency().equals(dfh.getCurrency())).findFirst().get();

      String subCategory = security.getAssetClass().getSubCategoryByLanguage(Language.GERMAN);
      double cashaccountAmount = dfh.getHoldings() * dfh.getAmount();
      double taxCost = subCategory.contains("Schweiz") ? DataHelper.round(cashaccountAmount * 0.35) : 0;
      cashaccountAmount -= taxCost;
      Date transactionTime = dfh.getPayDate() == null ? DateHelper.setTimeToZeroAndAddDay(dfh.getExDate(), 28)
          : new Date(dfh.getPayDate().getTime());

      Transaction transaction = new Transaction(idSecurityaccount, cashaccount, security, cashaccountAmount,
          dfh.getHoldings(), dfh.getAmount(), TransactionType.DIVIDEND, taxCost, null, null, transactionTime, null,
          null, new Date(dfh.getExDate().getTime()));
      transaction.setNote("System-Created");
      transaction.setTaxableInterest(true);
      transaction.setIdTenant(idTenant);
      transactionJpaRepository.save(transaction);

    }

  }

}
