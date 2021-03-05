package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.ProposeUserTask;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface ProposeUserTaskJpaRepository extends JpaRepository<ProposeUserTask, Integer>,
    ProposeUserTaskJpaRepositoryCustom, UpdateCreateJpaRepository<ProposeUserTask> {

  List<ProposeUserTask> findByIdTargetUserAndUserTaskType(Integer idTargetUser, byte userTaskType);
}
