package grafioschtrader.repository;

import org.springframework.beans.factory.annotation.Autowired;

public class TaskDataChangeJpaRepositoryImpl implements TaskDataChangeJpaRepositoryCustom {

  @Autowired
  TaskDataChangeJpaRepository taskDataChangeRepository;

}
