package grafioschtrader.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.rest.UpdateCreateJpaRepository;
import jakarta.transaction.Transactional;

public interface TaskDataChangeJpaRepository extends JpaRepository<TaskDataChange, Integer>,
    TaskDataChangeJpaRepositoryCustom, UpdateCreateJpaRepository<TaskDataChange> {

  Optional<TaskDataChange> findTopByProgressStateTypeAndEarliestStartTimeLessThanEqualOrderByExecutionPriorityAscCreationTimeAsc(
      byte progressState, LocalDateTime earliestStartTime);

  Optional<TaskDataChange> findByIdTaskAndIdEntityAndProgressStateType(byte idTask, Integer idEntity,
      byte progressStateType);

  @Transactional
  long removeByIdTaskDataChangeAndProgressStateTypeNot(Integer idTaskDataChange, byte progressStateType);
  
  long removeByIdTask(byte idTask);
  
  long removeByExecEndTimeBefore(LocalDateTime dateTime);
  
  
  @Query(nativeQuery = true)
  Stream<IdSecurityInfo>getAllTaskDataChangeSecurityInfoWithId();
  
  
  @Modifying
  @Transactional
  @Query("UPDATE TaskDataChange t SET t.progressStateType = ?2 WHERE t.progressStateType = ?1")
  int changeFromToProgressState(byte fromState, byte toState);
  
  public static interface IdSecurityInfo {
    public Integer getIdSecuritycurrency();
    public String getTooltip();
  }
}
