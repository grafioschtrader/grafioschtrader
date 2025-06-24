package grafiosch.dto;

/**
 * Interface for objects that can validate their own configuration properties.
 * 
 * <p>
 * This interface provides a contract for configuration objects that need to perform self-validation to ensure their
 * properties are complete, consistent, and properly configured. Implementations should check all required fields,
 * validate data integrity, and verify business rules.
 * </p>
 */
public interface IPropertiesSelfCheck {
  /**
   * Validates the object's properties for completeness and correctness.
   * 
   * <p>
   * Implementations should perform comprehensive validation including required field checks, data format validation,
   * business rule verification, and any other constraints specific to the object type.
   * </p>
   * 
   * @return null if all properties are valid, error message key if validation fails
   */
  String checkForValid();
}
