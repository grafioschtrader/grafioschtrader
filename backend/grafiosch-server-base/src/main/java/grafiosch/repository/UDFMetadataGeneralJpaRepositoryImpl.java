package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.dynamic.model.DataType;
import grafiosch.dynamic.model.FieldDescriptorInputAndShowExtendedGeneral;
import grafiosch.entities.UDFData;
import grafiosch.entities.UDFMetadataGeneral;
import grafiosch.entities.User;

public class UDFMetadataGeneralJpaRepositoryImpl extends UDFMetadataBase<UDFMetadataGeneral>
    implements UDFMetadataGeneralJpaRepositoryCustom {

  @Autowired
  private UDFMetadataGeneralJpaRepository uMetaRepository;

  @Override
  @Transactional
  public UDFMetadataGeneral saveOnlyAttributes(final UDFMetadataGeneral uDFMetadataGeneral,
      final UDFMetadataGeneral existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws Exception {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (existingEntity != null) {
      uDFMetadataGeneral.setEntity(existingEntity.getEntity());
    }
    UiOrderDescriptionCount uodc = uMetaRepository.countUiOrderAndDescription(
        new int[] { BaseConstants.UDF_ID_USER, user.getIdUser() }, uDFMetadataGeneral.getEntity(),
        uDFMetadataGeneral.getUiOrder(), uDFMetadataGeneral.getDescription(), user.getIdUser());
    uniqueDescUiOrderCheck(uodc, uDFMetadataGeneral, existingEntity);
    uDFMetadataGeneral.checkFieldSize();

    applyShareModeChange(uDFMetadataGeneral, existingEntity, uDFMetadataGeneral.getEntity());
    return RepositoryHelper.saveOnlyAttributes(uMetaRepository, uDFMetadataGeneral, existingEntity,
        updatePropertyLevelClasses);
  }

  @Override
  public List<FieldDescriptorInputAndShowExtendedGeneral> getFieldDescriptorByIdUserAndEveryUserForEntity(
      Integer idUser, String entity) {
    List<FieldDescriptorInputAndShowExtendedGeneral> fDiscriptor = new ArrayList<>();
    List<UDFMetadataGeneral> udfMetaDataList = uMetaRepository
        .getAllByIdUserInAndEntityOrderByUiOrder(new int[] { idUser, BaseConstants.UDF_ID_USER }, entity);
    udfMetaDataList = withSubscriptions(udfMetaDataList, idUser, UDFMetadataGeneral.class).stream()
        .filter(f -> f.getEntity().equals(entity)).toList();
    udfMetaDataList.forEach(um -> {
      Double[] minMaxValue = um.getFieldLength();
      var descriptor = new FieldDescriptorInputAndShowExtendedGeneral(
          BaseConstants.UDF_FIELD_PREFIX + um.getIdUDFMetadata(), um.getDescription(), um.getDescriptionHelp(),
          um.getUiOrder(), DataType.getDataType(um.getUdfDataType().getValue()), minMaxValue[0], minMaxValue[1],
          um.getUdfSpecialType() == null ? null : um.getUdfSpecialType().getValue(), um.getIdUser(), um.getEntity());
      descriptor.setValueEditable(Boolean.TRUE.equals(um.getValueEditable()));
      fDiscriptor.add(descriptor);
    });
    return fDiscriptor;
  }

  @Transactional
  public int delEntityWithUserId(Integer id, Integer idUser) {
    return deleteOrOrphan(id, idUser, () -> uMetaRepository.deleteByIdUDFMetadataAndIdUser(id, idUser));
  }

  @Override
  public List<String> getSupportedEntities() {
    return UDFData.UDF_GENERAL_ENTITIES.stream().map(c -> c.getSimpleName()).collect(Collectors.toList());
  }

  @Override
  public List<UDFMetadataGeneral> getMetadataByUserAndEntityAndIdEntity(Integer idUser, String entity,
      Integer idEntity) {
    return withSubscriptions(uMetaRepository.getAllByIdUserInAndEntityOrderByUiOrder(new int[] { idUser }, entity),
        idUser, UDFMetadataGeneral.class).stream().filter(f -> f.getEntity().equals(entity)).toList();
  }

  @Override
  public java.util.Optional<UDFMetadataGeneral> findManagedField(Integer id) {
    return uMetaRepository.findById(id);
  }

  @Override
  public UiOrderDescriptionCount countUiOrderAndDescriptionForUser(Integer idUser, UDFMetadataGeneral field,
      byte order) {
    return uMetaRepository.countUiOrderAndDescription(new int[] { 0, idUser }, field.getEntity(), order,
        field.getDescription(), idUser);
  }

  @Override
  public List<UDFMetadataGeneral> getFieldsForUser(Integer idUser) {
    return withSubscriptions(uMetaRepository.getAllByIdUserInOrderByUiOrder(new int[] { idUser, 0 }), idUser,
        UDFMetadataGeneral.class);
  }

  @Override
  public List<UDFMetadataGeneral> getShared(Integer idUser) {
    return sharedCatalog(idUser, UDFMetadataGeneral.class);
  }
}
