package grafioschtrader.task.exec;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.TaskDataChangeJpaRepository;

@Service
@Transactional
public class TaskDataChangePurgeTask {

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Scheduled(cron = "${gt.purge.task.data}", zone = BaseConstants.TIME_ZONE)
  public void purgeExpired() {
    taskDataChangeJpaRepository.removeByExecEndTimeBefore(
        LocalDateTime.now().minusDays(globalparametersJpaRepository.getTaskDataDaysPreserve()));
  }

}
