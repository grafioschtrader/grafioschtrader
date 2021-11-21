package grafioschtrader.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.Dividend;

public interface DividendJpaRepository extends JpaRepository<Dividend, Integer>, DividendJpaRepositoryCustom {
  Long deleteByIdSecuritycurrencyAndCreateType(Integer idSecuritycurrency, byte createType);

  List<Dividend> findByIdSecuritycurrencyInOrderByIdSecuritycurrencyAscExDateAsc(List<Integer> securityIds);

  List<Dividend> findByIdSecuritycurrencyOrderByExDateAsc(Integer idSecuritycurrency);

  List<Dividend> findByIdSecuritycurrencyAndCreateTypeOrderByExDateAsc(Integer idSecuritycurrency, byte createType);

  @Query(nativeQuery = true)
  List<DivdendForHoldings> getDivdendForSecurityHoldingByIdTenant(Integer idTenant);

  @Query(nativeQuery = true)
  List<Integer> getIdSecurityForPeriodicallyUpdate(Integer daysAdded, Short maxRetryDividend);

  @Query(nativeQuery = true)
  List<Integer> getIdSecuritySplitAfterDividendWhenAdjusted(List<String> idsConnectorDividend,
      List<Integer> idsSecurity);

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
