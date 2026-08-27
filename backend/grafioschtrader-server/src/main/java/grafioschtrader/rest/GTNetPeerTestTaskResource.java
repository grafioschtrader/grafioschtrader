package grafioschtrader.rest;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.TaskDataChange;
import grafiosch.task.TaskExecutionService;

/**
 * Explicit task trigger for the two-peer GTNet application suite. It exists only under the e2e-gtnet profile, so
 * neither production, nor a developer start, nor the ordinary e2e main suite ever exposes it.
 *
 * Both application peers run with the background worker disabled, because its 15 second poll would make protocol
 * assertions depend on timing. A test that covers task orchestration therefore invokes the real ITask.doWork through
 * the production TaskExecutionService, with its timeout, state-transition and failure handling intact, and asserts the
 * resulting task_data_change state.
 */
@Profile("e2e-gtnet")
@RestController
@RequestMapping("/api/gtnet-peer-test/tasks")
@PreAuthorize("hasRole('ADMIN')")
public class GTNetPeerTestTaskResource {

  private final TaskExecutionService taskExecutionService;

  public GTNetPeerTestTaskResource(TaskExecutionService taskExecutionService) {
    this.taskExecutionService = taskExecutionService;
  }

  /**
   * Runs one queued task synchronously through the production lifecycle.
   *
   * @param idTaskDataChange the queued task row to execute
   * @return the task row in its final state
   */
  @PostMapping("/{idTaskDataChange}/run")
  public TaskDataChange runTask(@PathVariable Integer idTaskDataChange) throws InterruptedException {
    return taskExecutionService.executeWithTimeout(idTaskDataChange);
  }
}
