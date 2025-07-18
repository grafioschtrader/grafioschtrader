import {FormConfig} from '../../dynamic-form/models/form.config';
import {FieldConfig} from '../../dynamic-form/models/field.config';

/**
 * Abstract base class for form components providing common form structure and configuration.
 * Serves as foundation for all form-based components with standardized field configuration management.
 */
export abstract class FormBase {
  /**
   * Array of field configurations defining form field structure and behavior.
   * Each FieldConfig contains metadata about field type, validation, and display properties.
   */
  config: FieldConfig[] = [];

  /**
   * Object mapping field names to their configurations for quick O(1) access.
   * Used for dynamic field manipulation and validation by key name.
   */
  configObject: { [namekey: string]: FieldConfig };
  /**
   * Global form configuration containing form-wide settings and behaviors.
   * Includes validation rules, styling options, and form-level configurations.
   */
  formConfig: FormConfig;
}
