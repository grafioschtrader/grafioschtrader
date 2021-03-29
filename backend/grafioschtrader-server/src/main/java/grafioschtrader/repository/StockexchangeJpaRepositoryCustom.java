package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.entities.Stockexchange;

public interface StockexchangeJpaRepositoryCustom extends BaseRepositoryCustom<Stockexchange> {
  List<Stockexchange> getAllStockExchanges(boolean includeNameOfCalendarIndex);
}
