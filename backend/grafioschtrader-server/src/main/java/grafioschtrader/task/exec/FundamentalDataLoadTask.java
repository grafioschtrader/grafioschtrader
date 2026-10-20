package grafioschtrader.task.exec;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.ProgressStateType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.entities.Security;
import grafioschtrader.fundamental.FundamentalDataLoadService;
import grafioschtrader.fundamental.FundamentalDataLoadService.SourceSummary;
import grafioschtrader.types.FundamentalSource;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Weekly load of the fundamental base values that the watchlist indicator columns read. Without an entity it covers
 * every active direct equity in a watchlist or a transaction; with a security as entity it loads only that one, so that
 * an administrator can refresh a single security from the task monitor while the schedule is off.
 *
 * <p>
 * The schedule {@code gt.fundamental.load.cron} is disabled with {@code -}; the property {@code gt.fundamental.sources}
 * selects the sources, and when it is empty no fundamental request leaves the instance, not even from a manual task.
 * </p>
 *
 * <p>
 * The outcome per source is written to the log. When a source that was asked delivered not a single usable answer, or
 * was stopped by a refused key or plan, the task ends as failed with that outcome as its message, so the task monitor
 * shows when a source stopped working; the values that did arrive are stored all the same.
 * </p>
 */
@Component
public class FundamentalDataLoadTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(FundamentalDataLoadTask.class);

  @Autowired
  private FundamentalDataLoadService fundamentalDataLoadService;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.FUNDAMENTAL_DATA_LOAD;
  }

  /** The empty entity is the whole scope, a security the load of just that one. */
  @Override
  public List<String> getAllowedEntities() {
    return List.of("", Security.class.getSimpleName());
  }

  @Scheduled(cron = "${gt.fundamental.load.cron:0 23 4 * * SAT}", zone = BaseConstants.TIME_ZONE)
  public void loadFundamentalData() {
    taskDataChangeJpaRepository.save(new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_VERY_LOW));
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    if (anotherRunIsRunning(taskDataChange)) {
      log.info("Another fundamental data load is running, task {} ends", taskDataChange.getIdTaskDataChange());
      return;
    }
    Map<FundamentalSource, SourceSummary> summaries;
    try {
      summaries = fundamentalDataLoadService.load(taskDataChange.getIdEntity());
    } catch (Exception e) {
      // The task monitor shows only the system messages, so the stack trace goes there as well as into the log.
      log.error("Fundamental data load failed", e);
      StringWriter stackTrace = new StringWriter();
      e.printStackTrace(new PrintWriter(stackTrace));
      throw new TaskBackgroundException("gt.fundamental.load.failure", List.of(stackTrace.toString()), false);
    }
    List<String> lines = new ArrayList<>();
    boolean sourceWithoutAnswer = false;
    for (Map.Entry<FundamentalSource, SourceSummary> entry : summaries.entrySet()) {
      SourceSummary summary = entry.getValue();
      lines.add(entry.getKey().name() + ": " + summary);
      sourceWithoutAnswer |= summary.usable == 0 && summary.failed > 0 || summary.stopReason != null;
    }
    log.info("Fundamental data load {}", lines.isEmpty() ? "skipped, no source enabled" : String.join("; ", lines));
    if (sourceWithoutAnswer) {
      throw new TaskBackgroundException("gt.fundamental.source.no.usable", lines, false);
    }
  }

  /** Two runs must never overwrite each other's results; the task itself is already running. */
  private boolean anotherRunIsRunning(TaskDataChange taskDataChange) {
    return taskDataChangeJpaRepository.findByIdTaskIn(List.of(getTaskType().getValue())).stream()
        .anyMatch(t -> t.getProgressStateType() == ProgressStateType.PROG_RUNNING
            && !t.getIdTaskDataChange().equals(taskDataChange.getIdTaskDataChange()));
  }
}
