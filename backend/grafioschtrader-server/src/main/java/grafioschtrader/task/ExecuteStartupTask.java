package grafioschtrader.task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import grafiosch.entities.Globalparameters;
import grafiosch.entities.TaskDataChange;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Execute Job PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU when it was not executed
 * the day before or there is no existing
 * {@link grafiosch.entities.TaskDataChange}.
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
        .findById(GlobalParamKeyDefault.GLOB_KEY_YOUNGEST_SPLIT_APPEND_DATE);
    if (gpLastAppend.isEmpty() || gpLastAppend.get().getPropertyDate().isBefore(LocalDate.now().minusDays(1L))) {
      addDataUpdateTask();
    } else if (taskDataChangeJpaRepository.count() == 0) {
      addDataUpdateTask();
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT,
          TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusMinutes(10), null, null));
    }
  }

  private void addDataUpdateTask() {
    TaskDataChange taskDataChange = new TaskDataChange(TaskTypeExtended.PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU,
        TaskDataExecPriority.PRIO_HIGH, LocalDateTime.now().plusMinutes(5), null, null);
    taskDataChangeJpaRepository.save(taskDataChange);
  }

}
