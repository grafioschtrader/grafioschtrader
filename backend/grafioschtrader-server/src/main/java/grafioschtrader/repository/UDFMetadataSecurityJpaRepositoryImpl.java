package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.FieldDescriptorInputAndShowExtendedSecurity;
import grafioschtrader.dynamic.model.DataType;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.entities.User;

public class UDFMetadataSecurityJpaRepositoryImpl extends UDFMetadataBase<UDFMetadataSecurity>
    implements UDFMetadataSecurityJpaRepositoryCustom {

  @Autowired
  private UDFMetadataSecurityJpaRepository uMetaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;
  
  @Override
  public UDFMetadataSecurity saveOnlyAttributes(final UDFMetadataSecurity uDFMetadataSecurity,
      final UDFMetadataSecurity existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws Exception {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    UiOrderDescriptionCount uodc = uMetaRepository.countUiOrderAndDescription(new int[] { 0, user.getIdUser() },
        uDFMetadataSecurity.getUiOrder(), uDFMetadataSecurity.getDescription());
    uniqueDescUiOrderCheck(uodc, uDFMetadataSecurity, existingEntity);
    uDFMetadataSecurity.checkFieldSize();

    return RepositoryHelper.saveOnlyAttributes(uMetaRepository, uDFMetadataSecurity, existingEntity,
        updatePropertyLevelClasses);
  }

  @Override
  public List<FieldDescriptorInputAndShowExtendedSecurity> getFieldDescriptorByIdUserAndEveryUser(Integer idUser) {
    List<FieldDescriptorInputAndShowExtendedSecurity> fDiscriptor = new ArrayList<>();
    List<UDFMetadataSecurity> udfMetaDataList = uMetaRepository.getAllByIdUserInOrderByUiOrder(new int[] { idUser, 0 });
    udfMetaDataList.forEach(um -> {
      Double[] minMaxValue = um.getFieldLength();
      fDiscriptor.add(new FieldDescriptorInputAndShowExtendedSecurity(um.getCategoryTypeEnums(),
          um.getSpecialInvestmentInstrumentEnums(), GlobalConstants.UDF_FIELD_PREFIX + um.getIdUDFMetadata(),
          um.getDescription(), um.getDescriptionHelp(), um.getUiOrder(),
          DataType.getDataType(um.getUdfDataType().getValue()), minMaxValue[0], minMaxValue[1],
          um.getUdfSpecialType() == null ? null : um.getUdfSpecialType().getValue(), um.getIdUser()));
    });
    return fDiscriptor;
  }

  @Transactional
  public int delEntityWithUserId(Integer id, Integer idUser) {
    return uMetaRepository.deleteByIdUDFMetadataAndIdUser(id, idUser);
  }

  @Override
  public List<UDFMetadataSecurity> getMetadataByUserAndEntityAndIdEntity(Integer idUser, String entity, Integer idEntity) {
    Security security = securityJpaRepository.getReferenceById(idEntity);
    Assetclass assetclass = security.getAssetClass();
    List<UDFMetadataSecurity> udfMetadata = uMetaRepository.getAllByIdUserInOrderByUiOrder(new int[] {idUser});
    
    return udfMetadata.stream().filter( u -> u.getSpecialInvestmentInstrumentEnums().contains(assetclass.getSpecialInvestmentInstrument()) 
        && u.getCategoryTypeEnums().contains(assetclass.getCategoryType())).collect(Collectors.toList());
  }

  @Override
  public List<String> getSupportedEntities() {
    return List.of(Security.class.getSimpleName());
  }
}
