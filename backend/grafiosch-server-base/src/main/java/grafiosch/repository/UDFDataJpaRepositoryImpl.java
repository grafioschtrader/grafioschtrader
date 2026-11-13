package grafiosch.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.GenericTypeResolver;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.common.UDFSharedValueHelper;
import grafiosch.entities.UDFData;
import grafiosch.entities.UDFData.UDFDataKey;
import grafiosch.entities.UDFDataSharedKey;
import grafiosch.entities.UDFMetadata;
import grafiosch.entities.User;
import grafiosch.entities.projection.IUDFSupport;
import grafiosch.repository.UDFDataJpaRepository.IUDFRepository;
import grafiosch.rest.UpdateCreate;
import grafiosch.types.UDFDataType;
import grafiosch.types.UDFShareMode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

/**
 * Implementation of custom repository operations for UDFData entities. This class provides specialized functionality
 * for creating, updating, and retrieving user-defined field data with proper validation, security checks, and metadata
 * verification.
 *
 * The implementation ensures that UDF operations are performed only on supported entities and that users have
 * appropriate access rights to the referenced entities.
 */
public class UDFDataJpaRepositoryImpl implements UDFDataJpaRepositoryCustom {

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private UDFDataJpaRepository uDFDataJpaRepository;

  @Autowired
  private UDFDataSharedJpaRepository sharedData;

  @Autowired
  private UDFMetadataSubscriptionJpaRepository subscriptions;

  @Autowired
  private List<IUDFRepository<? extends UDFMetadata>> udfRepositories = new ArrayList<>();

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public UDFData createUpdate(UDFData udfData) throws Exception {
    final User user = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails());
    UpdateCreate.checkAndSetUserBaseIDWithUser(udfData.getuDFDataKey(), user);

    IUDFRepository<? extends UDFMetadata> udfRepository = udfRepositories.stream()
        .filter(c -> c.getSupportedEntities().contains(udfData.getuDFDataKey().getEntity())).findFirst().get();
    List<? extends UDFMetadata> rawMetadata = udfRepository.getMetadataByUserAndEntityAndIdEntity(user.getIdUser(),
        udfData.getuDFDataKey().getEntity(), udfData.getuDFDataKey().getIdEntity());

    checkEntityAndObject(udfData, user);
    // Lock the field before choosing its storage. A share-mode transition takes an exclusive field lock.
    List<UDFMetadata> udfMetadata = new ArrayList<>();
    for (UDFMetadata field : rawMetadata) {
      UDFMetadata current = entityManager.find(UDFMetadata.class, field.getId(), LockModeType.PESSIMISTIC_READ);
      boolean participant = current != null && (user.getIdUser().equals(current.getIdUser())
          || subscriptions.findByIdUDFMetadataAndIdUser(current.getId(), user.getIdUser()).isPresent());
      if (participant && current.isValueWritableBy(user.getIdUser())) {
        udfMetadata.add(current);
      }
    }
    udfData.setJsonValues(new HashMap<>(udfData.getJsonValues()));
    udfData.checkDataAgainstMetadata(udfMetadata);
    for (UDFMetadata field : udfMetadata) {
      String key = BaseConstants.UDF_FIELD_PREFIX + field.getId();
      if (field.getShareMode() != UDFShareMode.UDF_SHARE_NONE && udfData.getJsonValues().containsKey(key)) {
        String value = UDFSharedValueHelper.toCanonical(udfData.getJsonValues().remove(key));
        UDFDataSharedKey sharedKey = new UDFDataSharedKey(field.getId(), udfData.getuDFDataKey().getIdEntity());
        if (value == null) {
          sharedData.deleteById(sharedKey);
        } else {
          sharedData.putValue(field.getId(), sharedKey.getIdEntity(), udfData.getuDFDataKey().getEntity(), value);
        }
      }
    }
    uDFDataJpaRepository.saveAndFlush(udfData);
    return getUDFDataByIdUserAndEntityAndIdEntity(user.getIdUser(), udfData.getuDFDataKey().getEntity(),
        udfData.getuDFDataKey().getIdEntity()).orElseThrow();
  }

  /**
   * Validates that the specified entity supports user-defined fields and verifies tenant access rights. This method
   * performs two critical security checks:<br>
   * 1. Ensures the entity class is registered as supporting UDF functionality<br>
   * 2. Verifies the current user's tenant has access to the specific entity instance<br>
   *
   * The method prevents unauthorized access to entities belonging to other tenants and ensures UDF operations are only
   * performed on entities that have been configured to support them.
   *
   * @param udfData the UDF data containing the entity reference to validate
   * @param user    the current authenticated user whose tenant access will be verified
   * @throws SecurityException if the entity class is not supported for UDF operations, or if the user's tenant does not
   *                           have access to the specified entity instance
   */
  private void checkEntityAndObject(UDFData udfData, User user) {
    Optional<Class<?>> foundClass = UDFData.UDF_GENERAl_AND_SPECIAL_ENTITIES.stream()
        .filter(c -> c.getSimpleName().equals(udfData.getuDFDataKey().getEntity())).findFirst();
    if (foundClass.isPresent()) {
      Object uDFSupport = entityManager.getReference(foundClass.get(), udfData.getuDFDataKey().getIdEntity());
      if (!((IUDFSupport) uDFSupport).tenantHasAccess(user.getIdTenant())) {
        throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
      }
    } else {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
  }

  @Override
  public Optional<UDFData> getUDFDataByIdUserAndEntityAndIdEntity(Integer idUser, String entity, Integer idEntity) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (!user.getIdUser().equals(idUser)) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    UDFDataKey key = new UDFDataKey(idUser, entity, idEntity);
    checkEntityAndObject(new UDFData(key, null), user);
    Optional<UDFData> own = uDFDataJpaRepository.findById(key);
    var values = new HashMap<String, Object>();
    own.ifPresent(d -> values.putAll(d.getJsonValues()));
    // A stale private key must never shadow or reveal a shared field.
    udfRepositories.stream().filter(r -> r.getSupportedEntities().contains(entity)).findFirst()
        .ifPresent(r -> r.getMetadataByUserAndEntityAndIdEntity(idUser, entity, idEntity).stream()
            .filter(f -> f.getShareMode() != UDFShareMode.UDF_SHARE_NONE)
            .forEach(f -> values.remove(BaseConstants.UDF_FIELD_PREFIX + f.getId())));
    sharedData.findValuesOfUserForEntity(idUser, entity, idEntity).forEach(row -> values.put(
        BaseConstants.UDF_FIELD_PREFIX + ((Number) row[0]).intValue(),
        UDFSharedValueHelper.toJsonValue((String) row[1], UDFDataType.getUDFDataType(((Number) row[2]).byteValue()))));
    return own.isPresent() || !values.isEmpty() ? Optional.of(new UDFData(key, values)) : Optional.empty();
  }

  public Class<?>[] getGenericType(Class<?> classInstance, Class<?> classToGetGenerics) {
    return GenericTypeResolver.resolveTypeArguments(classInstance, classToGetGenerics);
  }

}
