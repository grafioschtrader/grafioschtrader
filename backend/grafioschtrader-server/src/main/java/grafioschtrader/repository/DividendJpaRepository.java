package grafioschtrader.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.Dividend;

public interface DividendJpaRepository extends JpaRepository<Dividend, Integer>{
  Long deleteByIdSecuritycurrencyAndCreateType(Integer idSecuritycurrency, byte createType);
  
  List<Dividend> findByIdSecuritycurrencyOrderByExDateAsc(Integer idSecuritycurrency);
  
  @Query(nativeQuery = true)
  List <DivdendForHoldings> getDivdendForSecurityHoldingByIdTenant(Integer idTenant);
  
  
  interface DivdendForHoldings {
    int getIdPortfolio();
    String getCurrency();
    int getIdSecurityaccount();
    int getIdSecuritycurrency();
    double getHoldings();
    Date getExDate();
    Date getPayDate();
    double getAmount();
  }
}
