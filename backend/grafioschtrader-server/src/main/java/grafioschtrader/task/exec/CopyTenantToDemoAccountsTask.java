package grafioschtrader.task.exec;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Globalparameters;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.CopyTenantService;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.repository.UserJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;

@Component
public class CopyTenantToDemoAccountsTask implements ITask {

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private CopyTenantService copyTenantService;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;
    
  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository;

  @Autowired
  private HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository;

  @Autowired
  private HoldCashaccountBalanceJpaRepository holdCashaccountBalanceJpaRepository;

  
  @Value("${gt.demo.account.pattern}")
  private String demoAccountPattern;

  @Override
  public TaskType getTaskType() {
    return TaskType.COPY_DEMO_ACCOUNTS;
  }
    
  
  @Scheduled(cron = "${gt.demo.account.tenant.copy}", zone = "UTC")
  public void catchAllUpSecuritycurrencyHistoryquote() {
    TaskDataChange taskDataChange =  new TaskDataChange(TaskType.COPY_DEMO_ACCOUNTS, (short) 35);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  @Transactional
  public void doWork(Integer idEntity, String entity) {
    Integer[] demoIdTenants = userJpaRepository.findIdTenantByMailPattern(demoAccountPattern);

    Optional<Globalparameters> sourceIdTenantOpt = globalparametersJpaRepository
        .findById(Globalparameters.GLOB_KEY_SOURCE_DEMO_ID_TENANT);

    if (sourceIdTenantOpt.isPresent()) {
      for (Integer targetIdTenant : demoIdTenants) {
        copyTenantService.copyTenant(sourceIdTenantOpt.get().getPropertyInt(), targetIdTenant);
        taskDataChangeRepository.save(new TaskDataChange(TaskType.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT, (short) 22,
            LocalDateTime.now().plusMinutes(1), targetIdTenant, Tenant.TABNAME));
      }
    }

  }

}
