package grafioschtrader.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;

@Component
public class LoadEmptyCurrencypairHistoryquotes implements ITask {

  @Autowired
  CurrencypairJpaRepository currencypairJpaRepository;
  
  @Override
  public TaskType getTaskType() {
    return TaskType.LOAD_EMPTY_CURRENCYPAIR_HISTORYQOUTES;
  }

  @Override
  public void doWork(Integer idEntity, String entity) {
    
    currencypairJpaRepository.fillEmptyCurrencypair(idEntity);
  }
  
}
