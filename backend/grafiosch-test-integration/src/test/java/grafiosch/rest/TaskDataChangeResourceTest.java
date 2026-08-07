package grafiosch.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import grafiosch.entities.TaskDataChange;
import grafiosch.types.ProgressStateType;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;

/**
 * Schedules a background job through the batch processing monitor endpoint and verifies that it is persisted as waiting
 * with the requested delay, without waiting for the single-threaded {@code BackgroundWorker} to pick it up.
 *
 * <p>
 * The task type is one of the library's own ({@code TaskTypeBase}); applications add theirs in the value band above 29.
 * A ten-minute delay keeps the job out of the way of the rest of the suite.
 */
@TestInstance(Lifecycle.PER_CLASS)
class TaskDataChangeResourceTest extends BaseIntegrationTest {

  private static final TaskTypeBase TASK_TYPE = TaskTypeBase.TOKEN_USER_REGISTRATION_PURGE;

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Test
  @DisplayName("Admin schedules the registration token purge with a ten-minute delay")
  void scheduleTokenUserRegistrationPurge() {
    LocalDateTime earliestStartTime = LocalDateTime.now().plusMinutes(10).withNano(0);
    TaskDataChange taskDataChange = new TaskDataChange(TASK_TYPE, TaskDataExecPriority.PRIO_NORMAL, earliestStartTime);

    TaskDataChange createdTask = authenticatedClient(RestTestHelper.ADMIN)
        .post()
        .uri(RequestMappings.TASK_DATA_CHANGE_MAP)
        .body(taskDataChange)
        .exchange()
        .expectStatus().isOk()
        .expectBody(TaskDataChange.class)
        .returnResult()
        .getResponseBody();

    assertThat(createdTask).isNotNull();
    assertThat(createdTask.getIdTaskDataChange()).isPositive();
    assertThat(createdTask.getIdTask()).isEqualTo(TASK_TYPE);
    assertThat(createdTask.getExecutionPriority()).isEqualTo(TaskDataExecPriority.PRIO_NORMAL);
    assertThat(createdTask.getProgressStateType()).isEqualTo(ProgressStateType.PROG_WAITING);
    assertThat(createdTask.getEarliestStartTime()).isEqualTo(earliestStartTime);

    TaskDataChange scheduledTask = getTaskById(createdTask.getIdTaskDataChange());
    assertThat(scheduledTask.getProgressStateType()).isEqualTo(ProgressStateType.PROG_WAITING);
    assertThat(scheduledTask.getEarliestStartTime()).isEqualTo(earliestStartTime);
    assertThat(Duration.between(scheduledTask.getCreationTime(), scheduledTask.getEarliestStartTime()))
        .isBetween(Duration.ofMinutes(9).plusSeconds(55), Duration.ofMinutes(10));
    assertThat(scheduledTask.getExecStartTime()).isNull();
    assertThat(scheduledTask.getExecEndTime()).isNull();
    assertThat(scheduledTask.getFailedMessageCode()).isNull();
    assertThat(scheduledTask.getFailedStackTrace()).isNull();
  }

  private TaskDataChange getTaskById(Integer idTaskDataChange) {
    TaskDataChange[] tasks = authenticatedClient(RestTestHelper.ADMIN)
        .get()
        .uri(uriBuilder -> uriBuilder.path(RequestMappings.TASK_DATA_CHANGE_MAP)
            .queryParam("idTasks", TASK_TYPE.getValue()).build())
        .exchange()
        .expectStatus().isOk()
        .expectBody(TaskDataChange[].class)
        .returnResult()
        .getResponseBody();

    assertThat(tasks).isNotNull();
    return Arrays.stream(tasks).filter(task -> idTaskDataChange.equals(task.getIdTaskDataChange())).findFirst()
        .orElseThrow();
  }
}
