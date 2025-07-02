package grafiosch.entities;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSetter;

import grafiosch.BaseConstants;
import grafiosch.types.ITaskType;
import grafiosch.types.ProgressStateType;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

/**
 * Entity representing background task execution data and status.
 * 
 * <p>Tracks the lifecycle of background tasks from creation through completion,
 * including execution timing, progress state, and error information.</p>
 */
@Schema(description = "Entity that contains the information for background processing")
@Entity
@Table(name = TaskDataChange.TABNAME)
public class TaskDataChange extends BaseID<Integer> {
  public static final String TABNAME = "task_data_change";
  
  /** Maximum size for failed stack trace field */
  public static final int MAX_SIZE_FAILED_STRACK_TRACE = 4096;

  /**
   * Contains the background work tasks. More of these tasks can be added by a dependent module.
   */
  public static final EnumRegistry<Byte, ITaskType> TASK_TYPES_REGISTRY = new EnumRegistry<>(TaskTypeBase.values());

  @Schema(description = "Unique identifier for the task")
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_task_data_change")
  protected Integer idTaskDataChange;

  @Schema(description = "Task type identifier")
  @Column(name = "id_task")
  protected byte idTask;

  @Schema(description = "Priority for the execution of this background job")
  @Column(name = "execution_priority")
  private byte executionPriority;

  @Schema(description = "Name of the entity which has been changed")
  @Column(name = "entity")
  private String entity;

  @Schema(description = "The id of the entity which has been changed")
  @Column(name = "id_entity")
  private Integer idEntity;

  @Schema(description = "Task creation timestamp")
  @Column(name = "creation_time")
  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND)
  private LocalDateTime creationTime;

  @Schema(description = "Earliest allowed execution time")
  @Column(name = "earliest_start_time")
  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND)
  private LocalDateTime earliestStartTime;

  @Schema(description = "Actual execution start time")
  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND)
  @Column(name = "exec_start_time")
  private LocalDateTime execStartTime;

  @Schema(description = "Execution completion time")
  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND)
  @Column(name = "exec_end_time")
  private LocalDateTime execEndTime;

  @Schema(description = """
      Additional information may be required when creating certain background jobs. 
      This information is then requested when the job is executed. Both strings and date values can be stored here.""")
  @Column(name = "old_value_varchar")
  private String oldValueString;

  @Schema(description = """
      Additional information may be required when creating certain background jobs. 
      This information is then requested when the job is executed. Numerical values can be stored here.""")
  @Column(name = "old_value_number")
  private Double oldValueNumber;

  @Schema(description = "Current task execution status")
  @Column(name = "progress_state")
  private byte progressStateType;

  @Schema(description = "Error message key if task failed")
  @Column(name = "failed_message_code")
  private String failedMessageCode;

  @Schema(description = "Stack trace of failure if task failed")
  @Size(max = MAX_SIZE_FAILED_STRACK_TRACE)
  @Column(name = "failed_stack_trace")
  private String failedStackTrace;

  public TaskDataChange() {
  }

  /**
   * Creates a new task with specified parameters and immediate start time.
   * 
   * @param taskType the type of task to execute
   * @param executionPriority the execution priority
   * @param earliestStartTime when the task can start execution
   */
  public TaskDataChange(ITaskType taskType, TaskDataExecPriority executionPriority, LocalDateTime earliestStartTime) {
    this(taskType, executionPriority, earliestStartTime, null, null);
  }

  /**
   * Creates a new task with full parameters.
   * 
   * @param taskType the type of task to execute
   * @param executionPriority the execution priority
   * @param earliestStartTime when the task can start execution
   * @param idEntity the ID of the entity that triggered this task
   * @param entity the name of the entity that triggered this task
   */
  public TaskDataChange(ITaskType taskType, TaskDataExecPriority executionPriority, LocalDateTime earliestStartTime,
      Integer idEntity, String entity) {
    this.idTask = taskType.getValue();
    this.executionPriority = executionPriority.getValue();
    this.earliestStartTime = earliestStartTime;
    this.idEntity = idEntity;
    this.creationTime = LocalDateTime.now();
    this.progressStateType = ProgressStateType.PROG_WAITING.getValue();
    this.entity = entity;
  }

  public Integer getIdTaskDataChange() {
    return idTaskDataChange;
  }

  public void setIdTaskDataChange(Integer idTaskDataChange) {
    this.idTaskDataChange = idTaskDataChange;
  }

  public TaskDataChange(ITaskType taskType, TaskDataExecPriority executionPriority) {
    this(taskType, executionPriority, LocalDateTime.now());
  }

  public TaskDataExecPriority getExecutionPriority() {
    return TaskDataExecPriority.getTaskDataExecPriorityByValue(executionPriority);
  }

  public void setExecutionPriority(TaskDataExecPriority executionPriority) {
    this.executionPriority = executionPriority.getValue();
  }

  public ITaskType getIdTask() {
    return TASK_TYPES_REGISTRY.getTypeByValue(idTask);
  }

  public void setIdTask(ITaskType idTask) {
    this.idTask = idTask.getValue();
  }

  // Change the setter to accept a String from the JSON payload.
  @JsonSetter("idTask")
  public void setIdTask(String idTaskName) {
    ITaskType taskType = TASK_TYPES_REGISTRY.getTypeByName(idTaskName);
    if (taskType == null) {
      throw new IllegalArgumentException("Unknown task type: " + idTaskName);
    }
    this.idTask = taskType.getValue();
  }

  public LocalDateTime getEarliestStartTime() {
    return earliestStartTime;
  }

  public void setEarliestStartTime(LocalDateTime earliestStartTime) {
    this.earliestStartTime = earliestStartTime;
  }

  public byte getTaskAsId() {
    return idTask;
  }

  public String getEntity() {
    return entity;
  }

  public void setEntity(String entity) {
    this.entity = entity;
  }

  public Integer getIdEntity() {
    return idEntity;
  }

  public void setIdEntity(Integer idEntity) {
    this.idEntity = idEntity;
  }

  public LocalDateTime getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(LocalDateTime creationTime) {
    this.creationTime = creationTime;
  }

  public LocalDateTime getExecStartTime() {
    return execStartTime;
  }

  public void setExecStartTime(LocalDateTime execStartTime) {
    this.execStartTime = execStartTime;
  }

  public LocalDateTime getExecEndTime() {
    return execEndTime;
  }

  public void setExecEndTime(LocalDateTime execEndTime) {
    this.execEndTime = execEndTime;
  }

  public String getOldValueString() {
    return oldValueString;
  }

  public void setOldValueString(String oldValueString) {
    this.oldValueString = oldValueString;
  }

  public Double getOldValueNumber() {
    return oldValueNumber;
  }

  public void setOldValueNumber(Double oldValueNumber) {
    this.oldValueNumber = oldValueNumber;
  }

  public ProgressStateType getProgressStateType() {
    return ProgressStateType.getProgressStateTypeByValue(progressStateType);
  }

  public void setProgressStateType(ProgressStateType progressStateType) {
    this.progressStateType = progressStateType.getValue();
  }

  public String getFailedMessageCode() {
    return failedMessageCode;
  }

  public void setFailedMessageCode(String failedMessageCode) {
    this.failedMessageCode = failedMessageCode;
  }

  public String getFailedStackTrace() {
    return failedStackTrace;
  }

  public void setFailedStackTrace(String failedStackTrace) {
    this.failedStackTrace = failedStackTrace;
  }

  @Override
  public Integer getId() {
    return idTaskDataChange;
  }

  public Long getExecutionDurationInSeconds() {
    return execStartTime != null && execEndTime != null ? Duration.between(execStartTime, execEndTime).toSeconds()
        : null;
  }

  /**
   * Marks the task as finished with the specified state.
   * Sets the execution times and progress state.
   * 
   * @param startTime the actual start time
   * @param progressStateType the final progress state
   */
  public void finishedJob(LocalDateTime startTime, ProgressStateType progressStateType) {
    this.progressStateType = progressStateType.getValue();
    this.execStartTime = startTime;
    this.execEndTime = LocalDateTime.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TaskDataChange that = (TaskDataChange) o;
    return Objects.equals(idTaskDataChange, that.idTaskDataChange);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idTaskDataChange);
  }

  @Override
  public String toString() {
    return "TaskDataChange [idTaskDataChange=" + idTaskDataChange + ", entity=" + entity + ", idEntity=" + idEntity
        + ", creationTime=" + creationTime + ", oldValueString=" + oldValueString + ", oldValueNumber=" + oldValueNumber
        + ", progressStateType=" + progressStateType + "]";
  }

}
