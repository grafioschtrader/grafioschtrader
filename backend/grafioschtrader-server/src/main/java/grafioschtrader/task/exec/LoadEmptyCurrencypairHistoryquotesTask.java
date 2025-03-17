package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.TaskDataChange;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

@Component
public class LoadEmptyCurrencypairHistoryquotesTask implements ITask {

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.LOAD_EMPTY_CURRENCYPAIR_HISTORYQUOTES;
  }

  @Override
  public List<String> getAllowedEntities() {
    return Arrays.asList(Currencypair.class.getSimpleName());
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) {
    currencypairJpaRepository.fillEmptyCurrencypair(taskDataChange.getIdEntity());
  }

}
