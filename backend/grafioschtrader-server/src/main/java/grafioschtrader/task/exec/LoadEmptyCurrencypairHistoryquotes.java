package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;

@Component
public class LoadEmptyCurrencypairHistoryquotes implements ITask {

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Override
  public TaskType getTaskType() {
    return TaskType.LOAD_EMPTY_CURRENCYPAIR_HISTORYQOUTES;
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
