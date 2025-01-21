package grafioschtrader.task.exec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.User;
import grafioschtrader.repository.CopyTenantService;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.repository.UserJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;

/**
 * Copies the data of one client to other clients. This function can be used for
 * demo user accounts. As visitors can change the data of these user accounts,
 * they should be recopied daily. The source accounts are copied to the target
 * accounts according to the specifications in application.properties and the
 * global settings. Two different source accounts are provided so that, for
 * example, German and English demo user accounts can be served.
 * <ul>
 * <li>gt.demo.account.pattern.de and gt.demo.account.pattern.en in
 * application.properties: This pattern is used to express the target accounts.
 * This pattern is used to search for the target accounts. If no demo user
 * account is desired, only this search pattern in the user's e-mail may not
 * result in a hit.</li>
 * <li>gt.source.demo.idtenant.de and gt.source.demo.idtenant.de in global
 * settings: These are the IDs of the source accounts. If the corresponding
 * source account is not found, no copying takes place</li>.
 * </ul>
 */
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

  @Value("${gt.demo.account.pattern.de}")
  private String demoAccountPatternDE;

  @Value("${gt.demo.account.pattern.en}")
  private String demoAccountPatternEN;

  @Override
  public TaskType getTaskType() {
    return TaskType.COPY_SOURCE_ACCOUNT_TO_DEMO_ACCOUNTS;
  }

  @Scheduled(cron = "${gt.demo.account.tenant.copy}", zone = GlobalConstants.TIME_ZONE)
  public void createCopyTenantToDemoAccountsTask() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_LOW);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  @Transactional
  public void doWork(TaskDataChange taskDataChange) {
    copyTenant(GlobalParamKeyDefault.GLOB_KEY_SOURCE_DEMO_ID_TENANT_DE, demoAccountPatternDE);
    copyTenant(GlobalParamKeyDefault.GLOB_KEY_SOURCE_DEMO_ID_TENANT_EN, demoAccountPatternEN);
  }

  private void copyTenant(String sourceTenantKey, String dap) {
    List<User> targetUsers = userJpaRepository.getUsersByMailPattern(dap);

    
    Optional<User> sourceUserOpt =  globalparametersJpaRepository.findById(sourceTenantKey).map( g ->  
    userJpaRepository.findByIdTenant(g.getPropertyInt())).get();

    if (sourceUserOpt.isPresent()) {
      for (User targetUser : targetUsers) {
        copyTenantService.copyTenant(sourceUserOpt.get(), targetUser);
        taskDataChangeRepository
            .save(new TaskDataChange(TaskType.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT, TaskDataExecPriority.PRIO_NORMAL,
                LocalDateTime.now().plusMinutes(1), targetUser.getIdTenant(), Tenant.class.getSimpleName()));
      }
    }
  }

}
