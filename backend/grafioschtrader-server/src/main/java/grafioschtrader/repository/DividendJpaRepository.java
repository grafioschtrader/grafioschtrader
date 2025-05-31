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

  /**
   * Retrieves dividend details for all security holdings of the given tenant.
   *
   * @param idTenant the tenant ID whose security holdings’ dividends are fetched
   * @return a list of {@link DivdendForHoldings} projections containing portfolio, account, security, holding, and
   *         dividend info
   */
  @Query(nativeQuery = true)
  List<DivdendForHoldings> getDivdendForSecurityHoldingByIdTenant(Integer idTenant);

  /**
   * Possible missing dividend income in the entity Dividend for securities. This is based on the date of the last
   * dividend payment and the periodicity of the expected payments. In addition, the dividend payments of the
   * transactions are also taken into account if the dividend payment is more recent than the date in the dividend
   * entity.
   *
   * @param daysAdded        additional days to account for in the dividend frequency interval
   * @param maxRetryDividend maximum allowed retry count for dividend loading
   * @return a list of security IDs requiring a periodic dividend update
   */
  @Query(nativeQuery = true)
  List<Integer> getIdSecurityForPeriodicallyUpdate(Integer daysAdded, Short maxRetryDividend);

  /**
   * Identifies securities where a split occurred on or after the latest dividend ex‐date, indicating an adjustment is
   * needed. In GT, dividends should also be adjusted for splits like the price data. The last split may be more recent
   * than the most recent dividend. This query provides the IDs of the securities for which this is the case.
   *
   * @param idsConnectorDividend list of connector‐dividend identifiers to filter by
   * @param idsSecurity          list of security IDs to consider
   * @return a list of security IDs whose recent splits affect dividend history
   */
  @Query(nativeQuery = true)
  List<Integer> getIdSecuritySplitAfterDividendWhenAdjusted(List<String> idsConnectorDividend,
      List<Integer> idsSecurity);

  /**
   * Provides dividend details for a tenant’s security holdings.
   */
  public interface DivdendForHoldings {

    /** Portfolio identifier */
    int getIdPortfolio();

    /** Dividend currency */
    String getCurrency();

    /** Security account identifier */
    int getIdSecurityaccount();

    /** Security identifier */
    int getIdSecuritycurrency();

    /** Number of shares held */
    double getHoldings();

    /** Ex‐date of the dividend */
    Date getExDate();

    /** Pay‐date of the dividend */
    Date getPayDate();

    /** Dividend amount per share */
    double getAmount();
  }
}
