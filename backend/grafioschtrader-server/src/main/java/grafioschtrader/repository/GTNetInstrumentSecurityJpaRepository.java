package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import grafioschtrader.entities.GTNetInstrumentSecurity;

/**
 * Repository for GTNetInstrumentSecurity entities.
 *
 * Provides access to security instruments in the GTNet instrument pool, identified by ISIN and currency.
 * For price data operations, use this repository to find instruments and then query GTNetLastpriceJpaRepository
 * for the associated price data.
 *
 * @see GTNetInstrumentSecurityJpaRepositoryCustom for batch operations
 * @see GTNetLastpriceJpaRepository for price data
 */
public interface GTNetInstrumentSecurityJpaRepository
    extends JpaRepository<GTNetInstrumentSecurity, Integer>, GTNetInstrumentSecurityJpaRepositoryCustom {

  /**
   * Finds a security instrument by its ISIN and currency.
   *
   * @param isin the ISIN code (ISO 6166)
   * @param currency the currency code (ISO 4217)
   * @return the instrument if found
   */
  Optional<GTNetInstrumentSecurity> findByIsinAndCurrency(String isin, String currency);

  /**
   * Finds all local security instruments (those that exist in the local database).
   * Uses a JOIN to determine locality dynamically based on matching ISIN and currency.
   *
   * @return list of local security instruments
   */
  @Query(nativeQuery = true, value = """
      SELECT g.* FROM gt_net_instrument_security g
      JOIN security s ON g.isin = s.isin AND g.currency = s.currency""")
  List<GTNetInstrumentSecurity> findLocalInstruments();

  /**
   * Finds all foreign security instruments (those that do NOT exist in the local database).
   * Uses a LEFT JOIN to find instruments with no matching local security.
   *
   * @return list of foreign security instruments
   */
  @Query(nativeQuery = true, value = """
      SELECT g.* FROM gt_net_instrument_security g
      LEFT JOIN security s ON g.isin = s.isin AND g.currency = s.currency
      WHERE s.id_securitycurrency IS NULL""")
  List<GTNetInstrumentSecurity> findForeignInstruments();

  /**
   * Finds security instruments by a list of composite keys (ISIN|currency format).
   * Used for batch lookups when matching instruments from GTNet requests.
   *
   * @param keys list of composite keys in "ISIN|currency" format
   * @return list of matching security instruments
   */
  @Query("SELECT s FROM GTNetInstrumentSecurity s WHERE CONCAT(s.isin, '|', s.currency) IN :keys")
  List<GTNetInstrumentSecurity> findByIsinCurrencyKeys(@Param("keys") List<String> keys);

  /**
   * Determines which GTNet security instruments have matching local securities via JOIN.
   * Returns mapping from GTNet instrument ID to local security ID.
   *
   * Uses a native JOIN query to find local securities that match the ISIN and currency
   * of the given GTNet instruments. This allows dynamic determination of instrument locality
   * without storing the reference in the GTNetInstrument entity.
   *
   * @param instrumentIds list of GTNet instrument IDs to check for locality
   * @return list of Object[] where [0] = idGtNetInstrument (Integer), [1] = idSecuritycurrency (Integer)
   */
  @Query(nativeQuery = true, value = """
      SELECT g.id_gt_net_instrument, s.id_securitycurrency
      FROM gt_net_instrument_security g
      JOIN security s ON g.isin = s.isin AND g.currency = s.currency
      WHERE g.id_gt_net_instrument IN (:ids)""")
  List<Object[]> findLocalSecurityMappings(@Param("ids") List<Integer> instrumentIds);

}
