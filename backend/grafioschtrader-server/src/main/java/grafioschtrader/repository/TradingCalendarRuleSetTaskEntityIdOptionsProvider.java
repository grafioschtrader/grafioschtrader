package grafioschtrader.repository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.dto.TaskDataChangeFormConstraints;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.repository.EntityIdOptionsProvider;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.TradingCalendarRuleSet;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Supplies the selectable ID options for the {@link TaskTypeExtended#CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET} task.
 * It offers two entity lists so an administrator can choose by name instead of typing a numeric ID:
 * <ul>
 * <li>{@code Stockexchange} &rarr; only the rule based exchanges ({@code id_trading_calendar_rule_set} set), the ones
 * this task can actually rebuild. The index based exchanges are served for the sibling task by
 * {@link StockexchangeEntityIdOptionsProvider}.</li>
 * <li>{@code TradingCalendarRuleSet} &rarr; every rule set, so a full rebuild of all exchanges using that set (and the
 * sets extending it) can be triggered by name.</li>
 * </ul>
 * Recreating every rule based calendar at once is done from the form by leaving the entity empty, not through these
 * lists.
 */
@Component
public class TradingCalendarRuleSetTaskEntityIdOptionsProvider implements EntityIdOptionsProvider {

  @Autowired
  private StockexchangeJpaRepository stockexchangeJpaRepository;

  @Autowired
  private TradingCalendarRuleSetJpaRepository tradingCalendarRuleSetJpaRepository;

  @Override
  public void addEntityIdOptions(TaskDataChangeFormConstraints constraints) {
    List<ValueKeyHtmlSelectOptions> ruleBasedExchangeOptions = stockexchangeJpaRepository
        .findByIdTradingCalendarRuleSetIsNotNull().stream()
        .map(stockexchange -> new ValueKeyHtmlSelectOptions(String.valueOf(stockexchange.getIdStockexchange()),
            stockexchange.getName()))
        .sorted(Comparator.comparing(o -> o.value))
        .collect(Collectors.toList());
    constraints.putTaskOptions(TaskTypeExtended.CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET,
        Stockexchange.class.getSimpleName(), ruleBasedExchangeOptions);

    List<ValueKeyHtmlSelectOptions> ruleSetOptions = tradingCalendarRuleSetJpaRepository.findAll().stream()
        .map(ruleSet -> new ValueKeyHtmlSelectOptions(String.valueOf(ruleSet.getIdTradingCalendarRuleSet()),
            ruleSet.getName()))
        .sorted(Comparator.comparing(o -> o.value))
        .collect(Collectors.toList());
    constraints.putTaskOptions(TaskTypeExtended.CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET,
        TradingCalendarRuleSet.class.getSimpleName(), ruleSetOptions);
  }
}
