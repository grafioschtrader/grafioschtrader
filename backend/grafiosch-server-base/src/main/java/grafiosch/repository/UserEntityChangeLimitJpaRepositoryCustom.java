package grafiosch.repository;

import java.util.List;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.UserEntityChangeLimit;

public interface UserEntityChangeLimitJpaRepositoryCustom extends BaseRepositoryCustom<UserEntityChangeLimit> {

  /**
   * Returns a list of public entities available to the user, formatted as HTML select options.
   * Excludes entities already used by the user and entities that are either administrative or abstract.
   * <p>
   * The method transforms each entity class name into an upper snake case string (e.g., "ImportTransactionPlatform" becomes "IMPORT_TRANSACTION_PLATFORM").
   * This format is suitable for use as a translation (NLS) key in resource bundles.
   * </p>
   *
   * @param idUser the unique identifier of the user
   * @param idUserEntityChangeLimit the identifier of the user entity change limit to exclude (can be null)
   * @return a list of options for HTML select elements, with each option containing the original entity name as the key and the formatted translation key as the value
   */
  List<ValueKeyHtmlSelectOptions> getPublicEntitiesAsHtmlSelectOptions(Integer idUser, Integer idUserEntityChangeLimit);
}
