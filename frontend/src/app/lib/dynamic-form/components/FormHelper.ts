import {FieldConfig} from '../models/field.config';
import {FieldFormGroup, FormGroupDefinition} from '../models/form.group.definition';
import {InputType} from '../models/input.type';

/**
 * Utility class providing static helper methods for manipulating dynamic form configurations.
 * This class handles the processing of form field configurations, form groups, and their visibility/state management.
 *
 * The class works with two main types:
 * - FieldConfig: Individual form field configurations
 * - FormGroupDefinition: Container objects that group multiple FieldConfig objects
 *
 * @abstract This class cannot be instantiated and only provides static utility methods
 */
export abstract class FormHelper {

  /**
   * Recursively flattens a nested structure of form groups and field configurations into a flat array of FieldConfig objects.
   * This method extracts all individual field configurations from FormGroupDefinition containers and their nested children.
   *
   * @param fieldFormGroups Array containing a mix of FieldConfig and FormGroupDefinition objects
   * @returns Flattened array containing only FieldConfig objects from all levels of nesting
   *
   * @example
   * ```typescript
   * const config = [
   *   fieldConfig1,
   *   {
   *     formGroupName: 'group1',
   *     fieldConfig: [fieldConfig2, fieldConfig3]
   *   }
   * ];
   * const flattened = FormHelper.flattenConfigMap(config); // Returns [fieldConfig1, fieldConfig2, fieldConfig3]
   * ```
   */
  public static flattenConfigMap(fieldFormGroups: FieldFormGroup[]): FieldConfig[] {
    return fieldFormGroups.reduce((explored, toExplore) =>
      explored.concat((FormHelper.isFieldConfig(toExplore)) ? toExplore :
        FormHelper.flattenConfigMap((<FormGroupDefinition>toExplore).fieldConfig)), []);
  }

  /**
   * Extracts and returns only the FormGroupDefinition objects from the provided array.
   * This method filters out individual FieldConfig objects and returns only the group containers.
   *
   * @param fieldFormGroups Array containing a mix of FieldConfig and FormGroupDefinition objects
   * @returns Array containing only FormGroupDefinition objects
   *
   * @example
   * ```typescript
   * const groups = FormHelper.getFormGroupDefinition(mixedArray);
   * groups.forEach(group => console.log(group.formGroupName));
   * ```
   */
  public static getFormGroupDefinition(fieldFormGroups: FieldFormGroup[]): FormGroupDefinition[] {
    return fieldFormGroups.filter(fieldConfig => !FormHelper.isFieldConfig(fieldConfig)) as FormGroupDefinition[];
  }

  /**
   * Extracts and returns only the top-level FieldConfig objects from the provided array.
   * Unlike flattenConfigMap, this method does NOT recursively process nested FormGroupDefinition objects.
   *
   * @param fieldFormGroups Array containing a mix of FieldConfig and FormGroupDefinition objects
   * @returns Array containing only top-level FieldConfig objects
   *
   * @example
   * ```typescript
   * const topLevelFields = FormHelper.getFieldConfigs(mixedArray);
   * // Returns only FieldConfig objects at the root level, ignoring nested ones
   * ```
   */
  public static getFieldConfigs(fieldFormGroups: FieldFormGroup[]): FieldConfig[] {
    return fieldFormGroups.filter(fieldConfig => FormHelper.isFieldConfig(fieldConfig)) as FieldConfig[];
  }

  /**
   * Type guard function that determines whether an object is a FieldConfig or FormGroupDefinition.
   * Uses the presence of the 'inputType' property to distinguish between the two types.
   *
   * @param fieldFormGroup Object to be checked
   * @returns true if the object is a FieldConfig, false if it's a FormGroupDefinition
   */
  public static isFieldConfig(fieldFormGroup: FieldFormGroup): boolean {
    return fieldFormGroup.hasOwnProperty('inputType');
  }

  /**
   * Controls the visibility of all fields within a specific fieldset by setting their invisible property.
   * Only affects fields that have a fieldsetName matching the provided fieldSetName.
   *
   * @param invisible true to hide the fields, false to show them
   * @param fieldSetName Name of the fieldset whose fields should be affected
   * @param fieldConfigs Array of FieldConfig objects to search through
   */
  public static hideVisibleFieldSet(invisible: boolean, fieldSetName: string, fieldConfigs: FieldConfig[]): void {
    fieldConfigs.filter(fieldConfig => fieldConfig.fieldsetName && fieldConfig.fieldsetName === fieldSetName)
      .forEach(fieldConfig => fieldConfig.invisible = invisible);
  }

  /**
   * Controls the visibility of all provided field configurations by setting their invisible property.
   * Affects all fields in the provided array regardless of their fieldset.
   *
   * @param invisible true to hide the fields, false to show them
   * @param fieldConfigs Array of FieldConfig objects to be affected
   */
  public static hideVisibleFieldConfigs(invisible: boolean, fieldConfigs: FieldConfig[]): void {
    fieldConfigs.forEach(fieldConfig => fieldConfig.invisible = invisible);
  }

  /**
   * Enables or disables form controls for non-button fields and makes them visible.
   * Button fields (Button and Pbutton types) are excluded from this operation.
   * When enabling/disabling, all affected fields are made visible (invisible = false).
   *
   * @param disable true to disable the form controls, false to enable them
   * @param fieldConfigs Array of FieldConfig objects to be affected
   */
  public static disableEnableFieldConfigs(disable: boolean, fieldConfigs: FieldConfig[]): void {
    fieldConfigs.forEach(fieldConfig => {
      if (fieldConfig.inputType !== InputType.Button && fieldConfig.inputType !== InputType.Pbutton) {
        fieldConfig.invisible = false;
        if (disable) {
          fieldConfig.formControl.disable();
        } else {
          fieldConfig.formControl.enable();
        }
      }
    });
  }

  /**
   * Conditionally enables or disables form controls for non-button fields that already have values.
   * When disabling, only affects fields that have non-null values in their form controls.
   * Preserves the original visibility state of each field (saves and restores the invisible property).
   *
   * @param disable true to disable form controls with values, false to enable all non-button controls
   * @param fieldConfigs Array of FieldConfig objects to be processed
   */
  public static disableEnableFieldConfigsWhenAlreadySet(disable: boolean, fieldConfigs: FieldConfig[]): void {
    fieldConfigs.forEach(fieldConfig => {
      if (fieldConfig.inputType !== InputType.Button && fieldConfig.inputType !== InputType.Pbutton) {
        const saveVisibleState = fieldConfig.invisible;
        fieldConfig.invisible = false;
        if (disable && fieldConfig.formControl.value != null) {
          fieldConfig.formControl.disable();
        } else {
          fieldConfig.formControl.enable();
        }
        fieldConfig.invisible = saveVisibleState;
      }
    });
  }

}
