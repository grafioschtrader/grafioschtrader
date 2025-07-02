package grafiosch.dynamic.model;

/**
 * Enumeration of constraint validator types supported by the dynamic form validation system. This enum defines the
 * different types of validation constraints that can be applied to model classes and their fields during dynamic form
 * generation and processing.
 */
public enum ConstraintValidatorType {
  /**
   * Date range validation constraint that ensures date values fall within a specified start and end date range. This
   * validator is typically applied at the class level and can affect multiple date fields within the entity.
   */
  DateRange
}
