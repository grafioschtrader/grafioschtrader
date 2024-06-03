package grafioschtrader.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.Dividend;

public interface DividendJpaRepository extends JpaRepository<Dividend, Integer>, DividendJpaRepositoryCustom {
  Long deleteByIdSecuritycurrencyAndCreateType(Integer idSecuritycurrency, byte createType);

  List<Dividend> findByIdSecuritycurrencyInAndCreateTypeOrderByIdSecuritycurrencyAscExDateAsc(List<Integer> securityIds,
      byte createType);

  List<Dividend> findByIdSecuritycurrencyOrderByExDateAsc(Integer idSecuritycurrency);

  List<Dividend> findByIdSecuritycurrencyAndCreateTypeOrderByExDateAsc(Integer idSecuritycurrency, byte createType);

  @Query(nativeQuery = true)
  List<DivdendForHoldings> getDivdendForSecurityHoldingByIdTenant(Integer idTenant);

  /**
   * Possible missing dividend income in the entity Dividend for securities. This
   * is based on the date of the last dividend payment and the periodicity of the
   * expected payments. In addition, the dividend payments of the transactions are
   * also taken into account if the dividend payment is more recent than the date
   * in the dividend entity.
   *
   * @param daysAdded
   * @param maxRetryDividend
   * @return
   */
  @Query(nativeQuery = true)
  List<Integer> getIdSecurityForPeriodicallyUpdate(Integer daysAdded, Short maxRetryDividend);

  /**
   * In GT, dividends should also be adjusted for splits like the price data. The
   * last split may be more recent than the most recent dividend. This query
   * provides the IDs of the securities for which this is the case.
   *
   * @param idsConnectorDividend
   * @param idsSecurity
   * @return
   */
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
