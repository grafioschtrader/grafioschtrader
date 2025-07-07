package grafiosch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.entities.UDFMetadataGeneral;
import grafiosch.repository.UDFMetadataBase.UiOrderDescriptionCount;
import grafiosch.rest.UpdateCreateDeleteWithUserIdJpaRepository;

/**
 * JPA repository interface for UDFMetadataGeneral entities providing comprehensive data access operations for general
 * user-defined field metadata. This repository manages metadata definitions for user-defined fields that can be applied
 * to any entity type that supports general UDF functionality, as opposed to specialized UDF metadata for specific
 * entity types like securities.
 * 
 * The repository combines standard JPA CRUD operations with custom query methods and user-specific operations for
 * managing general UDF metadata. It provides functionality for retrieving metadata by user scope, entity filtering,
 * uniqueness validation, and user-based deletion operations to support multi-tenant user-defined field management.
 * 
 * General UDF metadata applies to entities that don't require specialized metadata structures, making it suitable for
 * extending most business entities with custom fields while maintaining proper data isolation and validation
 * constraints.
 */
public interface UDFMetadataGeneralJpaRepository extends JpaRepository<UDFMetadataGeneral, Integer>,
    UDFMetadataGeneralJpaRepositoryCustom, UpdateCreateDeleteWithUserIdJpaRepository<UDFMetadataGeneral> {

  List<UDFMetadataGeneral> getAllByIdUserInOrderByUiOrder(int[] idUser);

  List<UDFMetadataGeneral> getAllByIdUserInAndEntityOrderByUiOrder(int[] idUser, String entity);

  int deleteByIdUDFMetadataAndIdUser(int idUDFMetadata, int idUser);

  /**
   * Counts existing UDF metadata entries with matching UI order and description values for validation purposes. This
   * method executes a native SQL query to efficiently check uniqueness constraints by counting how many UDF metadata
   * entries already exist with the specified UI order or description within the given user scope and entity type.<br>
   * 
   * The query performs two separate count operations:<br>
   * 1. Counts entries with the same UI order value for the specified users and entity<br>
   * 2. Counts entries with the same description for the specified users and entity<br>
   * 
   * This information is used to enforce uniqueness constraints before creating or updating UDF metadata entries,
   * ensuring that UI order values remain unique for proper field arrangement and descriptions remain unique within the
   * user's scope.
   * 
   * @param users       array of user IDs to check within (typically includes the current user and system user ID 0)
   * @param entityName  the name of the entity class for which to check UDF metadata uniqueness
   * @param uiOrder     the UI order value to check for existing usage
   * @param description the description text to check for existing usage
   * @return a UiOrderDescriptionCount object containing the count of existing entries with matching UI order and
   *         description values
   */
  @Query(nativeQuery = true)
  UiOrderDescriptionCount countUiOrderAndDescription(int[] users, String entityName, int uiOrder, String description);

//  public default List<String> getSupportedEntities() {
//    return UDFDataJpaRepositoryImpl.UDF_GENERAL_ENTITIES.stream().map(c -> c.getSimpleName()).collect(Collectors.toList());
//  }
}
