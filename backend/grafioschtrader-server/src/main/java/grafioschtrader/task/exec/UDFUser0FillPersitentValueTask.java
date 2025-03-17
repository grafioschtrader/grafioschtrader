package grafioschtrader.task.exec;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.reports.udfalluserfields.IUDFForEveryUser;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Some global user-defined fields have a longer validity period. This can make
 * the content persistent. In addition, the effort required to create their
 * content is sometimes time-consuming, e.g. because data suppliers have to be
 * contacted. It therefore makes sense to update them daily.
 */
@Service
public class UDFUser0FillPersitentValueTask implements ITask {

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

//  @Autowired
//  private SecurityJpaRepository securityJpaRepository;
//
   @Autowired
   private UDFMetadataSecurityJpaRepository uMetaSecurityRepository;
//
//  @Autowired
//  private GlobalparametersJpaRepository globalparametersJpaRepository;
//  
  @Autowired
  private List<IUDFForEveryUser> uDFForEveryUser;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.UDF_USER_0_FILL_PERSISTENT_FIELDS_WITH_VALUES;
  }

  @Scheduled(cron = "${gt.user0.persists.filds}", zone = BaseConstants.TIME_ZONE)
  public void createUDFUser0FillPersitentValueTask() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_VERY_LOW);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public boolean removeAllOtherJobsOfSameTask() {
    return true;
  }
  
  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    uMetaSecurityRepository.recreateUDFFieldsForEveryUser(uDFForEveryUser);
  }
  

//  @Override
//  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
//    Set<IUDFForEveryUser> udfForEveryUserSet = uDFForEveryUser.stream().filter(u -> u.mayRunInBackground())
//        .collect(Collectors.toSet());
//    Set<UDFMetadataSecurity> udfMSSet = uMetaSecurityRepository.getByUdfSpecialTypeInAndIdUser(
//        udfForEveryUserSet.stream().map(u -> u.getUDFSpecialType().getValue()).collect(Collectors.toSet()), 0);
//    Date now = new Date();
//    // It is possible that two user-defined fields have the same selection criteria.  
//    // This means that the second query can be avoided and processing can be carried out on the securities that have already been selected.
//    Map<Integer, SecuritycurrencyUDFGroup> cacheSecurites = new HashMap<>();
//   
//    Optional<Globalparameters> globalparamUDFOpt = globalparametersJpaRepository
//        .findById(GlobalParamKeyDefault.GLOB_KEY_UDF_GENERAL_RECREATE);
//    Globalparameters globalparameter = globalparamUDFOpt
//        .orElseGet(() -> new Globalparameters(GlobalParamKeyDefault.GLOB_KEY_UDF_GENERAL_RECREATE));
//    boolean recreateUDF = globalparameter.getPropertyInt() != null && globalparameter.getPropertyInt() == 0;
//    
//    for (IUDFForEveryUser udfEveryUser : udfForEveryUserSet) {
//      UDFMetadataSecurity uDFMetadataSecurity = udfMSSet.stream()
//          .filter(u -> u.getUdfSpecialType() == udfEveryUser.getUDFSpecialType()).findFirst().get();
//      SecuritycurrencyUDFGroup scUDFGroup = cacheSecurites
//          .get(calcHash(uDFMetadataSecurity.getCategoryTypes(), uDFMetadataSecurity.getSpecialInvestmentInstruments()));
//      if (scUDFGroup == null) {
//        List<Security> securities = securityJpaRepository
//            .findByAssetClass_CategoryTypeInAndAssetClass_SpecialInvestmentInstrumentInAndActiveToDateAfterAndIdTenantPrivateIsNull(
//                uDFMetadataSecurity.getCategoryTypeEnums().stream().map(c -> c.getValue()).collect(Collectors.toSet()),
//                uDFMetadataSecurity.getSpecialInvestmentInstrumentEnums().stream().map(c -> c.getValue())
//                    .collect(Collectors.toSet()),
//                now);
//        List<SecuritycurrencyPosition<Security>> securityPositionList = securities.stream()
//            .map(security -> new SecuritycurrencyPosition<Security>(security)).collect(Collectors.toList());
//        scUDFGroup = new SecuritycurrencyUDFGroup(securityPositionList);
//        cacheSecurites.put(
//            calcHash(uDFMetadataSecurity.getCategoryTypes(), uDFMetadataSecurity.getSpecialInvestmentInstruments()),
//            scUDFGroup);
//      }
//      udfEveryUser.addUDFForEveryUser(scUDFGroup, recreateUDF);
//    }
//    globalparameter.setPropertyInt(0);
//    globalparametersJpaRepository.save(globalparameter);    
//  }
//  
  

  private int calcHash(long value1, long value2) {
    long combinedValue = (value1 << 32) | (value2 & 0xFFFFFFFFL);
    return (int) (combinedValue ^ (combinedValue >>> 32));
  }

}
