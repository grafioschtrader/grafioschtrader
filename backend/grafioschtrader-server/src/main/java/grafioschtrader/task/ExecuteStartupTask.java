package grafioschtrader.task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.Globalparameters;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;

/**
 * Execute Job PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU when it was not executed
 * the day before or there is no existing
 * {@link grafioschtrader.entities.TaskDataChange}.
 *
 * @author Hugo Graf
 *
 */
@Component
public class ExecuteStartupTask implements ApplicationListener<ApplicationReadyEvent> {

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    Optional<Globalparameters> gpLastAppend = globalparametersJpaRepository
        .findById(Globalparameters.GLOB_KEY_YOUNGES_SPLIT_APPEND_DATE);
    if (gpLastAppend.isEmpty() || gpLastAppend.get().getPropertyDate().isBefore(LocalDate.now().minusDays(1L))) {
      addDataUpdateTask();
    } else if (taskDataChangeJpaRepository.count() == 0) {
      addDataUpdateTask();
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskType.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT,
          TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusMinutes(10), null, null));
    }
  }

  private void addDataUpdateTask() {
    TaskDataChange taskDataChange = new TaskDataChange(TaskType.PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU,
        TaskDataExecPriority.PRIO_HIGH, LocalDateTime.now().plusMinutes(5), null, null);
    taskDataChangeJpaRepository.save(taskDataChange);
  }

}
