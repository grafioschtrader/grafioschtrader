package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.entities.UDFMetadata;
import grafiosch.entities.UDFMetadataSubscription;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.limit.LimitKeyRegistry;
import grafiosch.repository.UDFDataJpaRepository.IUDFRepository;
import grafiosch.service.EntityLimitService;
import grafiosch.types.UDFShareMode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

public class UDFMetadataSubscriptionJpaRepositoryImpl extends BaseRepositoryImpl<UDFMetadataSubscription>
    implements UDFMetadataSubscriptionJpaRepositoryCustom {
  @PersistenceContext
  private EntityManager em;
  @Autowired
  @Lazy
  private UDFMetadataSubscriptionJpaRepository subscriptions;
  @Autowired
  private UDFMetadataJpaRepository metadata;
  @Autowired
  @Lazy
  private List<IUDFRepository<? extends UDFMetadata>> repositories;
  @Autowired
  private EntityLimitService limits;

  @Autowired(required = false)
  private List<IUDFSharingListener> sharingListeners = List.of();

  @Override
  @Transactional
  public UDFMetadataSubscription saveOnlyAttributes(UDFMetadataSubscription entity,
      UDFMetadataSubscription existingEntity, Set<Class<? extends Annotation>> levels) throws Exception {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer fieldId = existingEntity == null ? entity.getIdUDFMetadata() : existingEntity.getIdUDFMetadata();
    UDFMetadata field = em.find(UDFMetadata.class, fieldId, LockModeType.PESSIMISTIC_WRITE);
    if (field == null || field.getShareMode() == UDFShareMode.UDF_SHARE_NONE || Objects.equals(field.getIdUser(), 0)
        || Objects.equals(field.getIdUser(), user.getIdUser()) || existingEntity == null && field.getIdUser() == null
        || existingEntity != null && !Objects.equals(existingEntity.getIdUser(), user.getIdUser())) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    if (existingEntity == null && subscriptions.findByIdUDFMetadataAndIdUser(fieldId, user.getIdUser()).isPresent()) {
      throw new DataViolationException("ui.order", "g.udf.subscription.exists", null);
    }
    if (entity.getUiOrder() < 1 || entity.getUiOrder() >= BaseConstants.MAX_USER_UI_ORDER_VALUE) {
      throw new DataViolationException("ui.order", "g.udf.subscription.ui.order.used", null);
    }
    for (IUDFRepository<? extends UDFMetadata> repository : repositories) {
      if (repository.findManagedField(fieldId).isPresent()) {
        validateGroup(repository, user.getIdUser(), field, entity, existingEntity);
        break;
      }
    }
    entity.setIdUser(user.getIdUser());
    UDFMetadataSubscription saved = RepositoryHelper.saveOnlyAttributes(subscriptions, entity, existingEntity, levels);
    if (existingEntity == null) {
      sharingListeners.forEach(listener -> listener.afterSubscribed(field, saved));
    }
    return saved;
  }

  private <S extends UDFMetadata> void validateGroup(IUDFRepository<S> repository, Integer idUser, UDFMetadata field,
      UDFMetadataSubscription entity, UDFMetadataSubscription existing) {
    S managed = repository.findManagedField(field.getId()).orElseThrow();
    var count = repository.countUiOrderAndDescriptionForUser(idUser, managed, entity.getUiOrder());
    if ((existing == null || existing.getUiOrder() != entity.getUiOrder()) && count.getCountUiOrder() > 0) {
      throw new DataViolationException("ui.order", "g.udf.subscription.ui.order.used", null);
    }
    if (existing == null && count.getCountDescription() > 0) {
      throw new DataViolationException("description", "g.udf.subscription.description.used",
          new Object[] { field.getDescription() });
    }
  }

  @Override
  @Transactional
  public int delEntityWithUserId(Integer id, Integer idUser) {
    UDFMetadataSubscription subscription = subscriptions.findById(id)
        .orElseThrow(() -> new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH));
    if (!Objects.equals(subscription.getIdUser(), idUser)) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    Integer fieldId = subscription.getIdUDFMetadata();
    UDFMetadata field = em.find(UDFMetadata.class, fieldId, LockModeType.PESSIMISTIC_WRITE);
    int deleted = subscriptions.deleteByIdUDFMetadataSubscriptionAndIdUser(id, idUser);
    em.flush();
    if (metadata.deleteOrphanWithoutSubscription(fieldId) == 0) {
      sharingListeners.forEach(listener -> listener.afterUnsubscribed(field, idUser));
    }
    return deleted;
  }

  @Override
  @Transactional
  public void claimOwnership(Integer id, User user) {
    UDFMetadataSubscription subscription = subscriptions.findById(id)
        .orElseThrow(() -> new DataViolationException("share.mode", "g.udf.claim.taken", null));
    if (!Objects.equals(subscription.getIdUser(), user.getIdUser())) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    UDFMetadata field = em.find(UDFMetadata.class, subscription.getIdUDFMetadata(), LockModeType.PESSIMISTIC_WRITE);
    em.refresh(field);
    if (field.getIdUser() != null) {
      throw new DataViolationException("share.mode", "g.udf.claim.taken", null);
    }
    LimitKeyRegistry.findFlatMaxForEntity(field.getClass()).ifPresent(registration -> {
      if (!limits.fitsWithinLimit(user, registration.limitKey(), null, 1)) {
        throw new SecurityException(BaseConstants.LIMIT_SECURITY_BREACH);
      }
    });
    if (metadata.claim(field.getId(), user.getIdUser(), subscription.getUiOrder()) != 1) {
      throw new DataViolationException("share.mode", "g.udf.claim.taken", null);
    }
    subscriptions.deleteByIdUDFMetadataSubscriptionAndIdUser(id, user.getIdUser());
    UDFMetadata claimed = metadata.findById(field.getId()).orElseThrow();
    sharingListeners.forEach(listener -> listener.afterOwnerClaimed(claimed));
  }
}
