/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.common.UpdateQuery;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.dto.StockexchangeHasSecurity;
import grafioschtrader.entities.Stockexchange;

public interface StockexchangeJpaRepository extends JpaRepository<Stockexchange, Integer>,
    StockexchangeJpaRepositoryCustom, UpdateCreateJpaRepository<Stockexchange> {

  List<Stockexchange> findAllByOrderByNameAsc();

  List<Stockexchange> findByNoMarketValueFalse();

  Optional<Stockexchange> findByIdStockexchangeAndNoMarketValueFalse(int idStockexchange);

  Stockexchange findByName(String name);

  @Query(value = "SELECT DISTINCT s.country_code FROM stockexchange s", nativeQuery = true)
  String[] findDistinctCountryCodes();

  @UpdateQuery(value = "UPDATE stockexchange SET last_direct_price_update = UTC_TIMESTAMP() WHERE no_market_value = 0", nativeQuery = true)
  void updateHistoricalUpdateWithNowForAll();

  @UpdateQuery(value = "UPDATE stockexchange SET last_direct_price_update = UTC_TIMESTAMP() WHERE id_stockexchange IN (?1)", nativeQuery = true)
  void updateHistoricalUpdateWithNowByIdsStockexchange(List<Integer> ids);

  /**
   * Fetches stock exchange IDs along with the names of their calendar‐update index securities.
   *
   * @return a list of {@link IdStockexchangeIndexName} projections
   */
  @Query(nativeQuery = true)
  List<IdStockexchangeIndexName> getIdStockexchangeAndIndexNameForCalendarUpd();

  /**
   * Checks whether a specific stock exchange has any associated securities.
   *
   * @param idStockexchange the ID of the stock exchange to check
   * @return {@code 1} if at least one security exists; {@code 0} otherwise
   */
  @Query(nativeQuery = true)
  int stockexchangeHasSecurity(Integer idStockexchange);

  /**
   * Retrieves all stock exchanges with a flag indicating whether they have any securities.
   *
   * @return a list of {@link StockexchangeHasSecurity} projections
   */
  @Query(nativeQuery = true)
  List<StockexchangeHasSecurity> stockexchangesHasSecurity();

  /**
   * Projection interface for retrieving a stock exchange’s ID and the name of its calendar‐update index security.
   */
  public interface IdStockexchangeIndexName {

    /**
     * Gets the unique identifier of the stock exchange.
     *
     * @return the stock exchange ID as an {@link Integer}
     */
    Integer getIdStockexchange();

    /**
     * Gets the name of the index security used for calendar updates.
     *
     * @return the index security’s name as a {@link String}
     */
    String getNameIndexSecurity();
  }
}
