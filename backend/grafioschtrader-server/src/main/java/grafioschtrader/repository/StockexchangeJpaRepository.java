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

import grafioschtrader.dto.StockexchangeHasSecurity;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface StockexchangeJpaRepository extends JpaRepository<Stockexchange, Integer>,
    StockexchangeJpaRepositoryCustom, UpdateCreateJpaRepository<Stockexchange> {

  List<Stockexchange> findAllByOrderByNameAsc();

  List<Stockexchange> findByNoMarketValueFalse();
  
  Optional<Stockexchange> findByIdStockexchangeAndNoMarketValueFalse(int idStockexchange);

  Stockexchange findByName(String name);

  @Query(value = "SELECT DISTINCT s.country_code FROM stockexchange s", nativeQuery = true)
  String[] findDistinctCountryCodes();

  @Query(nativeQuery = true)
  int stockexchangeHasSecurity(Integer idStockexchange);

  @Query(nativeQuery = true)
  List<StockexchangeHasSecurity> stockexchangesHasSecurity();

  @Query(nativeQuery = true)
  List<IdStockexchangeIndexName> getIdStockexchangeAndIndexNameForCalendarUpd();

  @Query(value = "UPDATE stockexchange SET last_direct_price_update = UTC_TIMESTAMP() WHERE no_market_value = 0", nativeQuery = true)
  void updateHistoricalUpdateWithNowForAll();
  
  
  @Query(value = "UPDATE stockexchange SET last_direct_price_update = UTC_TIMESTAMP() WHERE id_stockexchange IN (?1)", nativeQuery = true)
  void updateHistoricalUpdateWithNowByIdsStockexchange(List<Integer> ids);
  
  public interface IdStockexchangeIndexName {
    public Integer getIdStockexchange();

    public String getNameIndexSecurity();
  }
}
