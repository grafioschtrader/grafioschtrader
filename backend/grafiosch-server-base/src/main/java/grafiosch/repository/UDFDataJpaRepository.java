package grafiosch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.UDFData;
import grafiosch.entities.UDFData.UDFDataKey;
import grafiosch.entities.UDFMetadata;

/**
 * JPA repository interface for UDFData entities providing standard CRUD operations and custom UDF functionality. This
 * interface combines Spring Data JPA's standard repository operations with custom UDF-specific operations for managing
 * user-defined field data. It serves as the primary data access layer for user-defined field values and their
 * associated metadata.
 * 
 * The repository uses a composite key (UDFDataKey) consisting of user ID, entity name, and entity ID to uniquely
 * identify UDF data entries, enabling proper multi-tenant data isolation and entity association.
 */
public interface UDFDataJpaRepository extends JpaRepository<UDFData, UDFDataKey>, UDFDataJpaRepositoryCustom {

  /**
   * Generic interface contract for UDF metadata repositories that support user-defined field operations. This interface
   * defines the standard operations that any UDF metadata repository must implement to provide metadata information for
   * user-defined fields, regardless of the specific metadata type (general, security-specific, etc.).
   * 
   * Implementations of this interface serve as bridges between the UDF data layer and the specific metadata
   * repositories, enabling polymorphic access to different types of UDF metadata while maintaining a consistent API for
   * UDF operations.
   * 
   * @param <S> the specific UDFMetadata entity type that extends the base UDFMetadata class
   */
  public static interface IUDFRepository<S extends UDFMetadata> {
    /**
     * Retrieves UDF metadata entries for a specific user, entity type, and entity instance. This method returns the
     * metadata definitions that apply to the specified combination of user, entity class, and entity instance, which
     * are used for validation and form generation of user-defined fields.
     * 
     * The returned metadata includes field definitions, validation rules, data types, and other properties necessary
     * for proper UDF data handling and user interface generation.
     * 
     * @param idUser   the ID of the user for whom to retrieve UDF metadata
     * @param entity   the name of the entity class for which UDF metadata is requested
     * @param idEntity the ID of the specific entity instance that may influence metadata selection
     * @return a list of UDF metadata entries applicable to the specified user, entity type, and entity instance
     */
    List<S> getMetadataByUserAndEntityAndIdEntity(Integer idUser, String entity, Integer idEntity);

    /**
     * Returns the list of entity class names that are supported by this UDF metadata repository. This method provides
     * information about which entity types can have user-defined fields managed by this particular metadata repository
     * implementation.
     * 
     * The returned entity names correspond to the simple class names of entities that have been configured to support
     * user-defined fields through this metadata repository. This information is used for validation and routing UDF
     * operations to the appropriate metadata repository.
     * 
     * @return a list of entity class names (simple names) that support UDF operations through this metadata repository
     */
    List<String> getSupportedEntities();
  }

}
