package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.RepositoryHelper;
import grafiosch.repository.UDFMetadataBase;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.dto.FieldDescriptorInputAndShowExtendedSecurity;
import grafioschtrader.dto.UDFFillSourceOption;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.reports.udfalluserfields.IUDFForEveryUser;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.udffill.UDFFillService;
import grafioschtrader.udffill.UDFFillSources;
import grafioschtrader.udffill.UDFFillTaskQueue;
import grafioschtrader.udffill.resolver.UDFValueResolverRegistry;

public class UDFMetadataSecurityJpaRepositoryImpl extends UDFMetadataBase<UDFMetadataSecurity>
    implements UDFMetadataSecurityJpaRepositoryCustom {

  private final UDFMetadataSecurityJpaRepository uMetaSecurityRepository;
  private final SecurityJpaRepository securityJpaRepository;
  private final GlobalparametersJpaRepository globalparametersJpaRepository;
  private final UDFDataFillJpaRepository udfDataFillJpaRepository;
  private final UDFFillTaskQueue udfFillTaskQueue;
  private final UDFFillSources udfFillSources;
  private final UDFValueResolverRegistry resolverRegistry;

  public UDFMetadataSecurityJpaRepositoryImpl(@Lazy UDFMetadataSecurityJpaRepository uMetaSecurityRepository,
      SecurityJpaRepository securityJpaRepository, GlobalparametersJpaRepository globalparametersJpaRepository,
      UDFDataFillJpaRepository udfDataFillJpaRepository, UDFFillTaskQueue udfFillTaskQueue,
      UDFFillSources udfFillSources, UDFValueResolverRegistry resolverRegistry) {
    this.uMetaSecurityRepository = uMetaSecurityRepository;
    this.securityJpaRepository = securityJpaRepository;
    this.globalparametersJpaRepository = globalparametersJpaRepository;
    this.udfDataFillJpaRepository = udfDataFillJpaRepository;
    this.udfFillTaskQueue = udfFillTaskQueue;
    this.udfFillSources = udfFillSources;
    this.resolverRegistry = resolverRegistry;

    UDFData.UDF_GENERAL_ENTITIES.add(Currencypair.class);
    UDFData.UDF_GENERAl_AND_SPECIAL_ENTITIES.add(Security.class);
    UDFData.UDF_GENERAl_AND_SPECIAL_ENTITIES.addAll(UDFData.UDF_GENERAL_ENTITIES);
  }

  @Override
  public UDFMetadataSecurity saveOnlyAttributes(final UDFMetadataSecurity uDFMetadataSecurity,
      final UDFMetadataSecurity existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws Exception {
    validateFill(uDFMetadataSecurity, existingEntity);
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    UiOrderDescriptionCount uodc = uMetaSecurityRepository.countUiOrderAndDescription(
        new int[] { BaseConstants.UDF_ID_USER, user.getIdUser() }, uDFMetadataSecurity.getUiOrder(),
        uDFMetadataSecurity.getDescription());
    uniqueDescUiOrderCheck(uodc, uDFMetadataSecurity, existingEntity);
    uDFMetadataSecurity.checkFieldSize();

    UDFFillSource previousSource = existingEntity == null ? null : existingEntity.getFillSource();
    boolean fillChanged = existingEntity == null ? uDFMetadataSecurity.isAutomaticallyFilled()
        : previousSource != uDFMetadataSecurity.getFillSource()
            || !Objects.equals(existingEntity.getFillRule(), uDFMetadataSecurity.getFillRule());
    boolean scopeChanged = existingEntity != null
        && (existingEntity.getCategoryTypes() != uDFMetadataSecurity.getCategoryTypes()
            || existingEntity.getSpecialInvestmentInstruments() != uDFMetadataSecurity.getSpecialInvestmentInstruments()
            || !Objects.equals(existingEntity.getFieldSize(), uDFMetadataSecurity.getFieldSize()));
    if (existingEntity == null) {
      uDFMetadataSecurity.setFillRuleVersion(0);
    } else if (fillChanged) {
      // The version is maintained by the server only, the copy of the updatable properties never touches it
      existingEntity.setFillRuleVersion(existingEntity.getFillRuleVersion() + 1);
    }
    UDFMetadataSecurity saved = RepositoryHelper.saveOnlyAttributes(uMetaSecurityRepository, uDFMetadataSecurity,
        existingEntity, updatePropertyLevelClasses);
    applyFillTriggers(saved, previousSource, fillChanged || scopeChanged);
    return saved;
  }

  /**
   * Validates the fill source and rule of a field. A source other than MANUAL is refused for user 0 and for a field
   * with special type, and must be offered by this instance for the data type of the field; a template rule must parse,
   * a resolver rule names an existing resolver for the data type and is stored with its normalised argument. For MANUAL
   * the rule is cleared, and the fill period is kept for RESOLVER and LLM only.
   */
  private void validateFill(UDFMetadataSecurity entity, UDFMetadataSecurity existingEntity) {
    UDFFillSource fillSource = entity.getFillSource();
    if (fillSource != UDFFillSource.RESOLVER && fillSource != UDFFillSource.LLM) {
      // A template is evaluated locally on every run, so a period means nothing for it
      entity.setFillPeriod(null);
    }
    if (fillSource == UDFFillSource.MANUAL) {
      entity.setFillRule(null);
      return;
    }
    UDFMetadataSecurity reference = existingEntity == null ? entity : existingEntity;
    if (!UDFFillService.isUserFillable(entity) || !UDFFillService.isUserFillable(reference)) {
      throw new DataViolationException("fill.source", "gt.udf.fill.source.not.allowed", null);
    }
    Optional<UDFFillSourceOption> offered = udfFillSources.getOffered(fillSource);
    if (offered.isEmpty()) {
      throw new DataViolationException("fill.source", "gt.udf.fill.source.not.offered",
          new Object[] { fillSource.name() });
    }
    if (!offered.get().udfDataTypes().contains(reference.getUdfDataType())) {
      throw new DataViolationException("fill.source", "gt.udf.fill.source.data.type",
          new Object[] { fillSource.name(), reference.getUdfDataType().name() });
    }
    if (fillSource == UDFFillSource.TEMPLATE) {
      UDFFillService.parseForSave(entity.getFillRule());
    } else if (fillSource == UDFFillSource.RESOLVER) {
      entity.setFillRule(resolverRegistry.normalizeRule(entity.getFillRule(), reference.getUdfDataType()));
    } else if (fillSource == UDFFillSource.LLM) {
      // The rule of a language model field is the context text the user writes for the model
      if (entity.getFillRule() == null || entity.getFillRule().isBlank()) {
        throw new DataViolationException("fill.rule", "gt.udf.fill.llm.context.missing", null);
      }
      entity.setFillRule(entity.getFillRule().strip());
    }
  }

  /**
   * Enqueues a fill run when the fill configuration or the scope of an automatically filled field changed, and drops
   * the provenance of every cell when the field is switched to MANUAL: the values stay and are manual from then on.
   */
  private void applyFillTriggers(UDFMetadataSecurity saved, UDFFillSource previousSource, boolean changed) {
    if (saved.isAutomaticallyFilled()) {
      // Every run of a language model is started by the user, saving such a field enqueues nothing
      if (changed && saved.getFillSource() != UDFFillSource.LLM) {
        udfFillTaskQueue.enqueue(saved.getIdUDFMetadata(), TaskDataExecPriority.PRIO_NORMAL);
      }
    } else if (previousSource != null && previousSource != UDFFillSource.MANUAL) {
      udfDataFillJpaRepository.deleteByIdUDFMetadata(saved.getIdUDFMetadata());
    }
  }

  @Override
  public void recreateUDFFieldsForEveryUser(List<IUDFForEveryUser> uDFForEveryUser) {
    Set<IUDFForEveryUser> udfForEveryUserSet = uDFForEveryUser.stream().filter(u -> u.mayRunInBackground())
        .collect(Collectors.toSet());
    Set<UDFMetadataSecurity> udfMSSet = uMetaSecurityRepository.getByUdfSpecialTypeInAndIdUser(
        udfForEveryUserSet.stream().map(u -> u.getUDFSpecialType().getValue()).collect(Collectors.toSet()), 0);
    LocalDate now = LocalDate.now();
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
