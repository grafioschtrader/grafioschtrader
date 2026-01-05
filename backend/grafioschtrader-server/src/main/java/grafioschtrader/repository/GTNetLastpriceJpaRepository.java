package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.GTNetLastprice;

/**
 * Repository for GTNetLastprice entities.
 *
 * Provides access to intraday price data for instruments in the GTNet pool.
 * Each lastprice entry is linked to a GTNetInstrument via foreign key.
 */
public interface GTNetLastpriceJpaRepository extends JpaRepository<GTNetLastprice, Integer> {

  /**
   * Finds a lastprice entry by its instrument ID.
   *
   * @param idGtNetInstrument the instrument ID
   * @return the lastprice if found
   */
  Optional<GTNetLastprice> findByGtNetInstrumentIdGtNetInstrument(Integer idGtNetInstrument);

  /**
   * Finds all lastprice entries for a list of instrument IDs.
   * Useful for batch operations where multiple instruments need price data.
   *
   * @param instrumentIds list of instrument IDs
   * @return list of lastprice entries
   */
  List<GTNetLastprice> findByGtNetInstrumentIdGtNetInstrumentIn(List<Integer> instrumentIds);

  /**
   * Finds all lastprice entries for a specific GTNet server.
   * Joins through the instrument table to filter by server ID.
   *
   * @param idGtNet the GTNet server ID
   * @return list of lastprice entries
   */
  @Query("SELECT lp FROM GTNetLastprice lp WHERE lp.gtNetInstrument.idGtNet = ?1")
  List<GTNetLastprice> findByIdGtNet(Integer idGtNet);

  /**
   * Finds all lastprice entries for security instruments of a specific GTNet server.
   *
   * @param idGtNet the GTNet server ID
   * @return list of lastprice entries for securities
   */
  @Query("""
      SELECT lp FROM GTNetLastprice lp
      JOIN GTNetInstrumentSecurity s ON lp.gtNetInstrument.idGtNetInstrument = s.idGtNetInstrument
      WHERE lp.gtNetInstrument.idGtNet = ?1""")
  List<GTNetLastprice> findSecurityLastpricesByIdGtNet(Integer idGtNet);

  /**
   * Finds all lastprice entries for currency pair instruments of a specific GTNet server.
   *
   * @param idGtNet the GTNet server ID
   * @return list of lastprice entries for currency pairs
   */
  @Query("""
      SELECT lp FROM GTNetLastprice lp
      JOIN GTNetInstrumentCurrencypair c ON lp.gtNetInstrument.idGtNetInstrument = c.idGtNetInstrument
      WHERE lp.gtNetInstrument.idGtNet = ?1""")
  List<GTNetLastprice> findCurrencypairLastpricesByIdGtNet(Integer idGtNet);

  /**
   * Deletes all lastprice entries for a specific instrument.
   *
   * @param idGtNetInstrument the instrument ID
   */
  void deleteByGtNetInstrumentIdGtNetInstrument(Integer idGtNetInstrument);

}
