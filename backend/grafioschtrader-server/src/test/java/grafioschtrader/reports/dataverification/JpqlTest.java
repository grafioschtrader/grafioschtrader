package grafioschtrader.reports.dataverification;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.test.start.GTforTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = GTforTest.class)
public class JpqlTest {

  @Autowired
  TransactionJpaRepository transactionJpaRepository;
  @Autowired
  PortfolioJpaRepository portfolioJpaRepository;

  @Test
  void findByIdPortfolioAndIdSecurityaccount() {
    System.out.println("---------------------------------------------------------------------------");
    Portfolio portfolio = portfolioJpaRepository.findById(9).get();
    List<Integer> sa = portfolio.getSecurityaccountList().stream().map(Securityaccount::getIdSecuritycashAccount)
        .collect(Collectors.toList());
    List<Transaction> transactions = transactionJpaRepository.findByIdPortfolioAndIdSecurity(sa, 1941);
    System.out.println("---------------------------------------------------------------------------");
    for (Transaction t : transactions) {
      if(t.getCashaccount() == null) {
        System.out.println(t);
      }
      
    }
    
  }
}
