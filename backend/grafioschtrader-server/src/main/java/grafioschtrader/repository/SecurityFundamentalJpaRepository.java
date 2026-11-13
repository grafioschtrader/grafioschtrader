package grafioschtrader.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.SecurityFundamental;
import grafioschtrader.entities.SecurityFundamental.SecurityFundamentalKey;

/**
 * Fundamental base values per security, metric and source. The weekly load task writes them; the watchlist indicator
 * report reads only the selected ones.
 */
public interface SecurityFundamentalJpaRepository extends JpaRepository<SecurityFundamental, SecurityFundamentalKey> {

  List<SecurityFundamental> findByIdSecuritycurrency(Integer idSecuritycurrency);

  /** Last effective split, used to avoid combining a pre-split SEC stock with a post-split quote. */
  @Query(value = "SELECT MAX(split_date) FROM securitysplit WHERE id_securitycurrency = ?1 AND split_date <= ?2", nativeQuery = true)
  LocalDate findLatestSplitDate(Integer idSecuritycurrency, LocalDate asOfDate);

  /**
   * Removes every fundamental row of a security, used when a change of the security invalidates them: its currency,
   * ISIN, the extension of its Yahoo or Finnhub connector or its Yahoo symbol.
   */
  @Transactional
  @Modifying
  @Query(value = "DELETE FROM security_fundamental WHERE id_securitycurrency = ?1", nativeQuery = true)
  int deleteByIdSecuritycurrency(Integer idSecuritycurrency);

  /**
   * Loads the rows that bind an expression variable for several securities in one round trip. The freshness and the
   * split rule of the selection are applied again with the date of the request, so that values expire on schedule even
   * when the load task is disabled or keeps failing.
   *
   * Named query: SecurityFundamental.findSelectedByIds
   * <ul>
   * <li>Only rows in state CONFIRMED, CHECKED or SINGLE (1, 2, 3) of the seven bound metrics (1–7).</li>
   * <li>A dated row counts while its period ended at most 18 months before {@code asOfDate}, an undated row while it
   * was fetched at most 21 days before.</li>
   * <li>SHARES is a stock, not an EPS denominator: only SEC (4) and Yahoo (1), fetched in the last 21 days. A dated
   * stock must be at most six months old; a SEC stock must state its measurement date.</li>
   * <li>Every row of a security is skipped that was fetched on or before the day of a split of that security: the new
   * price would otherwise be combined with per-share values of the old share count. A SEC stock is also skipped for a
   * split on or after its measurement date, even if the report was downloaded later. Future splits do not count.</li>
   * <li>{@code exchangeRate} turns the value into the currency of the security: 1 for the share count and for a value
   * in that currency, otherwise the newest close of the currency pair up to {@code asOfDate}, inverted when only the
   * reverse pair exists, and null when no pair or no currency is known.</li>
   * </ul>
   *
   * @param idsSecuritycurrency the securities (?1)
   * @param asOfDate            the day of the request (?2)
   * @return the selected rows with the rate into the currency of their security
   */
  @Query(nativeQuery = true)
  List<SelectedFundamental> findSelectedByIds(List<Integer> idsSecuritycurrency, LocalDate asOfDate);

  /**
   * Loads Yahoo's market capitalisation, stored as evidence for the check of the share count, for several securities in
   * one round trip. The watchlist market map falls back to it when no share count is selected.
   *
   * Named query: SecurityFundamental.findMarketCapChecks
   * <ul>
   * <li>Only rows of metric MCAP_CHECK (9) and source YAHOO (1) that state a currency.</li>
   * <li>A row counts while it was fetched at most 21 days before {@code asOfDate}.</li>
   * <li>A row fetched on or before the day of a split of its security is skipped.</li>
   * </ul>
   *
   * @param idsSecuritycurrency the securities (?1), must not be empty
   * @param asOfDate            the day of the request (?2)
   * @return per security at most one row, in full units of the stated currency
   */
  @Query(nativeQuery = true)
  List<MarketCapCheck> findMarketCapChecks(List<Integer> idsSecuritycurrency, LocalDate asOfDate);

  /**
   * Ids of the securities the weekly load covers: active direct equities in a watchlist of any tenant or in a
   * transaction, the universe of the fundamental source probe.
   *
   * Named query: SecurityFundamental.findScopeIds
   *
   * @param categoryType         byte value of {@code AssetclassType.EQUITIES} (?1)
   * @param specInvestInstrument byte value of {@code SpecialInvestmentInstruments.DIRECT_INVESTMENT} (?2)
   * @param activeOnDate         the security must be active on this day (?3)
   * @return the ids in ascending order
   */
  @Query(nativeQuery = true)
  List<Integer> findScopeIds(byte categoryType, byte specInvestInstrument, LocalDate activeOnDate);

  /**
   * Removes the rows of every security that no longer belongs to the load scope of {@link #findScopeIds}, for example
   * because it became inactive or moved to another asset class.
   *
   * Named query: SecurityFundamental.deleteOutOfScope; parameters as {@link #findScopeIds}.
   */
  @Transactional
  @Modifying
  @Query(nativeQuery = true)
  int deleteOutOfScope(byte categoryType, byte specInvestInstrument, LocalDate activeOnDate);

  /** Yahoo's market capitalisation of a security in the currency it was stated in. */
  interface MarketCapCheck {
    Integer getIdSecuritycurrency();

    Double getValue();

    String getCurrency();
  }

  /** A selected fundamental row with the rate into the currency of its security. */
  interface SelectedFundamental {
    Integer getIdSecuritycurrency();

    Integer getMetric();

    Integer getSource();

    Double getValue();

    String getCurrency();

    LocalDate getPeriodEnd();

    LocalDateTime getFetchedTime();

    Integer getState();

    Double getExchangeRate();
  }
}
