package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.query.Procedure;

import grafioschtrader.entities.TradingDaysMinus;
import grafioschtrader.entities.TradingDaysMinus.TradingDaysMinusKey;

public interface TradingDaysMinusJpaRepository
    extends JpaRepository<TradingDaysMinus, TradingDaysMinusKey>, TradingDaysMinusJpaRepositoryCustom {

  List<TradingDaysMinus> findByTradingDaysMinusKey_IdStockexchangeAndTradingDaysMinusKey_TradingDateMinusBetween(
      Integer idStockexchange, LocalDate fromDate, LocalDate toDate);

  @Procedure(procedureName = "copyTradingMinusToOtherStockexchange")
  void copyTradingMinusToOtherStockexchange(Integer idSource, Integer idTarget, LocalDate dateFrom, LocalDate dateTo);

  @Procedure(procedureName = "updCalendarStockexchangeByIndex")
  void updCalendarStockexchangeByIndex();

}
