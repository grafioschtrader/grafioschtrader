package grafioschtrader.rest;

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
import grafiosch.rest.RequestMappings;
import grafiosch.types.ProgressStateType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.types.TaskTypeExtended;

@TestInstance(Lifecycle.PER_CLASS)
class TaskDataChangeResourceTest extends BaseIntegrationTest {

  private static final String TEST_USER_EMAIL = "hg@hugograf.com";

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Test
  @DisplayName("User 'hg@hugograf.com' schedules the stock exchange calendar task with a five-minute delay")
  void scheduleStockexchangeTradingDaysMinusByIndexTask() {
    assertThat(RestTestHelper.getUserByNickname(RestTestHelper.ADMIN).email).isEqualTo(TEST_USER_EMAIL);

    LocalDateTime earliestStartTime = LocalDateTime.now().plusMinutes(5).withNano(0);
    TaskDataChange taskDataChange = new TaskDataChange(TaskTypeExtended.CREATE_STOCK_EXCHANGE_CALENDAR_BY_INDEX,
        TaskDataExecPriority.PRIO_NORMAL, earliestStartTime);

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
    assertThat(createdTask.getIdTask()).isEqualTo(TaskTypeExtended.CREATE_STOCK_EXCHANGE_CALENDAR_BY_INDEX);
    assertThat(createdTask.getExecutionPriority()).isEqualTo(TaskDataExecPriority.PRIO_NORMAL);
    assertThat(createdTask.getProgressStateType()).isEqualTo(ProgressStateType.PROG_WAITING);
    assertThat(createdTask.getEarliestStartTime()).isEqualTo(earliestStartTime);

    TaskDataChange scheduledTask = getTaskById(createdTask.getIdTaskDataChange());
    assertThat(scheduledTask.getProgressStateType()).isEqualTo(ProgressStateType.PROG_WAITING);
    assertThat(scheduledTask.getEarliestStartTime()).isEqualTo(earliestStartTime);
    assertThat(Duration.between(scheduledTask.getCreationTime(), scheduledTask.getEarliestStartTime()))
        .isBetween(Duration.ofMinutes(4).plusSeconds(55), Duration.ofMinutes(5));
    assertThat(scheduledTask.getExecStartTime()).isNull();
    assertThat(scheduledTask.getExecEndTime()).isNull();
    assertThat(scheduledTask.getFailedMessageCode()).isNull();
    assertThat(scheduledTask.getFailedStackTrace()).isNull();
  }

  private TaskDataChange getTaskById(Integer idTaskDataChange) {
    TaskDataChange[] tasks = authenticatedClient(RestTestHelper.ADMIN)
        .get()
        .uri(uriBuilder -> uriBuilder.path(RequestMappings.TASK_DATA_CHANGE_MAP)
            .queryParam("idTasks", TaskTypeExtended.CREATE_STOCK_EXCHANGE_CALENDAR_BY_INDEX.getValue()).build())
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
