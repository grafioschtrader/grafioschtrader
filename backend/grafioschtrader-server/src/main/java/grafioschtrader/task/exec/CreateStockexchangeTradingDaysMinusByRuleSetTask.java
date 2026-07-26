package grafioschtrader.task.exec;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.calendar.rule.HolidayRuleSet;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.TradingCalendarRuleSet;
import grafioschtrader.entities.TradingDaysMinus;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.repository.StockexchangeJpaRepository;
import grafioschtrader.repository.TradingCalendarRuleSetJpaRepository;
import grafioschtrader.repository.TradingDaysMinusJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Derives the trading calendar of a stock exchange from a trading calendar rule set. It is the rule based counterpart
 * of {@link CreateStockexchangeTradingDaysMinusByIndexTask}: instead of reading the gaps in a reference index it
 * resolves the rule set to its closure dates and writes them into {@code trading_days_minus}.
 *
 * <p>
 * The task runs in one of three modes, selected by the entity reference of the task entry:
 * </p>
 * <ul>
 * <li>No entity: the scheduled weekly run. Every rule based stock exchange is updated incrementally, that means only
 * the days after {@code stockexchange.max_calendar_upd_date} up to the end of the period are added. This is normally a
 * no-op until {@code trading_days_plus} is extended into a new year.</li>
 * <li>{@code Stockexchange}: the calendar of that single stock exchange is rebuilt completely. Created when the
 * calendar source of an exchange changed.</li>
 * <li>{@code TradingCalendarRuleSet}: the rules changed, so every stock exchange using that rule set is rebuilt
 * completely. Because a rule set may extend another one, the exchanges of every set that extends the changed one are
 * rebuilt as well.</li>
 * </ul>
 *
 * <p>
 * A full rebuild ignores {@code max_calendar_upd_date} and regenerates the whole authoritative period, which is
 * required when the rules themselves changed. Manually created entries ({@code create_type = 5}) always survive a
 * rebuild.
 * </p>
 */
@Component
public class CreateStockexchangeTradingDaysMinusByRuleSetTask implements ITask {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired
  private StockexchangeJpaRepository stockexchangeJpaRepository;

  @Autowired
  private TradingCalendarRuleSetJpaRepository tradingCalendarRuleSetJpaRepository;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Autowired
  private TradingDaysMinusJpaRepository tradingDaysMinusJpaRepository;

  /**
   * Marker written to {@code oldValueString} of the weekly scheduled task so its no-entity run stays incremental. A
   * no-entity task created from the admin form carries no marker and therefore performs a full recreate of every rule
   * based calendar.
   */
  public static final String INCREMENTAL_MARKER = "INCREMENTAL";

  @Scheduled(cron = "${gt.calendar.update.ruleset}", zone = BaseConstants.TIME_ZONE)
  public void createCreateStockexchangeTradingDaysMinusByRuleSetTask() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_NORMAL);
    taskDataChange.setOldValueString(INCREMENTAL_MARKER);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET;
  }

  @Override
  public List<String> getAllowedEntities() {
    // The empty entity is the "all rule based exchanges" option; a no-entity job created from the form recreates
    // every rule based calendar (full rebuild).
    return Arrays.asList("", Stockexchange.class.getSimpleName(), TradingCalendarRuleSet.class.getSimpleName());
  }

  @Override
  @Transactional
  public void doWork(TaskDataChange taskDataChange) {
    if (Stockexchange.class.getSimpleName().equals(taskDataChange.getEntity())) {
      stockexchangeJpaRepository.findById(taskDataChange.getIdEntity())
          .ifPresent(stockexchange -> rebuildExchange(stockexchange, true));
    } else if (TradingCalendarRuleSet.class.getSimpleName().equals(taskDataChange.getEntity())) {
      rebuildExchangesOfRuleSet(taskDataChange.getIdEntity());
    } else {
      // No entity: the weekly cron marks itself incremental; an admin created no-entity job has no marker and does a
      // full recreate of every rule based calendar.
      boolean fullRebuild = !INCREMENTAL_MARKER.equals(taskDataChange.getOldValueString());
      rebuildAllRuleBasedExchanges(fullRebuild);
    }
  }

  /**
   * Run over every rule based stock exchange. In incremental mode (the weekly cron) each exchange is only extended past
   * its {@code max_calendar_upd_date}, so nothing is recomputed as long as the period was not extended. In full rebuild
   * mode (an admin triggered no-entity job) the whole authoritative period is regenerated.
   *
   * @param fullRebuild {@code true} regenerates the whole period for every exchange, {@code false} only appends the tail
   */
  private void rebuildAllRuleBasedExchanges(boolean fullRebuild) {
    List<Stockexchange> stockexchanges = stockexchangeJpaRepository.findByIdTradingCalendarRuleSetIsNotNull();
    for (Stockexchange stockexchange : stockexchanges) {
      rebuildExchangeGuarded(stockexchange, fullRebuild);
    }
    log.info("{} the rule based trading calendars of {} stock exchanges", fullRebuild ? "Rebuilt" : "Updated",
        stockexchanges.size());
  }

  /**
   * Full rebuild of every stock exchange affected by a change to the given rule set. Affected are the exchanges using
   * that set and the exchanges of every set that extends it, because a parent rule change alters the resolved closures
   * of its children.
   *
   * @param idTradingCalendarRuleSet the rule set that changed
   */
  private void rebuildExchangesOfRuleSet(Integer idTradingCalendarRuleSet) {
    Set<Integer> affectedRuleSets = collectRuleSetWithDescendants(idTradingCalendarRuleSet);
    List<Stockexchange> stockexchanges = stockexchangeJpaRepository.findByIdTradingCalendarRuleSetIn(affectedRuleSets);
    for (Stockexchange stockexchange : stockexchanges) {
      rebuildExchangeGuarded(stockexchange, true);
    }
    log.info("Rebuilt the trading calendars of {} stock exchanges after a change to rule set {}",
        stockexchanges.size(), idTradingCalendarRuleSet);
  }

  /**
   * Returns the given rule set together with every rule set that transitively extends it. Loads all rule sets once and
   * walks the {@code id_extends_rule_set} graph downward, which is cheap for the small number of rule sets.
   */
  private Set<Integer> collectRuleSetWithDescendants(Integer idRootRuleSet) {
    Map<Integer, List<Integer>> childrenByParent = tradingCalendarRuleSetJpaRepository.findAll().stream()
        .filter(rs -> rs.getIdExtendsRuleSet() != null)
        .collect(Collectors.groupingBy(TradingCalendarRuleSet::getIdExtendsRuleSet,
            Collectors.mapping(TradingCalendarRuleSet::getIdTradingCalendarRuleSet, Collectors.toList())));

    Set<Integer> affected = new HashSet<>();
    List<Integer> pending = new ArrayList<>(List.of(idRootRuleSet));
    while (!pending.isEmpty()) {
      Integer current = pending.remove(pending.size() - 1);
      if (affected.add(current)) {
        pending.addAll(childrenByParent.getOrDefault(current, List.of()));
      }
    }
    return affected;
  }

  private void rebuildExchangeGuarded(Stockexchange stockexchange, boolean fullRebuild) {
    try {
      rebuildExchange(stockexchange, fullRebuild);
    } catch (RuntimeException ex) {
      // One malformed rule set must not abort the whole batch; the other exchanges still get their calendar.
      log.error("Could not rebuild the trading calendar of stock exchange {} ({})",
          stockexchange.getIdStockexchange(), stockexchange.getName(), ex);
    }
  }

  /**
   * Rebuilds the {@code trading_days_minus} rows of one rule based stock exchange from its resolved rule set.
   *
   * @param stockexchange the exchange to rebuild, must reference a rule set
   * @param fullRebuild   {@code true} regenerates the whole authoritative period, {@code false} only appends the days
   *                      after {@code max_calendar_upd_date}
   */
  private void rebuildExchange(Stockexchange stockexchange, boolean fullRebuild) {
    if (stockexchange.getIdTradingCalendarRuleSet() == null) {
      return;
    }
    TradingDaysPlus lastPlus = tradingDaysPlusJpaRepository.findTopByOrderByTradingDateDesc();
    TradingDaysPlus firstPlus = tradingDaysPlusJpaRepository.findTopByOrderByTradingDateAsc();
    if (lastPlus == null || firstPlus == null) {
      return;
    }
    LocalDate periodEnd = lastPlus.getTradingDate();
    LocalDate periodStart = fullRebuild || stockexchange.getMaxCalendarUpdDate() == null ? firstPlus.getTradingDate()
        : stockexchange.getMaxCalendarUpdDate().plusDays(1);
    if (periodStart.isAfter(periodEnd)) {
      return;
    }

    HolidayRuleSet ruleSet = tradingCalendarRuleSetJpaRepository
        .getResolvedRuleSet(stockexchange.getIdTradingCalendarRuleSet());
    Set<LocalDate> closures = resolveClosuresInRange(ruleSet, periodStart, periodEnd);

    // Only days that are candidate trading days may become non trading days. Global holidays such as 1 January and
    // 25 December are already absent from trading_days_plus, so they drop out here.
    Set<LocalDate> candidateDays = tradingDaysPlusJpaRepository
        .findByTradingDateBetweenOrderByTradingDate(periodStart, periodEnd).stream()
        .map(TradingDaysPlus::getTradingDate).collect(Collectors.toSet());
    closures.retainAll(candidateDays);

    Integer idStockexchange = stockexchange.getIdStockexchange();
    tradingDaysMinusJpaRepository.deleteDerivedByStockexchangeInRange(idStockexchange, periodStart, periodEnd);
    Set<LocalDate> userDates = tradingDaysMinusJpaRepository.findUserDatesInRange(idStockexchange, periodStart,
        periodEnd);

    List<TradingDaysMinus> newRows = closures.stream().filter(date -> !userDates.contains(date))
        .map(date -> new TradingDaysMinus(idStockexchange, date, CreateType.RULE_CREATED)).toList();
    tradingDaysMinusJpaRepository.saveAll(newRows);

    stockexchange.setMaxCalendarUpdDate(periodEnd);
    stockexchangeJpaRepository.save(stockexchange);
    log.info("Rebuilt {} rule based non trading days for stock exchange {} between {} and {}", newRows.size(),
        idStockexchange, periodStart, periodEnd);
  }

  /**
   * Collects the closure dates of the rule set within the range, skipping the years the set is not authoritative for
   * (asking for a non authoritative year throws).
   */
  private Set<LocalDate> resolveClosuresInRange(HolidayRuleSet ruleSet, LocalDate from, LocalDate to) {
    Set<LocalDate> closures = new HashSet<>();
    for (int year = from.getYear(); year <= to.getYear(); year++) {
      if (!ruleSet.isAuthoritativeForYear(year)) {
        continue;
      }
      for (LocalDate date : ruleSet.closureDatesForYear(year)) {
        if (!date.isBefore(from) && !date.isAfter(to)) {
          closures.add(date);
        }
      }
    }
    return closures;
  }
}
