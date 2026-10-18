package grafiosch.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.TaskDataChange;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;

/**
 * Background tasks run in queues with their own workers. A wrong assignment lets a slow task hold up the main queue
 * again, or lets two workers start the same task, or two runs work on the same subject and spend twice.
 */
class TaskQueueTest {

  private static final ITaskType MAIN_TYPE = TaskTypeBase.TOKEN_USER_REGISTRATION_PURGE;
  private static final ITaskType PARALLEL_TYPE = TaskTypeBase.MAIL_ROLE_MESSAGE_PURGE;
  private static final ITaskType MISCONFIGURED_TYPE = TaskTypeBase.GTNET_SERVER_STATUS_CHECK;

  @Test
  @DisplayName("A task runs in its declared queue; an undeclared queue falls back to the main queue, which comes first")
  void queueAssignment() {
    TaskQueueRegistry registry = new TaskQueueRegistry(
        List.of(task(MAIN_TYPE, TaskQueueDefinition.MAIN), task(PARALLEL_TYPE, "AI"), task(MISCONFIGURED_TYPE, "NONE")),
        List.of(new TaskQueueDefinition("AI", 3, "TASK_QUEUE_AI")));

    assertThat(registry.getQueues()).extracting(TaskQueueDefinition::name).containsExactly(TaskQueueDefinition.MAIN,
        "AI");
    assertThat(registry.getQueues().getFirst().parallel()).isEqualTo(1);
    assertThat(registry.getTaskTypes(TaskQueueDefinition.MAIN)).containsExactlyInAnyOrder(MAIN_TYPE.getValue(),
        MISCONFIGURED_TYPE.getValue());
    assertThat(registry.getTaskTypes("AI")).containsExactly(PARALLEL_TYPE.getValue());
  }

  @Test
  @DisplayName("A worker skips a task whose subject another worker of the queue runs, and takes the next one")
  void subjectRunsOnlyOnce() {
    TaskDataChangeJpaRepository repository = mock(TaskDataChangeJpaRepository.class);
    BackgroundWorker worker = new BackgroundWorker();
    ReflectionTestUtils.setField(worker, "taskDataChangeRepository", repository);
    TaskDataChange running = waiting(1, 6383);
    TaskDataChange sameField = waiting(2, 6383);
    TaskDataChange otherField = waiting(3, 7);
    // The query already excludes the claimed task 1
    when(repository.findNextInQueue(anyByte(), any(LocalDateTime.class), anyList(), anyList(), any(Pageable.class)))
        .thenReturn(List.of(sameField, otherField));
    Map<Integer, String> claimed = new HashMap<>();
    claimed.put(1, BackgroundWorker.subject(running));

    assertThat(worker.claimNext(List.of(PARALLEL_TYPE.getValue()), claimed)).contains(otherField);
    assertThat(claimed).containsOnlyKeys(1, 3);

    when(repository.findNextInQueue(anyByte(), any(LocalDateTime.class), anyList(), anyList(), any(Pageable.class)))
        .thenReturn(List.of(sameField));
    assertThat(worker.claimNext(List.of(PARALLEL_TYPE.getValue()), claimed)).isEmpty();
  }

  @Test
  @DisplayName("Tasks without an entity never collide with each other")
  void tasksWithoutEntityDoNotCollide() {
    TaskDataChange first = new TaskDataChange(PARALLEL_TYPE, TaskDataExecPriority.PRIO_NORMAL);
    first.setIdTaskDataChange(10);
    TaskDataChange second = new TaskDataChange(PARALLEL_TYPE, TaskDataExecPriority.PRIO_NORMAL);
    second.setIdTaskDataChange(11);
    assertThat(BackgroundWorker.subject(first)).isNotEqualTo(BackgroundWorker.subject(second));
  }

  private static TaskDataChange waiting(int id, int idEntity) {
    TaskDataChange task = new TaskDataChange(PARALLEL_TYPE, TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now(),
        idEntity, "UDFMetadataSecurity");
    task.setIdTaskDataChange(id);
    return task;
  }

  private static ITask task(ITaskType type, String queue) {
    return new ITask() {
      @Override
      public ITaskType getTaskType() {
        return type;
      }

      @Override
      public void doWork(TaskDataChange taskDataChange) {
      }

      @Override
      public String getQueue() {
        return queue;
      }
    };
  }
}
