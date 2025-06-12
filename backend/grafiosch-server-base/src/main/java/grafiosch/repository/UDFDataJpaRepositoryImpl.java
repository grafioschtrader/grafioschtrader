package grafiosch.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.GenericTypeResolver;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.BaseConstants;
import grafiosch.entities.UDFData;
import grafiosch.entities.UDFData.UDFDataKey;
import grafiosch.entities.UDFMetadata;
import grafiosch.entities.User;
import grafiosch.entities.projection.IUDFSupport;
import grafiosch.repository.UDFDataJpaRepository.IUDFRepository;
import grafiosch.rest.UpdateCreate;
import jakarta.persistence.EntityManager;
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
  private List<IUDFRepository<? extends UDFMetadata>> udfRepositories = new ArrayList<>();

  @Override
  public UDFData createUpdate(UDFData udfData) throws Exception {
    final User user = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails());
    UpdateCreate.checkAndSetUserBaseIDWithUser(udfData.getuDFDataKey(), user);

    IUDFRepository<? extends UDFMetadata> udfRepository = udfRepositories.stream()
        .filter(c -> c.getSupportedEntities().contains(udfData.getuDFDataKey().getEntity())).findFirst().get();
    List<? extends UDFMetadata> rawMetadata  = udfRepository.getMetadataByUserAndEntityAndIdEntity(user.getIdUser(),
        udfData.getuDFDataKey().getEntity(), udfData.getuDFDataKey().getIdEntity());

    List<UDFMetadata> udfMetadata = new ArrayList<>(rawMetadata);
    checkEntityAndObject(udfData, user);
    udfData.checkDataAgainstMetadata(udfMetadata);
    return uDFDataJpaRepository.save(udfData);
  }

  /**
   * Validates that the specified entity supports user-defined fields and verifies tenant access rights. This method
   * performs two critical security checks:</br>
   * 1. Ensures the entity class is registered as supporting UDF functionality</br>
   * 2. Verifies the current user's tenant has access to the specific entity instance</br>
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
    return uDFDataJpaRepository.findById(new UDFDataKey(idUser, entity, idEntity));
  }

  public Class<?>[] getGenericType(Class<?> classInstance, Class<?> classToGetGenerics) {
    return GenericTypeResolver.resolveTypeArguments(classInstance, classToGetGenerics);
  }

}
