package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;

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
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.repository.StockexchangeJpaRepository;
import grafioschtrader.repository.TradingDaysMinusJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

/**
 * An index can be used to track the free trading days on a trading venue. This eliminates the need to update the
 * trading days manually. If possible, this task should be carried out on a Sunday to avoid temporary incorrect entries
 * of public holidays.
 *
 * <p>
 * The task runs in one of three modes, selected by the entity reference of the task entry:
 * </p>
 * <ul>
 * <li>No entity: the scheduled weekly run. Every index linked stock exchange gets its calendar appended incrementally,
 * that means only the days after the newest manually created entry are derived again.</li>
 * <li>{@code Stockexchange}: the calendar of that single stock exchange is rebuilt completely. This is the variant an
 * administrator can create manually.</li>
 * <li>{@code Security}: the historical price data of that index was reloaded, therefore the calendar of every stock
 * exchange linked to it is rebuilt completely.</li>
 * </ul>
 *
 * <p>
 * A complete rebuild is required after a reload of the index, because the incremental mode never touches the days
 * before the newest manually created entry. Those days would otherwise stay derived from the discarded price data.
 * Manually created entries always survive a rebuild.
 * </p>
 */
@Component
public class CreateStockexchangeTradingDaysMinusByIndexTask implements ITask {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired
  private TradingDaysMinusJpaRepository tradingDaysMinusJpaRepository;

  @Autowired
  private StockexchangeJpaRepository stockexchangeJpaRepository;

  @Scheduled(cron = "${gt.calendar.update.index}", zone = BaseConstants.TIME_ZONE)
  public void createCreateStockexchangeTradingDaysMinusByIndexTask() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_NORMAL);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.CREATE_STOCK_EXCHANGE_CALENDAR_BY_INDEX;
  }

  @Override
  public List<String> getAllowedEntities() {
    return Arrays.asList(Stockexchange.class.getSimpleName(), Security.class.getSimpleName());
  }

  @Override
  @Transactional
  public void doWork(TaskDataChange taskDataChange) {
    if (Stockexchange.class.getSimpleName().equals(taskDataChange.getEntity())) {
      rebuildStockexchange(taskDataChange.getIdEntity());
    } else if (Security.class.getSimpleName().equals(taskDataChange.getEntity())) {
      rebuildStockexchangesOfIndex(taskDataChange.getIdEntity());
    } else {
      tradingDaysMinusJpaRepository.updCalendarStockexchangeByIndex(null, false);
      log.info("Update stock exchange tranding calendars");
    }
  }

  /**
   * Rebuilds the trading calendar of every stock exchange whose calendar is derived from the given index. Called after
   * the historical price data of that index was wiped and reloaded.
   *
   * @param idSecuritycurrency the ID of the reloaded index security
   */
  private void rebuildStockexchangesOfIndex(Integer idSecuritycurrency) {
    List<Stockexchange> stockexchanges = stockexchangeJpaRepository.findByIdIndexUpdCalendar(idSecuritycurrency);
    if (stockexchanges.isEmpty()) {
      log.info("Security {} is not used as calendar index, no trading calendar rebuilt", idSecuritycurrency);
      return;
    }
    stockexchanges.forEach(stockexchange -> rebuildStockexchange(stockexchange.getIdStockexchange()));
  }

  private void rebuildStockexchange(Integer idStockexchange) {
    tradingDaysMinusJpaRepository.updCalendarStockexchangeByIndex(idStockexchange, true);
    log.info("Rebuilt trading calendar of stock exchange {}", idStockexchange);
  }

}
