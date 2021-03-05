package grafioschtrader.repository;

import grafioschtrader.dto.CopyTradingDaysFromSourceToTarget;
import grafioschtrader.dto.TradingDaysWithDateBoundaries;

public interface TradingDaysMinusJpaRepositoryCustom extends TradingDaysBase {
  TradingDaysWithDateBoundaries getTradingDaysByIdStockexchangeAndYear(int idStockexchange, int year);

  TradingDaysWithDateBoundaries save(int idStockexchange, SaveTradingDays saveTradingDays);

  TradingDaysWithDateBoundaries copyTradingDaysMinusToOtherStockexchange(
      CopyTradingDaysFromSourceToTarget copyTradingDaysFromSourceToTarget, boolean excludeCheck);
}
