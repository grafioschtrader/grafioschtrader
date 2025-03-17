package grafiosch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.ProposeUserTask;
import grafiosch.rest.UpdateCreateJpaRepository;

public interface ProposeUserTaskJpaRepository extends JpaRepository<ProposeUserTask, Integer>,
    ProposeUserTaskJpaRepositoryCustom, UpdateCreateJpaRepository<ProposeUserTask> {

  List<ProposeUserTask> findByIdTargetUserAndUserTaskType(Integer idTargetUser, byte userTaskType);
}
