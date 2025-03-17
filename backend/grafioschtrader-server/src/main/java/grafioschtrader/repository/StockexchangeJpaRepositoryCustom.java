package grafioschtrader.repository;

import java.util.List;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.dto.StockexchangeBaseData;
import grafioschtrader.entities.Stockexchange;

public interface StockexchangeJpaRepositoryCustom extends BaseRepositoryCustom<Stockexchange> {
  List<Stockexchange> getAllStockExchanges(boolean includeNameOfCalendarIndex);

  StockexchangeBaseData getAllStockexchangesBaseData();
}
