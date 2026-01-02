package grafioschtrader.repository;

import java.util.Date;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.GTNetExchange;

/**
 * Repository for managing GTNetExchange entities.
 *
 * Provides CRUD operations and custom methods for querying exchange configurations
 * for securities and currency pairs that participate in GTNet price data sharing.
 *
 * @see GTNetExchangeJpaRepositoryCustom for additional custom methods
 */
public interface GTNetExchangeJpaRepository
    extends JpaRepository<GTNetExchange, Integer>, GTNetExchangeJpaRepositoryCustom {

  java.util.Optional<GTNetExchange> findBySecuritycurrency_IdSecuritycurrency(Integer idSecuritycurrency);

  /**
   * Finds all GTNetExchange entries configured to receive intraday prices.
   *
   * @return list of exchanges with lastpriceRecv = true
   */
  List<GTNetExchange> findByLastpriceRecvTrue();

  /**
   * Finds all GTNetExchange entries configured to send intraday prices.
   *
   * @return list of exchanges with lastpriceSend = true
   */
  List<GTNetExchange> findByLastpriceSendTrue();

  /**
   * Finds all GTNetExchange entries configured to receive historical price data.
   *
   * @return list of exchanges with historicalRecv = true
   */
  List<GTNetExchange> findByHistoricalRecvTrue();

  /**
   * Finds all GTNetExchange entries configured to send historical price data.
   *
   * @return list of exchanges with historicalSend = true
   */
  List<GTNetExchange> findByHistoricalSendTrue();

  /**
   * Returns IDs of instruments configured to receive intraday prices via GTNet.
   * Used to filter watchlist instruments during price exchange.
   *
   * @return set of securitycurrency IDs with lastpriceRecv = true
   */
  @Query("SELECT ge.securitycurrency.idSecuritycurrency FROM GTNetExchange ge WHERE ge.lastpriceRecv = true")
  Set<Integer> findIdsWithLastpriceRecv();

  /**
   * Returns IDs of instruments configured to send intraday prices via GTNet.
   * Used by provider side to filter which prices to include in response.
   *
   * @return set of securitycurrency IDs with lastpriceSend = true
   */
  @Query("SELECT ge.securitycurrency.idSecuritycurrency FROM GTNetExchange ge WHERE ge.lastpriceSend = true")
  Set<Integer> findIdsWithLastpriceSend();

  /**
   * Finds all GTNetExchange entries modified after the given timestamp.
   * Used for incremental synchronization with GTNet peers to determine which
   * exchange configurations have changed since the last sync.
   *
   * @param timestamp the timestamp after which to find modified entries
   * @return list of GTNetExchange entries with lastModifiedTime after the given timestamp
   */
  List<GTNetExchange> findByLastModifiedTimeAfter(Date timestamp);

}
