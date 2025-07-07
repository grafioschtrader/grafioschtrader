package grafioschtrader.reports.udfalluserfields;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;

import grafiosch.BaseConstants;
import grafiosch.common.EnumHelper;
import grafiosch.repository.UDFDataJpaRepository;
import grafiosch.types.IUDFSpecialType;
import grafiosch.udfalluserfields.UDFFieldsHelper;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;

/**
 * Abstract base class for managing security-related user-defined fields across all users in the system. This class
 * provides common functionality for implementing UDF operations that apply to all users for security entities,
 * including field value management, metadata retrieval, and asset class matching.
 * 
 * The class serves as a foundation for concrete implementations that handle specific types of security UDF operations,
 * such as calculating derived values, updating field data, or generating reports based on security characteristics and
 * user-defined field configurations.<br>
 * 
 * Key responsibilities include:<br>
 * - Managing UDF data persistence for security entities at the system level (user ID 0)<br>
 * - Handling JSON-based field value storage and retrieval for watchlist contexts<br>
 * - Providing asset class and investment instrument matching for UDF applicability<br>
 * - Centralizing security UDF metadata access and validation<br>
 * 
 * Subclasses typically implement specific business logic for different types of security UDF operations while
 * leveraging the common infrastructure provided by this base class.
 */
public abstract class AllUserFieldsSecurity {

  @Autowired
  protected UDFDataJpaRepository uDFDataJpaRepository;

  @Autowired
  private UDFMetadataSecurityJpaRepository uDFMetadataSecurityJpaRepository;
  private static final Logger log = LoggerFactory.getLogger(AllUserFieldsSecurity.class);

  /**
   * Updates UDF field values in both watchlist context and persistent storage for a specific security. This method
   * handles the dual storage of UDF values: in-memory for watchlist display and persistent storage for system-wide
   * availability. The method manages JSON serialization and ensures data consistency across both storage mechanisms.
   * 
   * @param securitycurrencyUDFGroup the UDF group containing watchlist and field data context
   * @param udfMetaDataSecurity      the metadata definition for the UDF field being updated
   * @param idSecurity               the ID of the security entity for which the field value is being set
   * @param value                    the field value to be stored (will be serialized appropriately based on field type)
   * @param persits                  true if the value should be persisted to database storage, false for in-memory only
   */
  protected void putValueToJsonValue(SecuritycurrencyUDFGroup securitycurrencyUDFGroup,
      UDFMetadataSecurity udfMetaDataSecurity, int idSecurity, Object value, boolean persits) {
    try {
      if (securitycurrencyUDFGroup.idWatchlist != null) {
        String jsonValuesAsString = securitycurrencyUDFGroup.getUdfEntityValues().get(idSecurity);
        String jsonValuesMapAsString = UDFFieldsHelper.putToTarget(jsonValuesAsString, udfMetaDataSecurity, value);
        securitycurrencyUDFGroup.getUdfEntityValues().put(idSecurity, jsonValuesMapAsString);
      }
      if (persits) {
        UDFFieldsHelper.writeValueToUser0(udfMetaDataSecurity, uDFDataJpaRepository, Security.class, idSecurity, value);
      }
    } catch (JsonProcessingException e) {
      log.error("Failed to read or write JSON with field for all users", e);
    }
  }

  /**
   * Retrieves UDF security metadata for a specific special type from the system-wide definitions. This method accesses
   * metadata definitions that are available to all users (user ID 0) for the specified UDF special type, providing the
   * field configuration and validation rules.
   * 
   * @param udfSpecialType the special type identifier for the UDF field
   * @return the UDF security metadata definition for the specified special type
   */
  protected UDFMetadataSecurity getMetadataSecurity(IUDFSpecialType udfSpecialType) {
    return uDFMetadataSecurityJpaRepository.getByUdfSpecialTypeAndIdUser(udfSpecialType.getValue(),
        BaseConstants.UDF_ID_USER);
  }

  /**
   * Determines if a security's asset class characteristics match the UDF metadata field applicability criteria. This
   * method checks whether the security's category type and special investment instrument fall within the scope defined
   * by the UDF metadata configuration, ensuring that UDF fields are only applied to appropriate security types.
   * 
   * @param udfMetadataSecurity the UDF metadata containing the applicable category types and investment instruments
   * @param assetclass          the asset class of the security to be checked for UDF field applicability
   * @return true if the asset class matches the UDF metadata criteria and the field should be applied, false if the
   *         security type is not within the UDF field's scope
   */
  protected boolean matchAssetclassAndSpecialInvestmentInstruments(UDFMetadataSecurity udfMetadataSecurity,
      Assetclass assetclass) {
    return EnumHelper.contains(assetclass.getCategoryType(), udfMetadataSecurity.getCategoryTypes()) && EnumHelper
        .contains(assetclass.getSpecialInvestmentInstrument(), udfMetadataSecurity.getSpecialInvestmentInstruments());
  }

}
