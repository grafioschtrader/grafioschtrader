package grafioschtrader.repository;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.dto.TaskDataChangeFormConstraints;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.repository.EntityIdOptionsProvider;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Offers the stock exchanges whose trading calendar is derived from an index as a selectable list for the
 * {@link TaskTypeExtended#CREATE_STOCK_EXCHANGE_CALENDAR_BY_INDEX} task, so an administrator can create a calendar
 * rebuild task by choosing the trading venue by name instead of entering its numeric ID. Stock exchanges without a
 * calendar index are left out, because that task cannot process them. The rule based counterpart is served by
 * {@link TradingCalendarRuleSetTaskEntityIdOptionsProvider}.
 */
@Component
public class StockexchangeEntityIdOptionsProvider implements EntityIdOptionsProvider {

  @Autowired
  private StockexchangeJpaRepository stockexchangeJpaRepository;

  @Override
  public void addEntityIdOptions(TaskDataChangeFormConstraints constraints) {
    List<ValueKeyHtmlSelectOptions> stockexchangeOptions = stockexchangeJpaRepository.findAllByOrderByNameAsc().stream()
        .filter(stockexchange -> stockexchange.getIdIndexUpdCalendar() != null)
        .map(stockexchange -> new ValueKeyHtmlSelectOptions(String.valueOf(stockexchange.getIdStockexchange()),
            stockexchange.getName()))
        .collect(Collectors.toList());
    constraints.putTaskOptions(TaskTypeExtended.CREATE_STOCK_EXCHANGE_CALENDAR_BY_INDEX,
        Stockexchange.class.getSimpleName(), stockexchangeOptions);
  }
}
