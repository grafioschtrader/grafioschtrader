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

    @SuppressWarnings("unchecked")
    IUDFRepository<UDFMetadata> udfRepository = (IUDFRepository<UDFMetadata>) udfRepositories.stream()
        .filter(c -> c.getSupportedEntities().contains(udfData.getuDFDataKey().getEntity())).findFirst().get();
    List<UDFMetadata> udfMetadata = udfRepository.getMetadataByUserAndEntityAndIdEntity(user.getIdUser(),
        udfData.getuDFDataKey().getEntity(), udfData.getuDFDataKey().getIdEntity());

    checkEntityAndObject(udfData, user);
    udfData.checkDataAgainstMetadata(udfMetadata);
    return uDFDataJpaRepository.save(udfData);
  }

  /**
   * Checks whether this information class supports user-defined fields. It also checks whether additional fields do not
   * refer to a private object.
   *
   * @param udfData
   * @param user
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
