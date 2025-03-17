package grafiosch.repository;

import grafiosch.BaseConstants;
import grafiosch.entities.UDFMetadata;

public class UDFMetadataBase<T extends UDFMetadata> extends BaseRepositoryImpl<T> {

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

  public static interface UiOrderDescriptionCount {
    int getCountUiOrder();

    int getCountDescription();
  }
}
