package grafioschtrader.task.exec;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.TaskDataChangeJpaRepository;

@Service
@Transactional
public class TaskDataChangePurgeTask {

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Scheduled(cron = "${gt.purge.task.data}", zone = GlobalConstants.TIME_ZONE)
  public void purgeExpired() {
    taskDataChangeJpaRepository.removeByExecEndTimeBefore(
        LocalDateTime.now().minusDays(globalparametersJpaRepository.getTaskDataDaysPreserve()));
  }

}
