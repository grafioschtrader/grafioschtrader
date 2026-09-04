package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.RepositoryHelper;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.ProgressStateType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.dto.StockexchangeBaseData;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.StockexchangeMic;
import grafioschtrader.entities.TradingCalendarRuleSet;
import grafioschtrader.repository.StockexchangeJpaRepository.IdStockexchangeIndexName;
import grafioschtrader.types.TaskTypeExtended;

public class StockexchangeJpaRepositoryImpl extends BaseRepositoryImpl<Stockexchange>
    implements StockexchangeJpaRepositoryCustom {

  @Autowired
  private StockexchangeJpaRepository stockexchangeJpaRepository;

  @Autowired
  private StockexchangeMicJpaRepository stockexchangeMicJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private TradingCalendarRuleSetJpaRepository tradingCalendarRuleSetJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired
  private TradingDaysMinusJpaRepository tradingDaysMinusJpaRepository;

  @Override
  public Stockexchange saveOnlyAttributes(final Stockexchange stockexchange, final Stockexchange existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {

    if (stockexchange.getMic() != null) {
      Optional<StockexchangeMic> stockexchangeMic = stockexchangeMicJpaRepository
          .findByMicAndCountryCode(stockexchange.getMic(), stockexchange.getCountryCode());
      if (!stockexchangeMic.isPresent()) {
        throw new IllegalArgumentException("The combination of MIC and Country does not exists!");
      }
    }

    // The trading calendar comes either from the quotes of a reference index or from a rule set, never from both.
    // Allowing both would leave it undefined which of the two produces the non trading days of this exchange.
    if (stockexchange.getIdIndexUpdCalendar() != null && stockexchange.getIdTradingCalendarRuleSet() != null) {
      throw new DataViolationException("id.index.upd.calendar", "gt.calendar.source.exclusive", null);
    }

    checkNoMarketValueChangeable(stockexchange, existingEntity);

    // Read the previous calendar source before the save overwrites the existing entity's fields.
    Integer oldIndex = existingEntity == null ? null : existingEntity.getIdIndexUpdCalendar();
    Integer oldRuleSet = existingEntity == null ? null : existingEntity.getIdTradingCalendarRuleSet();

    Stockexchange saved = RepositoryHelper.saveOnlyAttributes(stockexchangeJpaRepository, stockexchange, existingEntity,
        updatePropertyLevelClasses);

    enqueueCalendarRebuildOnSourceChange(saved, oldIndex, oldRuleSet);
    return saved;
  }

  /**
   * Rejects a change of the "no market value" flag once the exchange is in use. The flag decides where the prices of
   * its instruments come from - downloaded quotes or periods entered by the user - so switching it on an exchange that
   * already carries instruments would make their recorded prices unusable. As long as no instrument references the
   * exchange there is nothing to invalidate and the flag stays correctable.
   *
   * @param stockexchange  the exchange as it was submitted
   * @param existingEntity the persisted exchange, {@code null} while it is being created
   */
  private void checkNoMarketValueChangeable(Stockexchange stockexchange, Stockexchange existingEntity) {
    if (existingEntity != null && existingEntity.isNoMarketValue() != stockexchange.isNoMarketValue()
        && stockexchangeJpaRepository.stockexchangeHasSecurity(existingEntity.getIdStockexchange()) != 0) {
      throw new DataViolationException("no.market.value", "gt.stockexchange.no.market.value.locked", null);
    }
  }

  /**
   * Schedules a trading calendar rebuild when the calendar source of the exchange changed. A newly assigned rule set
   * or index triggers a full rebuild of this exchange through the matching background job; removing the source deletes
   * the derived calendar so nothing stale remains. The user created non trading days are never affected.
   */
  private void enqueueCalendarRebuildOnSourceChange(Stockexchange saved, Integer oldIndex, Integer oldRuleSet) {
    Integer newIndex = saved.getIdIndexUpdCalendar();
    Integer newRuleSet = saved.getIdTradingCalendarRuleSet();
    Integer idStockexchange = saved.getIdStockexchange();

    if (newRuleSet != null && !newRuleSet.equals(oldRuleSet)) {
      enqueueOncePerEntity(TaskTypeExtended.CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET, idStockexchange);
    } else if (newIndex != null && !newIndex.equals(oldIndex)) {
      enqueueOncePerEntity(TaskTypeExtended.CREATE_STOCK_EXCHANGE_CALENDAR_BY_INDEX, idStockexchange);
    } else if (newIndex == null && newRuleSet == null && (oldIndex != null || oldRuleSet != null)) {
      tradingDaysMinusJpaRepository.deleteDerivedByStockexchange(idStockexchange);
    }
  }

  /**
   * Enqueues a background job of the given type for this stock exchange, unless an equivalent job is already waiting.
   * The dedup keeps repeated saves from piling up redundant rebuilds.
   */
  private void enqueueOncePerEntity(TaskTypeExtended taskType, Integer idStockexchange) {
    if (taskDataChangeJpaRepository
        .findByIdTaskAndIdEntityAndProgressStateType(taskType.getValue(), idStockexchange,
            ProgressStateType.PROG_WAITING.getValue())
        .isEmpty()) {
      taskDataChangeJpaRepository.save(new TaskDataChange(taskType, TaskDataExecPriority.PRIO_LOW,
          LocalDateTime.now(), idStockexchange, Stockexchange.class.getSimpleName()));
    }
  }

  @Override
  public List<Stockexchange> getAllStockExchanges(boolean includeNameOfCalendarIndex) {
    List<Stockexchange> stockexchanes = stockexchangeJpaRepository.findAllByOrderByNameAsc();
    if (includeNameOfCalendarIndex) {
      Map<Integer, String> idSecurtyMap = stockexchangeJpaRepository.getIdStockexchangeAndIndexNameForCalendarUpd()
          .stream()
          .collect(Collectors.toMap(IdStockexchangeIndexName::getIdStockexchange, e -> e.getNameIndexSecurity()));
      // The calendar source is either the index or a rule set, so both names are resolved here and the table shows
      // whichever of the two is set.
      Map<Integer, String> ruleSetNameMap = tradingCalendarRuleSetJpaRepository.findAll().stream()
          .collect(Collectors.toMap(TradingCalendarRuleSet::getIdTradingCalendarRuleSet,
              TradingCalendarRuleSet::getName));
      stockexchanes.forEach(se -> {
        se.setNameIndexUpdCalendar(idSecurtyMap.get(se.getIdStockexchange()));
        se.setNameTradingCalendarRuleSet(se.getIdTradingCalendarRuleSet() == null ? null
            : ruleSetNameMap.get(se.getIdTradingCalendarRuleSet()));
      });
    }
    return stockexchanes;
  }

  @Override
  public StockexchangeBaseData getAllStockexchangesBaseData() {
    return new StockexchangeBaseData(getAllStockExchanges(true), stockexchangeJpaRepository.stockexchangesHasSecurity(),
        stockexchangeMicJpaRepository.findAll(), globalparametersJpaRepository.getCountriesForSelectBox());
  }
}
