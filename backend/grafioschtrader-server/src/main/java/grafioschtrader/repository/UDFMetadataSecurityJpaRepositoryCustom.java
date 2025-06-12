package grafioschtrader.repository;

import java.util.List;

import grafiosch.repository.BaseRepositoryCustom;
import grafiosch.repository.UDFDataJpaRepository.IUDFRepository;
import grafioschtrader.dto.FieldDescriptorInputAndShowExtendedSecurity;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.reports.udfalluserfields.IUDFForEveryUser;

/**
 * Custom repository interface for UDFMetadataSecurity entities providing specialized operations for security-related
 * user-defined fields. This interface extends the base repository functionality with security-specific UDF operations
 * including field descriptor retrieval for dynamic form generation and bulk UDF field management for all users.
 * 
 * The interface combines standard CRUD operations from BaseRepositoryCustom with UDF-specific operations from
 * IUDFRepository, while adding custom methods for managing security UDF metadata that includes investment instruments,
 * category types, and user-specific field enablement.
 */
public interface UDFMetadataSecurityJpaRepositoryCustom
    extends BaseRepositoryCustom<UDFMetadataSecurity>, IUDFRepository<UDFMetadataSecurity> {

  /**
   * Retrieves field descriptors for security-related user-defined fields accessible to a specific user, excluding
   * disabled fields. This method returns a comprehensive list of field descriptors that includes both user-specific UDF
   * metadata and global UDF metadata (system-wide fields with user ID 0), while filtering out any fields that have been
   * explicitly disabled by the user.
   * 
   * The returned field descriptors contain all necessary information for dynamic form generation of security-related
   * forms, including field names, data types, validation constraints, security-specific properties (category types,
   * investment instruments), descriptions, and UI positioning. Only enabled fields with UI order values indicating
   * user-defined fields are included.
   * 
   * @param idUser the ID of the user for whom to retrieve accessible and enabled field descriptors
   * @return a list of security field descriptors containing metadata, validation rules, security-specific properties,
   *         and UI information for all enabled user-defined security fields accessible to the specified user, ordered
   *         by UI order for consistent form layout
   */
  List<FieldDescriptorInputAndShowExtendedSecurity> getFieldDescriptorByIdUserAndEveryUserExcludeDisabled(
      Integer idUser);

  /**
   * Recreates and synchronizes UDF security metadata fields across all users based on the provided field definitions.
   * This method performs a bulk operation to ensure that all users have consistent access to the same set of
   * user-defined security fields. It typically involves removing existing UDF metadata entries and recreating them
   * based on the new field definitions provided.
   * 
   * This operation is commonly used during system maintenance, field schema updates, or when applying new UDF field
   * configurations globally across all users. The method ensures data consistency by applying the same UDF field
   * structure to all users in the system.
   * 
   * @param uDFForEveryUser a list of UDF field definitions that will be applied to all users, containing the metadata
   *                        structure and properties for each security UDF field that should be available system-wide
   */
  void recreateUDFFieldsForEveryUser(List<IUDFForEveryUser> uDFForEveryUser);
}
