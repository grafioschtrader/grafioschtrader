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
   * Finds a security instrument by GTNet server ID, ISIN and currency.
   *
   * @param idGtNet the GTNet server ID
   * @param isin the ISIN code
   * @param currency the currency code
   * @return the instrument if found
   */
  Optional<GTNetInstrumentSecurity> findByIdGtNetAndIsinAndCurrency(Integer idGtNet, String isin, String currency);

  /**
   * Finds all security instruments for a specific GTNet server.
   *
   * @param idGtNet the GTNet server ID
   * @return list of security instruments
   */
  List<GTNetInstrumentSecurity> findByIdGtNet(Integer idGtNet);

  /**
   * Finds all local security instruments (those that exist in the local database).
   *
   * @param idGtNet the GTNet server ID
   * @return list of local security instruments
   */
  @Query("SELECT s FROM GTNetInstrumentSecurity s WHERE s.idGtNet = ?1 AND s.idSecuritycurrency IS NOT NULL")
  List<GTNetInstrumentSecurity> findLocalInstrumentsByIdGtNet(Integer idGtNet);

  /**
   * Finds all foreign security instruments (those that do NOT exist in the local database).
   *
   * @param idGtNet the GTNet server ID
   * @return list of foreign security instruments
   */
  @Query("SELECT s FROM GTNetInstrumentSecurity s WHERE s.idGtNet = ?1 AND s.idSecuritycurrency IS NULL")
  List<GTNetInstrumentSecurity> findForeignInstrumentsByIdGtNet(Integer idGtNet);

  /**
   * Finds security instruments by a list of composite keys (ISIN|currency format).
   * Used for batch lookups when matching instruments from GTNet requests.
   *
   * @param keys list of composite keys in "ISIN|currency" format
   * @return list of matching security instruments
   */
  @Query("SELECT s FROM GTNetInstrumentSecurity s WHERE CONCAT(s.isin, '|', s.currency) IN :keys")
  List<GTNetInstrumentSecurity> findByIsinCurrencyKeys(@Param("keys") List<String> keys);

}
