import type { FormConfig } from '../dynamic-form/models/form.config';
import type { FieldConfig } from '../dynamic-form/models/field.config';
import type { FieldFormGroup } from '../dynamic-form/models/form.group.definition';
import { requireInitialized } from '../helper/required-state';

/**
 * Abstract base class for form components providing common form structure and configuration.
 * Serves as foundation for all form-based components with standardized field configuration management.
 */
export abstract class FormBase<F extends FieldFormGroup = FieldConfig> {
  /**
   * Field definitions, optionally grouped, defining form structure, validation and display properties.
   */
  config: F[] = [];

  /**
   * Object mapping field names to their configurations for quick O(1) access.
   * Used for dynamic field manipulation and validation by key name.
   */
  private initializedConfigObject?: Record<string, FieldConfig>;

  /** Optional reads are reserved for readiness checks and preserving fields while rebuilding a form. */
  protected get configObjectIfInitialized(): Record<string, FieldConfig> | undefined {
    return this.initializedConfigObject;
  }

  get configObject(): Record<string, FieldConfig> {
    return requireInitialized(this.initializedConfigObject, 'FormBase.configObject');
  }

  set configObject(value: Record<string, FieldConfig>) {
    this.initializedConfigObject = value;
  }
  /**
   * Global form configuration containing form-wide settings and behaviors.
   * Includes validation rules, styling options, and form-level configurations.
   */
  private initializedFormConfig?: FormConfig;

  get formConfig(): FormConfig {
    return requireInitialized(this.initializedFormConfig, 'FormBase.formConfig');
  }

  set formConfig(value: FormConfig) {
    this.initializedFormConfig = value;
  }
}
