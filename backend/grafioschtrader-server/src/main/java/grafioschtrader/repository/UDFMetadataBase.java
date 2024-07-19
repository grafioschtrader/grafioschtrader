package grafioschtrader.repository;

import grafioschtrader.entities.UDFMetadata;

public class UDFMetadataBase<T extends UDFMetadata> extends BaseRepositoryImpl<T> {

  protected void uniqueDescUiOrderCheck(UiOrderDescriptionCount uodc, final UDFMetadata entity,
      final UDFMetadata existingEntity) {
    if (uodc.getCountDescription() > 0 && (existingEntity == null
        || existingEntity != null && !existingEntity.getDescription().equals(entity.getDescription()))) {
      throw new IllegalArgumentException("Own description must be unique!");
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
