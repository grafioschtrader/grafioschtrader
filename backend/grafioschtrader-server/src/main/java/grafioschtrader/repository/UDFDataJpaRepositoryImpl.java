package grafioschtrader.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.GenericTypeResolver;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFData;
import grafioschtrader.entities.UDFData.UDFDataKey;
import grafioschtrader.entities.UDFMetadata;
import grafioschtrader.entities.User;
import grafioschtrader.entities.projection.IUDFSupport;
import grafioschtrader.repository.UDFDataJpaRepository.IUDFRepository;
import grafioschtrader.rest.UpdateCreate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class UDFDataJpaRepositoryImpl implements UDFDataJpaRepositoryCustom {

  private static List<Class<?>> supportUDFList = Stream.of(Security.class).collect(Collectors.toList());
  static {
    supportUDFList.addAll(GlobalConstants.UDF_GENERAL_ENTITIES);
  }

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private UDFDataJpaRepository uDFDataJpaRepository;

  @Autowired
  private List<IUDFRepository<? extends UDFMetadata>> udfRepositories = new ArrayList<>();

  public UDFData createUpdate(UDFData udfData) throws Exception {
    final User user = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails());
    UpdateCreate.checkAndSetUserBaseIDWithUser(udfData.getuDFDataKey(), user);

    
    @SuppressWarnings("unchecked")
    IUDFRepository<UDFMetadata> udfRepository = (IUDFRepository<UDFMetadata>) udfRepositories.stream()
        .filter(c -> c.getSupportedEntities().contains(udfData.getuDFDataKey().getEntity())).findFirst().get();
    List<UDFMetadata> udfMetadata = udfRepository
        .getMetadataByUserAndEntityAndIdEntity(user.getIdUser(), udfData.getuDFDataKey().getEntity(),
            udfData.getuDFDataKey().getIdEntity());

    checkEntityAndObject(udfData, user);
    udfData.checkDataAgainstMetadata(udfMetadata);
    
    
    return uDFDataJpaRepository.save(udfData);
  }
  

  /**
   * Checks whether this information class supports user-defined fields. It also
   * checks whether additional fields do not refer to a private object.
   * 
   * @param udfData
   * @param user
   */
  private void checkEntityAndObject(UDFData udfData, User user) {
    Optional<Class<?>> foundClass = supportUDFList.stream()
        .filter(c -> c.getSimpleName().equals(udfData.getuDFDataKey().getEntity())).findFirst();
    if (foundClass.isPresent()) {
      Object uDFSupport = entityManager.getReference(foundClass.get(), udfData.getuDFDataKey().getIdEntity());
      if (!((IUDFSupport) uDFSupport).tenantHasAccess(user.getIdTenant())) {
        throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
      }
    } else {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
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
