package grafiosch.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.types.ITaskType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
   Configuration constraints for task data change form operations and validations.
   This information is very helpful in the user interface for creating a new job.""")
public class TaskDataChangeFormConstraints {

  @Schema(description = "Configuration mapping of task types to their allowed parameter lists. Each task type can have specific parameters that control its behavior and execution",
      example = "{\"TOKEN_USER_REGISTRATION_PURGE\": [\"batchSize\", \"maxAge\"], \"MOVE_CREATED_BY_USER_TO_OTHER_USER\": [\"sourceUserId\", \"targetUserId\"]}")
  public Map<ITaskType, List<String>> taskTypeConfig = new HashMap<>();

  @Schema(description = "List of task types that support interruption during execution. Tasks in this list can be safely stopped or cancelled while running",
      example = "[\"TOKEN_USER_REGISTRATION_PURGE\"]")
  public List<ITaskType> canBeInterruptedList = new ArrayList<>();

  @Schema(description = """
      Selectable ID options per task type and entity name. When an entity type has predefined options
      (e.g., IFeedConnector with specific connectors), the frontend can display a dropdown instead of a
      free-form numeric input. The outer key is the task type, the inner key is the entity class simple
      name, and the value is a list of id-name pairs (key = numeric ID, value = display name). Options
      are task-scoped because the same entity type (e.g. Stockexchange) may need a different subset for
      different tasks.""",
      example = "{\"CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET\": {\"Stockexchange\": [{\"key\": \"5\", \"value\": \"SIX Swiss Exchange\"}]}}")
  public Map<ITaskType, Map<String, List<ValueKeyHtmlSelectOptions>>> entityIdOptions = new HashMap<>();

  /**
   * Options that apply to every task allowing the entity. A provider registers them once here; the form
   * builder copies them into {@link #entityIdOptions} for each task whose allowed entities contain the
   * entity. Not serialized; it is a staging map composed into {@link #entityIdOptions}.
   */
  @JsonIgnore
  public Map<String, List<ValueKeyHtmlSelectOptions>> sharedEntityIdOptions = new HashMap<>();

  /**
   * Task-specific option overrides keyed by task type then entity name. Used when the same entity type
   * needs a different option list per task. Takes precedence over {@link #sharedEntityIdOptions} for the
   * same entity. Not serialized; it is a staging map composed into {@link #entityIdOptions}.
   */
  @JsonIgnore
  public Map<ITaskType, Map<String, List<ValueKeyHtmlSelectOptions>>> taskEntityIdOptions = new HashMap<>();

  /**
   * Registers options that apply to every task allowing the given entity.
   *
   * @param entity  the entity class simple name
   * @param options the id-name pairs to offer for that entity
   */
  public void putSharedOptions(String entity, List<ValueKeyHtmlSelectOptions> options) {
    sharedEntityIdOptions.put(entity, options);
  }

  /**
   * Registers options for a single task and entity, overriding any shared options for that entity.
   *
   * @param taskType the task the options apply to
   * @param entity   the entity class simple name
   * @param options  the id-name pairs to offer for that entity within that task
   */
  public void putTaskOptions(ITaskType taskType, String entity, List<ValueKeyHtmlSelectOptions> options) {
    taskEntityIdOptions.computeIfAbsent(taskType, _ -> new HashMap<>()).put(entity, options);
  }

  @Schema(description = """
      Highest task type id_task value that a user is allowed to create. Task types with a value less than or
      equal to this threshold are offered in the create-task form; task types above it are system tasks that
      cannot be user-created (see the 80+ system band in TaskTypeBase / TaskTypeExtended).""")
  public int maxUserCreateTask = 79;
  @Schema(description = "Maximum number of days in the future that a task can be scheduled for execution")
  public int maxDaysInFuture = 32;
}
