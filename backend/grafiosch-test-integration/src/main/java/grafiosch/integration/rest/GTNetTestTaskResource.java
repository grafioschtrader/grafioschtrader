package grafiosch.integration.rest;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessageAttempt;
import grafiosch.entities.TaskDataChange;
import grafiosch.gtnet.GTNetServerOnlineStatusTypes;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GTNetMessageAttemptJpaRepository;
import grafiosch.task.TaskExecutionService;

/**
 * Deterministic controls for the two-peer browser and protocol tests. It is absent from every non-e2e runtime.
 *
 * Both endpoints exist because the peers run with the background worker disabled: a task is executed when the test asks
 * for it rather than when a 15 second poll happens to pick it up. It also offers test-only state controls and direct
 * entity reads, while production exposes only the administrator-facing attempt view.
 */
@Profile("e2e")
@RestController
@RequestMapping("/api/integration-gtnet-test")
@PreAuthorize("hasRole('ADMIN')")
public class GTNetTestTaskResource {

  private final TaskExecutionService taskExecutionService;
  private final GTNetMessageAttemptJpaRepository gtNetMessageAttemptJpaRepository;
  private final GTNetJpaRepository gtNetJpaRepository;

  public GTNetTestTaskResource(TaskExecutionService taskExecutionService,
      GTNetMessageAttemptJpaRepository gtNetMessageAttemptJpaRepository, GTNetJpaRepository gtNetJpaRepository) {
    this.taskExecutionService = taskExecutionService;
    this.gtNetMessageAttemptJpaRepository = gtNetMessageAttemptJpaRepository;
    this.gtNetJpaRepository = gtNetJpaRepository;
  }

  /**
   * Runs one queued task synchronously through the production lifecycle.
   *
   * @param idTaskDataChange the queued task row to execute
   * @return the task row in its final state
   */
  @PostMapping("/tasks/{idTaskDataChange}/run")
  public TaskDataChange runTask(@PathVariable Integer idTaskDataChange) throws InterruptedException {
    return taskExecutionService.executeWithTimeout(idTaskDataChange);
  }

  /**
   * Returns the delivery attempts recorded for one message, so a test can assert that a future-dated announcement moved
   * from unsent to sent, and that an unreachable peer leaves its attempt unsent.
   *
   * @param idGtNetMessage the message whose attempts are requested
   * @return every attempt row of that message, sent and unsent
   */
  @GetMapping("/messages/{idGtNetMessage}/attempts")
  public List<GTNetMessageAttempt> getAttempts(@PathVariable Integer idGtNetMessage) {
    return gtNetMessageAttemptJpaRepository.findByIdGtNetMessage(idGtNetMessage);
  }

  /** Changes one remote's online status so terminal delivery paths can be staged without stopping a peer process. */
  @PostMapping("/peers/{idGtNet}/online-status/{status}")
  public GTNet setOnlineStatus(@PathVariable Integer idGtNet, @PathVariable byte status) {
    GTNet peer = gtNetJpaRepository.findById(idGtNet).orElseThrow();
    peer.setServerOnline(GTNetServerOnlineStatusTypes.getGTNetServerOnlineStatusType(status));
    return gtNetJpaRepository.save(peer);
  }
}
