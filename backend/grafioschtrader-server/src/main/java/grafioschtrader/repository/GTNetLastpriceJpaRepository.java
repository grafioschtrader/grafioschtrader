package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
   * Deletes all lastprice entries for a specific instrument.
   *
   * @param idGtNetInstrument the instrument ID
   */
  void deleteByGtNetInstrumentIdGtNetInstrument(Integer idGtNetInstrument);

}
