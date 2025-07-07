package grafiosch.repository;

import grafiosch.BaseConstants;
import grafiosch.entities.UDFMetadata;

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
