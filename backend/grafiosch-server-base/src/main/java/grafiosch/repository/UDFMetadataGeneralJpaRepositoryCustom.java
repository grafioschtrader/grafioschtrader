package grafiosch.repository;

import java.util.List;

import grafiosch.dynamic.model.FieldDescriptorInputAndShowExtendedGeneral;
import grafiosch.entities.UDFMetadataGeneral;
import grafiosch.repository.UDFDataJpaRepository.IUDFRepository;

/**
 * Custom repository interface for UDFMetadataGeneral entities providing specialized query operations. This interface
 * extends the base repository functionality with additional methods specific to general user-defined field metadata
 * management and form descriptor generation.
 * 
 * The interface combines standard CRUD operations from BaseRepositoryCustom with UDF-specific operations from
 * IUDFRepository, while adding custom methods for retrieving field descriptors that can be used for dynamic form
 * generation in the user interface.
 */
public interface UDFMetadataGeneralJpaRepositoryCustom
    extends BaseRepositoryCustom<UDFMetadataGeneral>, IUDFRepository<UDFMetadataGeneral> {

  /**
   * Retrieves field descriptors for user-defined fields accessible to a specific user and entity type. This method
   * returns a comprehensive list of field descriptors that includes both user-specific UDF metadata and global UDF
   * metadata (system-wide fields with user ID 0), ordered by UI order for proper form field arrangement.
   * 
   * The returned field descriptors contain all necessary information for dynamic form generation, including field
   * names, data types, validation constraints, descriptions, and UI positioning. This enables the frontend to
   * dynamically create input forms for user-defined fields without hardcoded field definitions.
   * 
   * @param idUser the ID of the user for whom to retrieve accessible field descriptors
   * @param entity the name of the entity class for which to retrieve UDF field descriptors
   * @return a list of field descriptors containing metadata, validation rules, and UI information for all user-defined
   *         fields accessible to the specified user for the given entity type, ordered by UI order for consistent form
   *         layout
   */
  List<FieldDescriptorInputAndShowExtendedGeneral> getFieldDescriptorByIdUserAndEveryUserForEntity(Integer idUser,
      String entity);
}
