package grafiosch.repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import grafiosch.BaseConstants;
import grafiosch.entities.UDFMetadata;
import grafiosch.entities.UDFMetadataSubscription;
import grafiosch.exceptions.DataViolationException;
import grafiosch.types.UDFDataType;
import grafiosch.types.UDFShareMode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

/**
 * Base repository class for UDF (User-Defined Field) metadata entities. This abstract class provides common validation
 * functionality for all UDF metadata repositories, ensuring data integrity constraints such as unique descriptions and
 * UI ordering.<br>
 *
 * The class handles validation of user-defined field metadata properties including:<br>
 * - Uniqueness of field descriptions within the user's scope<br>
 * - Uniqueness of UI order values for proper field arrangement<br>
 * - Enforcement of maximum UI order value limits<br>
 *
 * @param <T> the specific UDFMetadata entity type that extends the base UDFMetadata class
 */
public class UDFMetadataBase<T extends UDFMetadata> extends BaseRepositoryImpl<T> {

  @PersistenceContext
  protected EntityManager entityManager;
  @Autowired
  @Lazy
  protected UDFMetadataSubscriptionJpaRepository subscriptions;
  @Autowired
  private UDFMetadataJpaRepository metadata;
  @Autowired
  private UDFDataSharedJpaRepository sharedData;
  @Autowired
  private UDFDataJpaRepository data;

  protected void applyShareModeChange(UDFMetadata entity, UDFMetadata existingEntity, String entityName) {
    if ((Objects.equals(entity.getIdUser(), 0) || entity.getUdfSpecialType() != null)
        && entity.getShareMode() != UDFShareMode.UDF_SHARE_NONE) {
      throw new DataViolationException("share.mode", "g.udf.share.not.allowed", null);
    }
    if (existingEntity == null) {
      return;
    }
    UDFMetadata current = entityManager.find(UDFMetadata.class, existingEntity.getId(), LockModeType.PESSIMISTIC_WRITE);
    entityManager.refresh(current);
    if (!Objects.equals(current.getIdUser(), entity.getIdUser()) || current.getIdUser() == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    int count = subscriptions.countByIdUDFMetadata(current.getId());
    if (entity.getShareMode() == UDFShareMode.UDF_SHARE_NONE && count > 0) {
      throw new DataViolationException("share.mode", "g.udf.share.has.subscribers", new Object[] { count });
    }
    if (current.getShareMode() == entity.getShareMode()) {
      return;
    }
    if (current.getShareMode() == UDFShareMode.UDF_SHARE_NONE) {
      sharedData.copyOwnerValuesToShared(current.getId(), current.getIdUser(), entityName);
      data.removeFieldKey(current.getId(), current.getIdUser(), entityName);
    } else if (entity.getShareMode() == UDFShareMode.UDF_SHARE_NONE) {
      if (current.getUdfDataType() == UDFDataType.UDF_Boolean) {
        data.mergeSharedBooleanValues(current.getId(), current.getIdUser(), entityName);
      } else {
        data.mergeSharedTextValues(current.getId(), current.getIdUser(), entityName);
      }
      sharedData.deleteById_IdUDFMetadata(current.getId());
    }
  }

  protected int deleteOrOrphan(Integer id, Integer idUser, IntSupplier physicalDelete) {
    UDFMetadata field = entityManager.find(UDFMetadata.class, id, LockModeType.PESSIMISTIC_WRITE);
    if (field == null || !Objects.equals(field.getIdUser(), idUser)) {
      return 0;
    }
    return subscriptions.countByIdUDFMetadata(id) > 0 ? metadata.orphan(id, idUser) : physicalDelete.getAsInt();
  }

  /** Detach before replacing the owner's ordering with the subscriber's personal ordering. */
  protected List<T> withSubscriptions(List<T> own, Integer idUser, Class<T> type) {
    List<T> fields = new ArrayList<>(own);
    fields.addAll(entityManager.createQuery("SELECT m FROM " + type.getSimpleName()
        + " m WHERE EXISTS (SELECT s FROM UDFMetadataSubscription s WHERE s.idUDFMetadata = m.idUDFMetadata AND s.idUser = ?1)",
        type).setParameter(1, idUser).getResultList());
    Map<Integer, UDFMetadataSubscription> byField = new HashMap<>();
    subscriptions.findByIdUser(idUser).forEach(s -> byField.put(s.getIdUDFMetadata(), s));
    Map<Integer, Integer> counts = new HashMap<>();
    subscriptions.countSubscribersOfOwner(idUser)
        .forEach(r -> counts.put(((Number) r[0]).intValue(), ((Number) r[1]).intValue()));
    for (T field : fields) {
      entityManager.detach(field);
      UDFMetadataSubscription subscription = byField.get(field.getId());
      field.setIdUDFMetadataSubscription(subscription == null ? null : subscription.getId());
      if (subscription != null) {
        field.setUiOrder(subscription.getUiOrder());
      }
      field
          .setSubscriberCount(Objects.equals(field.getIdUser(), idUser) ? counts.getOrDefault(field.getId(), 0) : null);
      field.setValueEditable(!Objects.equals(field.getIdUser(), 0) && field.isValueWritableBy(idUser));
    }
    fields.sort(Comparator.comparingInt(UDFMetadata::getUiOrder));
    return fields;
  }

  protected List<T> sharedCatalog(Integer idUser, Class<T> type) {
    List<T> fields = entityManager.createQuery(
        "SELECT m FROM " + type.getSimpleName()
            + " m WHERE m.shareMode <> 0 AND m.idUser <> 0 AND m.idUser <> ?1 AND NOT EXISTS"
            + " (SELECT s FROM UDFMetadataSubscription s WHERE s.idUDFMetadata = m.idUDFMetadata AND s.idUser = ?1)",
        type).setParameter(1, idUser).getResultList();
    fields.forEach(field -> {
      entityManager.detach(field);
      field.setValueEditable(false);
    });
    return fields;
  }

  /**
   * Validates uniqueness constraints for UDF metadata description and UI order properties. This method ensures that
   * user-defined field metadata maintains proper uniqueness constraints to prevent conflicts in the user interface and
   * data organization.<br>
   *
   * The method performs the following validations:<br>
   * 1. Ensures the description is unique within the user's scope when creating new entities or when modifying existing
   * entities with different descriptions<br>
   * 2. Validates that the UI order value does not exceed the maximum allowed limit<br>
   * 3. Ensures the UI order is unique within the user's scope to maintain proper field ordering<br>
   *
   * @param uodc           the count object containing current usage statistics for description and UI order values
   * @param entity         the UDF metadata entity being validated (new or updated)
   * @param existingEntity the existing entity from the database, or null if creating a new entity
   * @throws IllegalArgumentException if the description is not unique within the user's scope
   * @throws IllegalArgumentException if the UI order value exceeds the maximum allowed limit
   * @throws IllegalArgumentException if the UI order is not unique within the user's scope
   */
  protected void uniqueDescUiOrderCheck(UiOrderDescriptionCount uodc, final UDFMetadata entity,
      final UDFMetadata existingEntity) {
    if (uodc.getCountDescription() > 0 && (existingEntity == null
        || existingEntity != null && !existingEntity.getDescription().equals(entity.getDescription()))) {
      throw new IllegalArgumentException("Own description must be unique!");
    }

    if (entity.getUiOrder() >= BaseConstants.MAX_USER_UI_ORDER_VALUE) {
      throw new IllegalArgumentException(
          "The order GUI must be less than " + BaseConstants.MAX_USER_UI_ORDER_VALUE + " !");
    }

    if (uodc.getCountUiOrder() > 0
        && (existingEntity == null || existingEntity != null && existingEntity.getUiOrder() != entity.getUiOrder())) {
      throw new IllegalArgumentException("The order GUI must be unique!");
    }
  }

  /**
   * Interface for providing count information about existing UDF metadata properties. This interface is typically
   * implemented by database query result objects that return aggregated counts for validation purposes, helping to
   * enforce uniqueness constraints efficiently at the database level.
   */
  public static interface UiOrderDescriptionCount {
    /**
     * Returns the count of existing UDF metadata entries with the same UI order value. Used to validate uniqueness of
     * UI order within the user's scope.
     *
     * @return the number of existing entries with the same UI order value
     */
    int getCountUiOrder();

    /**
     * Returns the count of existing UDF metadata entries with the same description. Used to validate uniqueness of
     * description within the user's scope.
     *
     * @return the number of existing entries with the same description
     */
    int getCountDescription();
  }
}
