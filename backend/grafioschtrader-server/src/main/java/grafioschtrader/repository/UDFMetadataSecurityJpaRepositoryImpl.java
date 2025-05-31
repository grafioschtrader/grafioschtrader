package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.dynamic.model.DataType;
import grafiosch.entities.Globalparameters;
import grafiosch.entities.UDFData;
import grafiosch.entities.User;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.RepositoryHelper;
import grafiosch.repository.UDFMetadataBase;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.dto.FieldDescriptorInputAndShowExtendedSecurity;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.reports.udfalluserfields.IUDFForEveryUser;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;

public class UDFMetadataSecurityJpaRepositoryImpl extends UDFMetadataBase<UDFMetadataSecurity>
    implements UDFMetadataSecurityJpaRepositoryCustom {

  private final UDFMetadataSecurityJpaRepository uMetaSecurityRepository;
  private final SecurityJpaRepository securityJpaRepository;
  private final GlobalparametersJpaRepository globalparametersJpaRepository;

  public UDFMetadataSecurityJpaRepositoryImpl(@Lazy UDFMetadataSecurityJpaRepository uMetaSecurityRepository,
      SecurityJpaRepository securityJpaRepository, GlobalparametersJpaRepository globalparametersJpaRepository) {
    this.uMetaSecurityRepository = uMetaSecurityRepository;
    this.securityJpaRepository = securityJpaRepository;
    this.globalparametersJpaRepository = globalparametersJpaRepository;

    UDFData.UDF_GENERAL_ENTITIES.add(Currencypair.class);
    UDFData.UDF_GENERAl_AND_SPECIAL_ENTITIES.add(Security.class);
    UDFData.UDF_GENERAl_AND_SPECIAL_ENTITIES.addAll(UDFData.UDF_GENERAL_ENTITIES);
  }

  @Override
  public UDFMetadataSecurity saveOnlyAttributes(final UDFMetadataSecurity uDFMetadataSecurity,
      final UDFMetadataSecurity existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws Exception {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    UiOrderDescriptionCount uodc = uMetaSecurityRepository.countUiOrderAndDescription(
        new int[] { BaseConstants.UDF_ID_USER, user.getIdUser() }, uDFMetadataSecurity.getUiOrder(),
        uDFMetadataSecurity.getDescription());
    uniqueDescUiOrderCheck(uodc, uDFMetadataSecurity, existingEntity);
    uDFMetadataSecurity.checkFieldSize();

    return RepositoryHelper.saveOnlyAttributes(uMetaSecurityRepository, uDFMetadataSecurity, existingEntity,
        updatePropertyLevelClasses);
  }

  @Override
  public void recreateUDFFieldsForEveryUser(List<IUDFForEveryUser> uDFForEveryUser) {
    Set<IUDFForEveryUser> udfForEveryUserSet = uDFForEveryUser.stream().filter(u -> u.mayRunInBackground())
        .collect(Collectors.toSet());
    Set<UDFMetadataSecurity> udfMSSet = uMetaSecurityRepository.getByUdfSpecialTypeInAndIdUser(
        udfForEveryUserSet.stream().map(u -> u.getUDFSpecialType().getValue()).collect(Collectors.toSet()), 0);
    Date now = new Date();
    // It is possible that two user-defined fields have the same selection criteria.
    // This means that the second query can be avoided and processing can be carried out on the securities that have
    // already been selected.
    Map<Integer, SecuritycurrencyUDFGroup> cacheSecurites = new HashMap<>();

    Optional<Globalparameters> globalparamUDFOpt = globalparametersJpaRepository
        .findById(GlobalParamKeyDefault.GLOB_KEY_UDF_GENERAL_RECREATE);
    Globalparameters globalparameter = globalparamUDFOpt
        .orElseGet(() -> new Globalparameters(GlobalParamKeyDefault.GLOB_KEY_UDF_GENERAL_RECREATE));
    boolean recreateUDF = globalparameter.getPropertyInt() != null && globalparameter.getPropertyInt() == 0;

    for (IUDFForEveryUser udfEveryUser : udfForEveryUserSet) {
      UDFMetadataSecurity uDFMetadataSecurity = udfMSSet.stream()
          .filter(u -> u.getUdfSpecialType() == udfEveryUser.getUDFSpecialType()).findFirst().get();
      SecuritycurrencyUDFGroup scUDFGroup = cacheSecurites
          .get(calcHash(uDFMetadataSecurity.getCategoryTypes(), uDFMetadataSecurity.getSpecialInvestmentInstruments()));
      if (scUDFGroup == null) {
        List<Security> securities = securityJpaRepository
            .findByAssetClass_CategoryTypeInAndAssetClass_SpecialInvestmentInstrumentInAndActiveToDateAfterAndIdTenantPrivateIsNull(
                uDFMetadataSecurity.getCategoryTypeEnums().stream().map(c -> c.getValue()).collect(Collectors.toSet()),
                uDFMetadataSecurity.getSpecialInvestmentInstrumentEnums().stream().map(c -> c.getValue())
                    .collect(Collectors.toSet()),
                now);
        List<SecuritycurrencyPosition<Security>> securityPositionList = securities.stream()
            .map(security -> new SecuritycurrencyPosition<Security>(security)).collect(Collectors.toList());
        scUDFGroup = new SecuritycurrencyUDFGroup(securityPositionList);
        cacheSecurites.put(
            calcHash(uDFMetadataSecurity.getCategoryTypes(), uDFMetadataSecurity.getSpecialInvestmentInstruments()),
            scUDFGroup);
      }
      udfEveryUser.addUDFForEveryUser(scUDFGroup, recreateUDF);
    }
    globalparameter.setPropertyInt(0);
    globalparametersJpaRepository.save(globalparameter);
  }

  private int calcHash(long value1, long value2) {
    long combinedValue = (value1 << 32) | (value2 & 0xFFFFFFFFL);
    return (int) (combinedValue ^ (combinedValue >>> 32));
  }

  @Override
  public List<FieldDescriptorInputAndShowExtendedSecurity> getFieldDescriptorByIdUserAndEveryUserExcludeDisabled(
      Integer idUser) {
    List<FieldDescriptorInputAndShowExtendedSecurity> fDiscriptor = new ArrayList<>();
    List<UDFMetadataSecurity> udfMetaDataList = uMetaSecurityRepository
        .getAllByIdUserInOrderByUiOrderExcludeDisabled(idUser);
    udfMetaDataList.forEach(um -> {
      Double[] minMaxValue = um.getFieldLength();
      fDiscriptor.add(new FieldDescriptorInputAndShowExtendedSecurity(um.getCategoryTypeEnums(),
          um.getSpecialInvestmentInstrumentEnums(), BaseConstants.UDF_FIELD_PREFIX + um.getIdUDFMetadata(),
          um.getDescription(), um.getDescriptionHelp(), um.getUiOrder(),
          DataType.getDataType(um.getUdfDataType().getValue()), minMaxValue[0], minMaxValue[1],
          um.getUdfSpecialType() == null ? null : um.getUdfSpecialType().getValue(), um.getIdUser()));
    });
    return fDiscriptor;
  }

  @Transactional
  public int delEntityWithUserId(Integer id, Integer idUser) {
    return uMetaSecurityRepository.deleteByIdUDFMetadataAndIdUser(id, idUser);
  }

  @Override
  public List<UDFMetadataSecurity> getMetadataByUserAndEntityAndIdEntity(Integer idUser, String entity,
      Integer idEntity) {
    Security security = securityJpaRepository.getReferenceById(idEntity);
    Assetclass assetclass = security.getAssetClass();
    List<UDFMetadataSecurity> udfMetadata = uMetaSecurityRepository
        .getAllByIdUserInAndUiOrderLessThanOrderByUiOrder(new int[] { idUser }, BaseConstants.MAX_USER_UI_ORDER_VALUE);
    return udfMetadata.stream()
        .filter(u -> u.getSpecialInvestmentInstrumentEnums().contains(assetclass.getSpecialInvestmentInstrument())
            && u.getCategoryTypeEnums().contains(assetclass.getCategoryType()))
        .collect(Collectors.toList());
  }

  @Override
  public List<String> getSupportedEntities() {
    return List.of(Security.class.getSimpleName());
  }
}
