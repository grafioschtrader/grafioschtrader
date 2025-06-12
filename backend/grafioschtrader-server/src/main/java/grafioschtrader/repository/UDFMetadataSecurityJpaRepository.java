package grafioschtrader.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.repository.UDFMetadataBase.UiOrderDescriptionCount;
import grafiosch.rest.UpdateCreateDeleteWithUserIdJpaRepository;
import grafioschtrader.entities.UDFMetadataSecurity;

public interface UDFMetadataSecurityJpaRepository extends JpaRepository<UDFMetadataSecurity, Integer>,
    UDFMetadataSecurityJpaRepositoryCustom, UpdateCreateDeleteWithUserIdJpaRepository<UDFMetadataSecurity> {

  UDFMetadataSecurity getByUdfSpecialTypeAndIdUser(byte udfSpecialType, int idUser);

  Set<UDFMetadataSecurity> getByUdfSpecialTypeInAndIdUser(Set<Byte> udfSpecialTypes, int idUser);

  List<UDFMetadataSecurity> getAllByIdUserInAndUiOrderLessThanOrderByUiOrder(int[] idUser, byte lessThanUiOrderNum);

  int deleteByIdUDFMetadataAndIdUser(int idUDFMetadata, int idUser);

  /**
   * Counts existing UDF security metadata entries with matching UI order and description values for validation. This
   * method executes a native SQL query to efficiently check uniqueness constraints by counting how many UDF security
   * metadata entries already exist with the specified UI order or description within the given user scope.</br>
   * 
   * The query performs two separate count operations:</br>
   * 1. Counts entries with the same UI order value for the specified users</br>
   * 2. Counts entries with the same description for the specified users</br>
   * 
   * This information is used to enforce uniqueness constraints before creating or updating UDF security metadata
   * entries, ensuring that UI order values remain unique for proper field arrangement and descriptions remain unique
   * within the user's scope.
   * 
   * @param users       array of user IDs to check within (typically includes the current user and system user ID 0)
   * @param uiOrder     the UI order value to check for existing usage
   * @param description the description text to check for existing usage
   * @return a UiOrderDescriptionCount object containing the count of existing entries with matching UI order and
   *         description values
   */
  @Query(nativeQuery = true)
  UiOrderDescriptionCount countUiOrderAndDescription(int[] users, int uiOrder, String description);

  /**
   * Retrieves all UDF security metadata entries accessible to a user, excluding disabled fields and ordered by UI
   * sequence. This method executes a native SQL query that combines security-specific UDF metadata with base UDF
   * metadata, applying multiple filtering criteria to return only relevant and enabled fields.</br>
   * 
   * The query applies the following filters:</br>
   * 1. Includes entries for the specified user and system-wide entries (user ID 0)</br>
   * 2. Excludes fields that have been explicitly disabled by the user via UDFSpecialTypeDisableUser</br>
   * 3. Filters to user-defined fields only (UI order < 100, excluding system fields)</br>
   * 4. Orders results by UI order for consistent form field arrangement</br>
   * 
   * The returned metadata includes both security-specific properties (category types, investment instruments) and
   * general UDF properties (description, data type, field size, etc.) needed for dynamic form generation.
   * 
   * @param idUser the ID of the user for whom to retrieve accessible UDF security metadata entries
   * @return a list of UDFMetadataSecurity objects containing complete metadata for enabled user-defined security
   *         fields, ordered by UI order for proper form field sequence
   */
  @Query(nativeQuery = true)
  List<UDFMetadataSecurity> getAllByIdUserInOrderByUiOrderExcludeDisabled(int idUser);
}
