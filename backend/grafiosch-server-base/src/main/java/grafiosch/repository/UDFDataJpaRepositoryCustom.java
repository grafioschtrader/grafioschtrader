package grafiosch.repository;

import java.util.Optional;

import grafiosch.entities.UDFData;

/**
 * Custom repository interface for UDFData entity operations that extend the standard JPA repository functionality.
 * Provides specialized methods for creating, updating, and retrieving user-defined field data.
 */
public interface UDFDataJpaRepositoryCustom {

  /**
   * Creates a new UDFData entity or updates an existing one with validation and security checks. This method performs
   * metadata validation against the corresponding UDFMetadata and ensures the current user has proper access rights to
   * the referenced entity.
   * 
   * @param udfData the UDFData entity to create or update, must contain valid user, entity, and idEntity references
   * @return the saved UDFData entity with any generated values populated
   * @throws Exception if validation fails, security checks fail, or database operations encounter errors
   */
  UDFData createUpdate(UDFData udfData) throws Exception;

  /**
   * Retrieves UDFData for a specific user, entity type, and entity ID combination.
   * This method looks up user-defined field data using the composite key consisting of
   * user ID, entity class name, and the specific entity instance ID.
   * 
   * @param idUser the ID of the user who owns the UDF data
   * @param entity the name of the entity class that the UDF data extends
   * @param idEntity the ID of the specific entity instance
   * @return an Optional containing the UDFData if found, or empty Optional if no data exists for the given key
   */
  Optional<UDFData> getUDFDataByIdUserAndEntityAndIdEntity(Integer idUser, String entity, Integer idEntity);
}
