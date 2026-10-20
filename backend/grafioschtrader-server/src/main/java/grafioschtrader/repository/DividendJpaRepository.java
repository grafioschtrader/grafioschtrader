package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.Dividend;

public interface DividendJpaRepository extends JpaRepository<Dividend, Integer>, DividendJpaRepositoryCustom {
  long countByIdSecuritycurrency(Integer idSecuritycurrency);

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
   * Delivers the input of the total return adjustment ({@code TotalReturnAdjuster}) for several securities in one round
   * trip: every dividend together with the closes the reinvestment rule needs and the rate that converts the dividend
   * into the currency of the security. Both the dividend amount and the closes are split-adjusted, so no split
   * arithmetic is required by the caller. Rows of create type 1 (filled non-trading days) are skipped, as in the chart
   * queries.
   *
   * Named query: Dividend.getDividendsForTotalReturn
   * <ul>
   * <li>The previous close is the last one strictly before the ex-date. It is null when the ex-date lies before the
   * first quote; it also serves the plausibility check of the amount.</li>
   * <li>The ex-date close, at which the dividend is reinvested, is the close of the ex-date or, without a quote on that
   * day, the first one after it. When no history quote exists yet, the last price is taken if it is dated on or after
   * the ex-date, so a security going ex today is covered by the watchlist; otherwise it is null.</li>
   * <li>The exchange rate is 1 when the dividend is paid in the currency of the security. Otherwise it is the close of
   * the currency pair on the ex-date or the last one before it, inverted when only the reverse pair exists, and null
   * when no rate is known.</li>
   * </ul>
   *
   * @param idsSecuritycurrency ids of the securities whose dividends are loaded (?1)
   * @param untilDate           last ex-date included (?2); later calendar entries do not affect the price series
   * @return the dividends ordered by security id and ex-date ascending
   */
  @Query(nativeQuery = true)
  List<DividendTotalReturn> getDividendsForTotalReturn(List<Integer> idsSecuritycurrency, LocalDate untilDate);

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
    LocalDate getExDate();

    /** Pay‐date of the dividend */
    LocalDate getPayDate();

    /** Dividend amount per share */
    double getAmount();
  }

  /**
   * One dividend with the prices the total return adjustment needs.
   */
  public interface DividendTotalReturn {

    /** Security identifier */
    Integer getIdSecuritycurrency();

    /** Ex-date of the dividend */
    LocalDate getExDate();

    /** Split-adjusted dividend amount per share, in the dividend currency */
    double getAmountAdjusted();

    /** Dividend currency */
    String getCurrency();

    /** Last split-adjusted close before the ex-date, null when the ex-date precedes the first quote */
    Double getCloseBefore();

    /** Split-adjusted close of the ex-date or the first one after it, null when no price exists yet */
    Double getCloseExDate();

    /** Converts one unit of the dividend currency into the security currency, null when no rate is known */
    Double getExchangeRate();
  }

  /**
   * Dividend.findByIdsAndExDateRangeWithRate: securities (?1), inclusive ex-date range (?2/?3), FX at ex-date including
   * weekend fills.
   */
  @Query(nativeQuery = true)
  List<DividendTotalReturn> findByIdsAndExDateRangeWithRate(List<Integer> ids, LocalDate from, LocalDate to);

  /** Securities with any past dividend, including those whose last dividend lies outside the loaded window. */
  @Query(value = "SELECT DISTINCT id_securitycurrency FROM dividend WHERE id_securitycurrency IN (?1) AND ex_date <= ?2", nativeQuery = true)
  Set<Integer> findIdsWithDividendData(List<Integer> ids, LocalDate to);

}
