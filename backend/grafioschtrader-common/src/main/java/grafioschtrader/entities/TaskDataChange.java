package grafioschtrader.entities;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import grafioschtrader.types.ProgressStateType;
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;


@Entity
@Table(name = TaskDataChange.TABNAME)
@Schema(description = "Entity that contains the information for background processing")
public class TaskDataChange extends BaseID {
  public static final String TABNAME = "task_data_change";
  public static final int MAX_SIZE_FAILED_STRACK_TRACE = 4096;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_task_data_change")
  protected Integer idTaskDataChange;

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

  @Column(name = "creation_time")
  @JsonFormat(pattern = GlobalConstants.STANDARD_LOCAL_DATE_TIME_SECOND)
  private LocalDateTime creationTime;

  @Column(name = "earliest_start_time")
  @JsonFormat(pattern = GlobalConstants.STANDARD_LOCAL_DATE_TIME_SECOND)
  private LocalDateTime earliestStartTime;

  @Schema(description = "Start time of the execution")
  @JsonFormat(pattern = GlobalConstants.STANDARD_LOCAL_DATE_TIME_SECOND)
  @Column(name = "exec_start_time")
  private LocalDateTime execStartTime;

  @Schema(description = "End time of the execution")
  @JsonFormat(pattern = GlobalConstants.STANDARD_LOCAL_DATE_TIME_SECOND)
  @Column(name = "exec_end_time")
  private LocalDateTime execEndTime;

  @Column(name = "old_value_varchar")
  private String oldValueString;

  @Column(name = "old_value_number")
  private Double oldValueNumber;

  @Column(name = "progress_state")
  private byte progressStateType;

  @Column(name = "failed_message_code")
  private String failedMessageCode;

  @Size(max = MAX_SIZE_FAILED_STRACK_TRACE)
  @Column(name = "failed_stack_trace")
  private String failedStackTrace;

  public TaskDataChange() {
  }

  public TaskDataChange(TaskType taskType, TaskDataExecPriority executionPriority, LocalDateTime earliestStartTime) {
    this(taskType, executionPriority, earliestStartTime, null, null);
  }

  public TaskDataChange(TaskType taskType, TaskDataExecPriority executionPriority, LocalDateTime earliestStartTime,
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

  public TaskDataChange(TaskType taskType, TaskDataExecPriority executionPriority) {
    this(taskType, executionPriority, LocalDateTime.now());
  }

  public TaskDataExecPriority getExecutionPriority() {
    return TaskDataExecPriority.getTaskDataExecPriorityByValue(executionPriority);
  }

  public void setExecutionPriority(TaskDataExecPriority executionPriority) {
    this.executionPriority = executionPriority.getValue();
  }

  public TaskType getIdTask() {
    return TaskType.getTaskTypeByValue(idTask);
  }

  public void setIdTask(TaskType idTask) {
    this.idTask = idTask.getValue();
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
