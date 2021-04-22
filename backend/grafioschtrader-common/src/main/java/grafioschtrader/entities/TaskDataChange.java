package grafioschtrader.entities;

import java.time.LocalDateTime;
import java.util.Objects;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import grafioschtrader.types.ProgressStateType;
import grafioschtrader.types.TaskType;

/**
 * 
 * @author Hugo Graf
 *
 */
@Entity
@Table(name = TaskDataChange.TABNAME)
public class TaskDataChange {
  public byte getIdTask() {
    return idTask;
  }

  public static final String TABNAME = "task_data_change";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_task_data_change")
  protected Integer idTaskDataChange;

  @Column(name = "id_task")
  protected byte idTask;

  @Column(name = "execution_priority")
  private short executionPriority;

  /**
   * Name of the entity which has been changed
   */
  @Column(name = "entity")
  private String entity;

  /**
   * The id of the entity which has been changed
   */
  @Column(name = "id_entity")
  private Integer idEntity;

  @Column(name = "creation_time")
  private LocalDateTime creationTime;

  @Column(name = "earliest_start_time")
  private LocalDateTime earliestStartTime;

  /**
   * Start time of the execution
   */
  @Column(name = "exec_start_time")
  private LocalDateTime execStartTime;

  /**
   * End time of the execution
   */
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

  public TaskDataChange() {
  }

  public TaskDataChange(TaskType taskType, short executionPriority, LocalDateTime earliestStartTime) {
    this(taskType, executionPriority, earliestStartTime, null);
  }

  public TaskDataChange(TaskType taskType, short executionPriority, LocalDateTime earliestStartTime, Integer idEntity) {
    this(taskType, executionPriority, earliestStartTime, idEntity, null);
  }

  public TaskDataChange(TaskType taskType, short executionPriority, LocalDateTime earliestStartTime, Integer idEntity,
      String entity) {
    this.idTask = taskType.getValue();
    this.executionPriority = executionPriority;
    this.earliestStartTime = earliestStartTime;
    this.idEntity = idEntity;
    this.creationTime = LocalDateTime.now();
    this.progressStateType = ProgressStateType.WAITING.getValue();
    this.entity = entity;
  }

  public TaskDataChange(TaskType taskType, short executionPriority) {
    this(taskType, executionPriority, LocalDateTime.now());
  }

  public short getExecutionPriority() {
    return executionPriority;
  }

  public TaskType getTaskType() {
    return TaskType.getTaskTypeByValue(idTask);
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
