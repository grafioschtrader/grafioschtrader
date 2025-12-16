package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

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

}
