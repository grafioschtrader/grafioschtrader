package grafiosch.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
  @Schema(description = "Maximum number of tasks that a single user is allowed to create")
  public int maxUserCreateTask = 30;
  @Schema(description = "Maximum number of days in the future that a task can be scheduled for execution")
  public int maxDaysInFuture = 32;
}
